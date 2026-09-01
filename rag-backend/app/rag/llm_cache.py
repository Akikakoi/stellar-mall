"""LLM 三层缓存：L1 Redis 精确 + L2 Chroma 语义 + L3 真实 LLM 调用。

用法：
    from app.rag.llm_cache import get_llm_cache

    cache = get_llm_cache()
    hit = await cache.get(query=..., model=..., ...)
    if hit:
        return hit
    answer = await call_llm(...)
    await cache.put(query=..., answer=..., ...)
"""
from __future__ import annotations

import asyncio
import hashlib
import json
import os
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from typing import Any, Callable, Dict, List, Optional, Tuple

from app.config import settings
from app.core.logger import logger

# 方案C：缓存写入专用后台线程池。写入（embedding + Chroma 落库）较重且与当前请求无关，
# 放后台线程执行，避免阻塞 LLM 回答链路的返回。容量按 CPU 数限定。
_CACHE_WRITE_EXECUTOR: ThreadPoolExecutor = ThreadPoolExecutor(
    max_workers=min(4, max(1, os.cpu_count() or 2)),
    thread_name_prefix="llm-cache-write",
)

# ==============================================
# Redis 对象（模块级，启动时由 lifespan 注入）
# ==============================================
_redis_client: Any = None   # 异步客户端（FastAPI async 路径用）
_redis_sync: Any = None     # 同步客户端（LangGraph 线程池路径用）
_redis_available = False


async def init_redis() -> bool:
    """初始化 Redis 异步 + 同步客户端。成功返回 True，失败返回 False。"""
    global _redis_client, _redis_sync, _redis_available
    if not settings.LLM_CACHE_REDIS_ENABLED:
        logger.info("[LLMCache] L1 Redis 缓存已关闭")
        return False
    try:
        import redis.asyncio as aioredis  # type: ignore
        _redis_client = aioredis.from_url(
            settings.LLM_CACHE_REDIS_URL,
            max_connections=16,
            socket_timeout=2,
            socket_connect_timeout=1,
            retry_on_timeout=False,
            decode_responses=True,
        )
        pong = await _redis_client.ping()
        # 同时创建同步客户端（供线程池中的 LangGraph 节点使用）
        import redis as sync_redis
        _redis_sync = sync_redis.from_url(
            settings.LLM_CACHE_REDIS_URL,
            socket_timeout=2,
            socket_connect_timeout=1,
            decode_responses=True,
        )
        _redis_available = bool(pong)
        if _redis_available:
            logger.info(f"[LLMCache] L1 Redis 已连接: {settings.LLM_CACHE_REDIS_URL}")
        else:
            logger.warning("[LLMCache] L1 Redis PING 失败，降级为 Chroma-only 缓存")
    except Exception as e:
        _redis_available = False
        _redis_sync = None
        logger.warning(f"[LLMCache] L1 Redis 不可用 ({e})，降级为 Chroma-only 缓存")
    return _redis_available


async def close_redis() -> None:
    """关闭 Redis 连接。"""
    global _redis_client, _redis_sync, _redis_available
    if _redis_client:
        try:
            await _redis_client.close()
        except Exception:
            pass
    if _redis_sync:
        try:
            _redis_sync.close()
        except Exception:
            pass
    _redis_client = None
    _redis_sync = None
    _redis_available = False


# ==============================================
# Prompt Hash 常量（启动时计算一次）
# ==============================================
def _sha256(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


# 这些 hash 在 import 时计算一次，变化时重启生效
INTENT_PROMPT_HASH = ""  # 延迟计算，避免循环 import
AGENT_PROMPT_HASH = ""
RAG_PROMPT_HASH = ""
REWRITE_PROMPT_HASH = ""


def _init_prompt_hashes() -> None:
    """延迟计算 Prompt Hash（避免循环 import）。"""
    global INTENT_PROMPT_HASH, AGENT_PROMPT_HASH, RAG_PROMPT_HASH, REWRITE_PROMPT_HASH
    try:
        from app.agent.prompts import INTENT_CLASSIFICATION_PROMPT, AGENT_SYSTEM_PROMPT
        INTENT_PROMPT_HASH = _sha256(INTENT_CLASSIFICATION_PROMPT)
        AGENT_PROMPT_HASH = _sha256(AGENT_SYSTEM_PROMPT)
    except Exception:
        INTENT_PROMPT_HASH = _sha256("intent_fallback")
        AGENT_PROMPT_HASH = _sha256("agent_fallback")
    try:
        from app.rag.chains import SYSTEM_PROMPT, REWRITE_PROMPT
        RAG_PROMPT_HASH = _sha256(SYSTEM_PROMPT)
        REWRITE_PROMPT_HASH = _sha256(REWRITE_PROMPT)
    except Exception:
        RAG_PROMPT_HASH = _sha256("rag_fallback")
        REWRITE_PROMPT_HASH = _sha256("rewrite_fallback")


# ==============================================
# 指标统计
# ==============================================
class _CacheMetrics:
    """线程不安全的简化指标收集器。"""

    def __init__(self):
        self.l1_hits = 0
        self.l2_hits = 0
        self.misses = 0
        self.write_oks = 0
        self.write_fails = 0
        self.l1_latency_ms: List[float] = []  # 最近 100 次
        self.l2_latency_ms: List[float] = []

    def snapshot(self) -> Dict[str, Any]:
        total = self.l1_hits + self.l2_hits + self.misses
        avg_l1 = sum(self.l1_latency_ms[-50:]) / max(len(self.l1_latency_ms[-50:]), 1)
        avg_l2 = sum(self.l2_latency_ms[-50:]) / max(len(self.l2_latency_ms[-50:]), 1)
        return {
            "l1_hits": self.l1_hits,
            "l2_hits": self.l2_hits,
            "misses": self.misses,
            "hit_ratio": round((self.l1_hits + self.l2_hits) / max(total, 1), 4),
            "write_oks": self.write_oks,
            "write_fails": self.write_fails,
            "avg_l1_latency_ms": round(avg_l1, 2),
            "avg_l2_latency_ms": round(avg_l2, 2),
        }


_metrics = _CacheMetrics()


def get_cache_metrics() -> Dict[str, Any]:
    return _metrics.snapshot()


# ==============================================
# 缓存穿透防护：进程内布隆过滤器
# ==============================================
class _BloomFilter:
    """进程内布隆过滤器，用于 L1 精确缓存命中前的快速负判定。

    思路：对从未写入过的 key，Redis/Chroma 查询永远是浪费。对一个忠实实现
    布隆只存在"必然不存在"与"可能存在并需要进一步查询"两种结论，因此用它
    拦截穿透查询（缓存穿透）可省掉大量无效的 L1 Redis 往返。

    注意：
    - 进程内结构，进程重启后为空。重启后首次命中旧进程写入的 L1 条目时会
      因布隆<尚无>而被跳过一次，走 L2 / 真实 LLM，随后回写自愈——可接受。
    - 位数组默认 64K bit ≈ 8KB，7 个哈希函数，对小规模精确缓存足够。
    - 用 threading.Lock 保证 async（事件循环线程）与 sync（LangGraph 线程池）
      两条写路径同时访问时的线程安全。
    """

    def __init__(self, num_bits: int, num_hashes: int):
        self.num_bits = int(num_bits)
        self.num_hashes = int(num_hashes)
        self._bits = bytearray((self.num_bits + 7) // 8)

    @staticmethod
    def _double_hashes(key: str, num_bits: int, num_hashes: int) -> List[int]:
        # blake2b 16 字节派生两个 64 位哈希，用加倍哈希（double hashing）展开为多个下标
        digest = hashlib.blake2b(key.encode("utf-8"), digest_size=16).digest()
        h1 = int.from_bytes(digest[:8], "big")
        h2 = int.from_bytes(digest[8:], "big") | 1  # 置为奇数，避免 k*h2 恒为 0
        return [(h1 + k * h2) % num_bits for k in range(num_hashes)]

    def add(self, key: str) -> None:
        for idx in self._double_hashes(key, self.num_bits, self.num_hashes):
            byte_idx = idx >> 3
            bit_idx = idx & 7
            self._bits[byte_idx] |= 1 << bit_idx

    def contains(self, key: str) -> bool:
        for idx in self._double_hashes(key, self.num_bits, self.num_hashes):
            byte_idx = idx >> 3
            bit_idx = idx & 7
            if not (self._bits[byte_idx] & (1 << bit_idx)):
                return False
        return True


_bloom: Optional[_BloomFilter] = None
_bloom_lock = threading.Lock()


def _ensure_bloom() -> Optional[_BloomFilter]:
    """惰性初始化布隆过滤器；关闭时返回 None。"""
    global _bloom
    if not settings.LLM_CACHE_BLOOM_ENABLED:
        return None
    if _bloom is None:
        with _bloom_lock:
            if _bloom is None:
                _bloom = _BloomFilter(
                    settings.LLM_CACHE_BLOOM_NUM_BITS,
                    settings.LLM_CACHE_BLOOM_NUM_HASHES,
                )
    return _bloom


def _bloom_may_exist(
    query: str, model: str, temperature: float,
    system_prompt_hash: str, context_hash: Optional[str], cache_type: str,
) -> bool:
    """布隆判定：返回 True 表示「可能存在」，需要继续查 L1；False 表示「必然不存在」可直接 miss。"""
    bloom = _ensure_bloom()
    if bloom is None:
        return True  # 未启用 → 不拦任何查询
    key = _redis_key(query, model, temperature, system_prompt_hash, context_hash, cache_type)
    with _bloom_lock:
        return bloom.contains(key)


def _bloom_add(
    query: str, model: str, temperature: float,
    system_prompt_hash: str, context_hash: Optional[str], cache_type: str,
) -> None:
    """成功写入 L1 后回填布隆。"""
    bloom = _ensure_bloom()
    if bloom is None:
        return
    key = _redis_key(query, model, temperature, system_prompt_hash, context_hash, cache_type)
    with _bloom_lock:
        bloom.add(key)


# ==============================================
# 缓存击穿防护：Single-Flight（同一查询并发去重）
# ==============================================
async def _single_flight_async(key: str, producer: Callable[[], Any]):
    """异步 single-flight：并发相同 key 只执行一次 producer，其余等待复用首次结果。

    使用 asyncio.Future 传递结果；首个请求成为 leader 执行 producer，其余请求 await
    同一个 Future 拿到同一份结果（不重复调用 LLM）。producer 抛错时错误广播给所有等待者，
    避免各自重试造成放大。
    """
    if not settings.LLM_CACHE_SINGLE_FLIGHT_ENABLED:
        return await producer()

    loop = asyncio.get_running_loop()
    with _sf_async_lock:
        fut = _sf_async_map.get(key)
        leader = fut is None
        if leader:
            fut = loop.create_future()
            _sf_async_map[key] = fut

    if not leader:
        # 非 leader：等待并复用 leader 的结果 / 错误
        return await asyncio.shield(fut)

    try:
        result = await producer()
        if not fut.done():
            fut.set_result(result)
        return result
    except BaseException as e:
        if not fut.done():
            fut.set_exception(e)
        raise
    finally:
        with _sf_async_lock:
            if _sf_async_map.get(key) is fut:
                _sf_async_map.pop(key, None)


class _SyncFlight:
    __slots__ = ("done", "result", "error")

    def __init__(self):
        self.done = threading.Event()
        self.result: Any = None
        self.error: Optional[BaseException] = None


def _single_flight_sync(key: str, producer: Callable[[], Any]):
    """同步版 single-flight（供 LangGraph 线程池节点使用）。"""
    if not settings.LLM_CACHE_SINGLE_FLIGHT_ENABLED:
        return producer()

    with _sf_sync_lock:
        flight = _sf_sync_map.get(key)
        leader = flight is None
        if leader:
            flight = _SyncFlight()
            _sf_sync_map[key] = flight

    if not leader:
        # 非 leader：阻塞等待 leader 完成并复用结果
        flight.done.wait()
        if flight.error is not None:
            raise flight.error
        return flight.result

    try:
        result = producer()
        flight.result = result
        return result
    except BaseException as e:
        flight.error = e
        raise
    finally:
        flight.done.set()
        with _sf_sync_lock:
            if _sf_sync_map.get(key) is flight:
                _sf_sync_map.pop(key, None)


_sf_async_map: Dict[str, asyncio.Future] = {}
_sf_async_lock = threading.Lock()
_sf_sync_map: Dict[str, _SyncFlight] = {}
_sf_sync_lock = threading.Lock()


def single_flight(key: str, producer: Callable[[], Any]):
    """异步 single-flight：并发相同 key 只执行一次 producer，其余复用它（缓存击穿防护）。"""
    return _single_flight_async(key, producer)


def single_flight_sync(key: str, producer: Callable[[], Any]):
    """同步 single-flight（供 LangGraph 线程池节点使用）。"""
    return _single_flight_sync(key, producer)


# ==============================================
# L1: Redis 精确缓存
# ==============================================
def _redis_key(
    query: str,
    model: str,
    temperature: float,
    system_prompt_hash: str,
    context_hash: Optional[str],
    cache_type: str = "answer",
) -> str:
    """生成 Redis key。"""
    raw = f"{query}\n{model}\n{temperature:.2f}\n{system_prompt_hash}\n{context_hash or ''}"
    return f"llm:exact:{cache_type}:{_sha256(raw)}"


async def _redis_get(
    query: str, model: str, temperature: float,
    system_prompt_hash: str, context_hash: Optional[str],
    cache_type: str = "answer",
) -> Optional[Dict[str, Any]]:
    """从 Redis 精确缓存获取。"""
    if not _redis_available or not _redis_client:
        return None
    # 布隆快速负判定：从未写入过 → 直接 miss，省去 Redis 往返
    if not _bloom_may_exist(query, model, temperature, system_prompt_hash, context_hash, cache_type):
        return None
    t0 = time.monotonic()
    try:
        key = _redis_key(query, model, temperature, system_prompt_hash, context_hash, cache_type)
        raw = await _redis_client.get(key)
        if raw:
            data = json.loads(raw)
            elapsed = (time.monotonic() - t0) * 1000
            _metrics.l1_hits += 1
            _metrics.l1_latency_ms.append(elapsed)
            if len(_metrics.l1_latency_ms) > 100:
                _metrics.l1_latency_ms.pop(0)
            logger.debug(f"[LLMCache] L1 HIT  query={query[:30]}...  latency={elapsed:.1f}ms")
            return data
    except Exception as e:
        logger.warning(f"[LLMCache] L1 GET 异常: {e}")
    return None


async def _redis_put(
    query: str, model: str, temperature: float,
    system_prompt_hash: str, context_hash: Optional[str],
    data: Dict[str, Any], ttl: int = 7200, cache_type: str = "answer",
) -> bool:
    """写入 Redis 精确缓存（异步）。"""
    if not _redis_available or not _redis_client:
        return False
    try:
        key = _redis_key(query, model, temperature, system_prompt_hash, context_hash, cache_type)
        data["_cached_at"] = time.time()
        await _redis_client.setex(key, ttl, json.dumps(data, ensure_ascii=False))
        _metrics.write_oks += 1
        _bloom_add(query, model, temperature, system_prompt_hash, context_hash, cache_type)
        return True
    except Exception as e:
        _metrics.write_fails += 1
        logger.warning(f"[LLMCache] L1 PUT 异常: {e}")
        return False


def _redis_get_sync(
    query: str, model: str, temperature: float,
    system_prompt_hash: str, context_hash: Optional[str],
    cache_type: str = "answer",
) -> Optional[Dict[str, Any]]:
    """从 Redis 精确缓存获取（同步，供线程池中的 LangGraph 节点使用）。"""
    if not _redis_available or not _redis_sync:
        return None
    # 布隆快速负判定：从未写入过 → 直接 miss，省去 Redis 往返
    if not _bloom_may_exist(query, model, temperature, system_prompt_hash, context_hash, cache_type):
        return None
    t0 = time.monotonic()
    try:
        key = _redis_key(query, model, temperature, system_prompt_hash, context_hash, cache_type)
        raw = _redis_sync.get(key)
        if raw:
            data = json.loads(raw)
            elapsed = (time.monotonic() - t0) * 1000
            _metrics.l1_hits += 1
            _metrics.l1_latency_ms.append(elapsed)
            if len(_metrics.l1_latency_ms) > 100:
                _metrics.l1_latency_ms.pop(0)
            logger.debug(f"[LLMCache] L1 HIT(sync)  query={query[:30]}...  latency={elapsed:.1f}ms")
            return data
    except Exception as e:
        logger.warning(f"[LLMCache] L1 GET(sync) 异常: {e}")
    return None


def _redis_put_sync(
    query: str, model: str, temperature: float,
    system_prompt_hash: str, context_hash: Optional[str],
    data: Dict[str, Any], ttl: int = 7200, cache_type: str = "answer",
) -> bool:
    """写入 Redis 精确缓存（同步）。"""
    if not _redis_available or not _redis_sync:
        return False
    try:
        key = _redis_key(query, model, temperature, system_prompt_hash, context_hash, cache_type)
        data["_cached_at"] = time.time()
        _redis_sync.setex(key, ttl, json.dumps(data, ensure_ascii=False))
        _metrics.write_oks += 1
        _bloom_add(query, model, temperature, system_prompt_hash, context_hash, cache_type)
        return True
    except Exception as e:
        _metrics.write_fails += 1
        logger.warning(f"[LLMCache] L1 PUT(sync) 异常: {e}")
        return False


# ==============================================
# L2: Chroma 语义缓存
# ==============================================
_semantic_collection: Any = None
_semantic_available = False

_SEMANTIC_COLLECTION_NAME = "llm_semantic_cache"


def _get_semantic_collection():
    """获取或初始化 Chroma 语义缓存 collection。"""
    global _semantic_collection, _semantic_available
    if _semantic_collection is not None:
        return _semantic_collection if _semantic_available else None
    if not settings.LLM_CACHE_SEMANTIC_ENABLED:
        logger.info("[LLMCache] L2 语义缓存已关闭")
        return None
    try:
        import chromadb
        from app.rag.vector_store import get_vector_store
        vs = get_vector_store()
        client = vs._client
        try:
            _semantic_collection = client.get_collection(_SEMANTIC_COLLECTION_NAME)
            logger.info(f"[LLMCache] L2 Chroma collection '{_SEMANTIC_COLLECTION_NAME}' 已存在, "
                        f"count={_semantic_collection.count()}")
        except Exception:
            # collection 不存在，新建
            _semantic_collection = client.create_collection(
                name=_SEMANTIC_COLLECTION_NAME,
                metadata={"hnsw:space": "cosine"},
            )
            logger.info(f"[LLMCache] L2 Chroma collection '{_SEMANTIC_COLLECTION_NAME}' 新建成功")
        _semantic_available = True
        return _semantic_collection
    except Exception as e:
        _semantic_available = False
        logger.warning(f"[LLMCache] L2 Chroma 语义缓存不可用 ({e})")
        return None


async def _semantic_search(
    query: str,
    model: str,
    temperature: float,
    system_prompt_hash: str,
    cache_type: str = "answer",
    context_hash: Optional[str] = None,
) -> Optional[Dict[str, Any]]:
    """从 Chroma 语义缓存检索。

    流程：query embedding → Chroma 向量检索 (k) → 过滤匹配 model/temp/sys_hash → Reranker 精排 → 阈值判定。
    """
    col = _get_semantic_collection()
    if col is None:
        return None
    t0 = time.monotonic()
    try:
        from app.rag.embeddings import get_embeddings
        emb = get_embeddings()
        q_emb = emb.embed_query(query)

        k = min(settings.LLM_CACHE_SEMANTIC_K, col.count()) if col.count() > 0 else 0
        if k == 0:
            return None
        results = col.query(query_embeddings=[q_emb], n_results=k,
                            include=["documents", "metadatas", "distances", "embeddings"])

        ids_list = results.get("ids", [[]])[0]
        metas_list = results.get("metadatas", [[]])[0]
        embeddings = results.get("embeddings", [[]])[0]
        distances = results.get("distances", [[]])[0]

        if not ids_list:
            return None

        # 过滤：model、temperature、system_prompt_hash 必须匹配
        candidates: List[Tuple[int, Dict[str, Any]]] = []
        for i, meta in enumerate(metas_list):
            if not meta:
                continue
            # 精确匹配模型参数
            if (meta.get("model") != model
                    or abs(float(meta.get("temperature", 0)) - temperature) > 0.01
                    or meta.get("system_prompt_hash") != system_prompt_hash
                    or meta.get("cache_type") != cache_type):
                continue
            # context_hash 过滤：若调用方传了 context_hash，必须精确匹配
            # （防止"X100 Pro"的答案被语义检索返回给"X100 Pro Max"）
            if context_hash and meta.get("context_hash") and meta.get("context_hash") != context_hash:
                continue
            # 检查 TTL
            created_at = float(meta.get("created_at", 0))
            ttl = settings.LLM_CACHE_SEMANTIC_TTL_ANSWER if cache_type == "answer" else settings.LLM_CACHE_SEMANTIC_TTL_INTENT
            if time.time() - created_at > ttl:
                continue
            candidates.append((i, meta))

        if not candidates:
            return None

        # 方案B：直接用 query embedding + Chroma 已存向量做余弦精排，
        # 复用本已算出的 q_emb，不再走 rag.retriever.rerank（会再次触发云端 embedding，单次约 1.3s）。
        # 语义缓存排序只要求"候选间相似度倒序取 top1"，本地余弦即可，无需 CrossEncoder。
        import numpy as np
        q_np = np.asarray(q_emb, dtype=np.float32).reshape(1, -1)
        best_idx = None
        best_score = 0.0
        for pos, (idx, _meta) in enumerate(candidates):
            if idx >= len(embeddings):
                continue
            d_np = np.asarray(embeddings[idx], dtype=np.float32).reshape(1, -1)
            score = float((d_np @ q_np.T).ravel()[0] / (
                np.linalg.norm(d_np) * np.linalg.norm(q_np) + 1e-9))
            if score > best_score:
                best_score = score
                best_idx = pos

        if best_idx is None:
            return None

        best_meta = candidates[best_idx][1]
        threshold = settings.LLM_CACHE_SEMANTIC_RERANK_THRESHOLD
        if best_score < threshold:
            logger.debug(f"[LLMCache] L2 miss: best_score={best_score:.4f} < threshold={threshold}")
            return None

        # 更新命中计数
        try:
            hit_count = int(best_meta.get("hit_count", 0)) + 1
            col.update(
                ids=[ids_list[candidates[best_idx][0]]],
                metadatas=[{**best_meta, "hit_count": hit_count}],
            )
        except Exception:
            pass

        elapsed = (time.monotonic() - t0) * 1000
        _metrics.l2_hits += 1
        _metrics.l2_latency_ms.append(elapsed)
        if len(_metrics.l2_latency_ms) > 100:
            _metrics.l2_latency_ms.pop(0)
        logger.info(f"[LLMCache] L2 HIT  query={query[:30]}...  "
                     f"best_score={best_score:.3f}  latency={elapsed:.1f}ms")

        return {
            "answer": best_meta.get("answer", ""),
            "sources": json.loads(best_meta.get("sources_json", "[]")),
            "intent": best_meta.get("intent", ""),
            "tokens_used": int(best_meta.get("tokens_used", 0)),
        }
    except Exception as e:
        logger.warning(f"[LLMCache] L2 语义检索异常: {e}")
        elapsed = (time.monotonic() - t0) * 1000
        _metrics.misses += 1
    return None


async def _semantic_put(
    query: str,
    model: str,
    temperature: float,
    system_prompt_hash: str,
    context_hash: Optional[str],
    answer: str,
    sources: List[dict],
    intent: str,
    tokens_used: int,
    product_ids: Optional[List[int]] = None,
    cache_type: str = "answer",
) -> bool:
    """写入 Chroma 语义缓存。"""
    col = _get_semantic_collection()
    if col is None:
        return False
    try:
        from app.rag.embeddings import get_embeddings
        emb = get_embeddings()
        q_emb = emb.embed_query(query)

        # 生成唯一 ID
        raw_id = f"{query}{model}{temperature:.2f}{system_prompt_hash}{context_hash or ''}"
        doc_id = f"cache_{_sha256(raw_id)[:16]}_{int(time.time())}"

        ttl = settings.LLM_CACHE_SEMANTIC_TTL_ANSWER if cache_type == "answer" else settings.LLM_CACHE_SEMANTIC_TTL_INTENT
        metadata = {
            "query": query[:500],  # 截断，避免 metadata 过大
            "answer": answer,
            "model": model,
            "temperature": temperature,
            "system_prompt_hash": system_prompt_hash,
            "context_hash": context_hash or "",
            "intent": intent,
            "sources_json": json.dumps(sources, ensure_ascii=False),
            "tokens_used": tokens_used,
            "created_at": time.time(),
            "hit_count": 0,
            "product_ids": ",".join(str(p) for p in product_ids) if product_ids else "",
            "cache_type": cache_type,
        }

        col.add(
            ids=[doc_id],
            embeddings=[q_emb],
            documents=[query[:500]],
            metadatas=[metadata],
        )

        # 容量控制：超过 maxsize 时淘汰 hit_count 最低的 20%
        current_count = col.count()
        maxsize = settings.LLM_CACHE_SEMANTIC_MAXSIZE
        if current_count > maxsize:
            try:
                all_data = col.get(include=["metadatas"])
                all_ids = all_data.get("ids", [])
                all_metas = all_data.get("metadatas", [])
                if len(all_ids) > maxsize:
                    # 按 hit_count 排序，淘汰最低的 20%
                    pairs = list(zip(all_ids, all_metas))
                    pairs.sort(key=lambda x: int((x[1] or {}).get("hit_count", 0)))
                    evict_count = max(1, int(len(pairs) * 0.2))
                    evict_ids = [p[0] for p in pairs[:evict_count]]
                    col.delete(ids=evict_ids)
                    logger.info(f"[LLMCache] L2 容量淘汰: evicted={evict_count}  "
                                f"current={col.count()}  max={maxsize}")
            except Exception:
                pass

        _metrics.write_oks += 1
        logger.debug(f"[LLMCache] L2 PUT  query={query[:30]}...  id={doc_id}")
        return True
    except Exception as e:
        _metrics.write_fails += 1
        logger.warning(f"[LLMCache] L2 PUT 异常: {e}")
        return False


# ==============================================
# 方案C：后台异步缓存写入执行体
# ==============================================
def _do_put_sync(
    query: str,
    model: str,
    temperature: float,
    system_prompt_hash: str,
    context_hash: Optional[str],
    answer: str,
    sources: List[dict],
    intent: str,
    tokens_used: int,
    product_ids: Optional[List[int]] = None,
    cache_type: str = "answer",
) -> None:
    """在后台线程池中真正执行 L1 + L2 写入。与 async put() 等价，只做写、不做读。"""
    try:
        if not settings.LLM_CACHE_ENABLED or not answer or not answer.strip():
            return
        # L1: 同步写 Redis
        ttl = settings.LLM_CACHE_REDIS_TTL_ANSWER if cache_type == "answer" else settings.LLM_CACHE_REDIS_TTL_INTENT
        data = {
            "v": 1,
            "answer": answer,
            "sources": sources,
            "intent": intent,
            "tokens_used": tokens_used,
            "ts": int(time.time()),
        }
        _redis_put_sync(query, model, temperature, system_prompt_hash, context_hash, data, ttl, cache_type)
        # L2: Chroma 语义写入（内部是同步 client，但方法为 async，用 asyncio.run 驱动）
        try:
            asyncio.run(_semantic_put(
                query=query, model=model, temperature=temperature,
                system_prompt_hash=system_prompt_hash, context_hash=context_hash,
                answer=answer, sources=sources, intent=intent,
                tokens_used=tokens_used, product_ids=product_ids,
                cache_type=cache_type,
            ))
        except RuntimeError:
            pass
    except Exception as e:
        logger.warning(f"[LLMCache] 异步写入失败(后台): {e}")


# ==============================================
# 公开 API
# ==============================================
class LLMCache:
    """LLM 三层缓存统一接口"""

    async def get(
        self,
        query: str,
        model: str,
        temperature: float,
        system_prompt_hash: str,
        context_hash: Optional[str] = None,
        cache_type: str = "answer",
    ) -> Optional[Dict[str, Any]]:
        """查询缓存：L1 → L2，命中返回结果，未命中返回 None。"""
        if not settings.LLM_CACHE_ENABLED:
            return None

        # L1: Redis 精确缓存
        result = await _redis_get(query, model, temperature, system_prompt_hash, context_hash, cache_type)
        if result:
            return result

        # L2: Chroma 语义缓存
        result = await _semantic_search(query, model, temperature, system_prompt_hash, cache_type, context_hash)
        if result:
            # 语义命中后，顺便回写 L1（不阻塞）
            asyncio.create_task(_redis_put(
                query, model, temperature, system_prompt_hash, None,
                result, settings.LLM_CACHE_REDIS_TTL_ANSWER, cache_type,
            ))
            return result

        # 全部 miss
        _metrics.misses += 1
        return None

    async def put(
        self,
        query: str,
        model: str,
        temperature: float,
        system_prompt_hash: str,
        context_hash: Optional[str],
        answer: str,
        sources: List[dict],
        intent: str,
        tokens_used: int,
        product_ids: Optional[List[int]] = None,
        cache_type: str = "answer",
    ) -> None:
        """异步写入 L1 + L2（不阻塞主流程）。"""
        if not settings.LLM_CACHE_ENABLED:
            return
        if not answer or not answer.strip():
            return

        ttl = settings.LLM_CACHE_REDIS_TTL_ANSWER if cache_type == "answer" else settings.LLM_CACHE_REDIS_TTL_INTENT
        data = {
            "v": 1,
            "answer": answer,
            "sources": sources,
            "intent": intent,
            "tokens_used": tokens_used,
            "ts": int(time.time()),
        }

        # L1 写入
        await _redis_put(query, model, temperature, system_prompt_hash, context_hash, data, ttl, cache_type)
        # L2 写入
        await _semantic_put(
            query=query, model=model, temperature=temperature,
            system_prompt_hash=system_prompt_hash, context_hash=context_hash,
            answer=answer, sources=sources, intent=intent,
            tokens_used=tokens_used, product_ids=product_ids,
            cache_type=cache_type,
        )

    def get_sync(
        self,
        query: str,
        model: str,
        temperature: float,
        system_prompt_hash: str,
        context_hash: Optional[str] = None,
        cache_type: str = "answer",
    ) -> Optional[Dict[str, Any]]:
        """同步查询缓存（供线程池中的 LangGraph 节点使用）。

        L1 用 sync Redis、L2 用 Chroma（Chroma 是同步的），无需 event loop。
        """
        if not settings.LLM_CACHE_ENABLED:
            return None

        # L1: 同步 Redis
        result = _redis_get_sync(query, model, temperature, system_prompt_hash, context_hash, cache_type)
        if result:
            return result

        # L2: Chroma 语义缓存（需要 embedding + rerank，内部用 asyncio.run()）
        try:
            result = asyncio.run(_semantic_search(query, model, temperature, system_prompt_hash, cache_type, context_hash))
        except RuntimeError:
            # 若有运行中的 loop（不应在 get_sync 场景出现），走兜底
            return None
        if result:
            # 语义命中后回写 L1（同步）
            ttl = settings.LLM_CACHE_REDIS_TTL_ANSWER if cache_type == "answer" else settings.LLM_CACHE_REDIS_TTL_INTENT
            _redis_put_sync(query, model, temperature, system_prompt_hash, None, result, ttl, cache_type)
            return result

        _metrics.misses += 1
        return None

    def put_sync(
        self,
        query: str,
        model: str,
        temperature: float,
        system_prompt_hash: str,
        context_hash: Optional[str],
        answer: str,
        sources: List[dict],
        intent: str,
        tokens_used: int,
        product_ids: Optional[List[int]] = None,
        cache_type: str = "answer",
    ) -> None:
        """异步化写入 L1 + L2：提交到后台线程池，不阻塞主回答链路。

        方案C：本地 embedding + Chroma 落库较重（秒级），放后台线程执行；
        写入对当前请求无感知，成功后对后续请求仍可命中。
        """
        _CACHE_WRITE_EXECUTOR.submit(
            _do_put_sync,
            query, model, temperature, system_prompt_hash, context_hash,
            answer, sources, intent, tokens_used, product_ids, cache_type,
        )

    async def invalidate_by_product_ids(self, product_ids: List[int]) -> int:
        """按商品 ID 精准失效语义缓存。返回清除条数。"""
        col = _get_semantic_collection()
        if col is None or not product_ids:
            return 0
        try:
            all_data = col.get(include=["metadatas"])
            all_ids = all_data.get("ids", [])
            all_metas = all_data.get("metadatas", [])
            ids_to_delete = []
            for i, meta in enumerate(all_metas):
                if not meta:
                    continue
                cached_pids = str(meta.get("product_ids", ""))
                for pid in product_ids:
                    if f",{pid}," in f",{cached_pids},":
                        ids_to_delete.append(all_ids[i])
                        break
            if ids_to_delete:
                col.delete(ids=ids_to_delete)
                logger.info(f"[LLMCache] 精准失效: product_ids={product_ids}  "
                            f"cleared={len(ids_to_delete)}")
            return len(ids_to_delete)
        except Exception as e:
            logger.warning(f"[LLMCache] 精准失效异常: {e}")
            return 0

    async def invalidate_all(self) -> int:
        """清空所有语义缓存。返回清除条数。"""
        col = _get_semantic_collection()
        if col is None:
            return 0
        try:
            count = col.count()
            all_ids = col.get(include=[])["ids"]
            if all_ids:
                col.delete(ids=all_ids)
            logger.info(f"[LLMCache] 全量失效: cleared={count}")
            # Redis 端：自然过期（TTL），不需要手动清理
            return count
        except Exception as e:
            logger.warning(f"[LLMCache] 全量失效异常: {e}")
            return 0


# ==============================================
# 单例
# ==============================================
_llm_cache_instance: Optional[LLMCache] = None


def get_llm_cache() -> LLMCache:
    """获取 LLMCache 单例（同步初始化）。"""
    global _llm_cache_instance
    if _llm_cache_instance is None:
        _llm_cache_instance = LLMCache()
        _init_prompt_hashes()
    return _llm_cache_instance


# 同步便捷方法（供 LangGraph 节点使用，在设计文档中称为 get_cache_sync / put_cache_sync）
def get_cache_sync(
    query: str, model: str, temperature: float,
    system_prompt_hash: str, context_hash: Optional[str] = None,
    cache_type: str = "answer",
) -> Optional[Dict[str, Any]]:
    """同步查询缓存（供 LangGraph 线程池节点使用）。"""
    return get_llm_cache().get_sync(query, model, temperature, system_prompt_hash, context_hash, cache_type)


def put_cache_sync(
    query: str, model: str, temperature: float,
    system_prompt_hash: str, context_hash: Optional[str],
    answer: str, sources: List[dict], intent: str, tokens_used: int,
    product_ids: Optional[List[int]] = None, cache_type: str = "answer",
) -> None:
    """同步写入缓存（供 LangGraph 线程池节点使用）。"""
    try:
        get_llm_cache().put_sync(
            query, model, temperature, system_prompt_hash, context_hash,
            answer, sources, intent, tokens_used, product_ids, cache_type,
        )
    except Exception:
        pass
