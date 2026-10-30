"""检索器：ES 原生 RRF 混合召回（kNN + BM25）+ Rerank 精排 + 缓存。

P2 迁移说明：
  原 Chroma 时代 BM25 在 Python 内存里从全量文档重建（进程级缓存缓解），
  tags 过滤靠先取 top_k 再 Python 后过滤（有召回损失）。
  迁 ES 后，kNN 与 BM25 由 ES 单请求融合（rank.rrf），tags 过滤下推为
  filter context（wildcard 子串语义，见 es_client.es_tags_filter），
  全量重建与后过滤召回损失问题一并消失。
"""
from __future__ import annotations
import threading
from typing import List, Optional, Tuple

from app.config import settings
from app.core.logger import logger
from app.rag.embeddings import get_embeddings
from app.rag.es_client import KB_ES_INDEX, es_tags_filter, get_es

# BGE Reranker 单例（懒加载）
_reranker_instance = None
_reranker_load_attempted = False


# =====================================================
# 全量文档缓存（进程级单例，kb_tool 全库打分工具用）
# =====================================================
# ES 时代 BM25 倒排由 ES 维护，不再需要全量重建缓存；但 kb_tool 的
# kb_spec_compare / kb_specs_for_products 仍是「全库拉取 + Python 打分」
# 模式，这里保留进程级缓存避免每次工具调用都全量 IO。
#   - 按 doc 数量做失效校验，知识库增删后 count 变化即自动重建；
#   - 内容更新但 count 不变的场景（SPU 同步）由 invalidate_docs_cache() 显式置空。
_docs_cache: Optional[List] = None
_docs_cache_count: int = -1
_docs_lock = threading.Lock()


def invalidate_docs_cache() -> None:
    """置空全量文档缓存，下次查询时重建。知识库增删改文档后调用。"""
    global _docs_cache, _docs_cache_count
    with _docs_lock:
        _docs_cache = None
        _docs_cache_count = -1
    logger.info("[DocsCache] 全量文档缓存已失效，下次检索将重建")


def _matches_tag_filter(metadata: dict, tags_filter: List[str]) -> bool:
    """检查 metadata 的 tags 字段是否包含 tags_filter 中任一值（子串语义）。

    ES 检索路径的 tags 过滤已下推（wildcard），此函数仅供 kb_tool 的
    全库 Python 打分路径使用。tags 现为 list（逗号字符串已拆分），
    对任一元素做子串判断，与原逗号字符串 in 判断等价。
    """
    tags = metadata.get("tags", "")
    if isinstance(tags, list):
        tag_str = ",".join(str(t) for t in tags)
    else:
        tag_str = str(tags or "")
    if not tag_str:
        return False
    return any(t in tag_str for t in tags_filter)


def _get_all_docs_cached() -> List:
    """获取知识库全量 Document（进程级缓存）。count 变化时自动重建。"""
    global _docs_cache, _docs_cache_count
    from langchain_core.documents import Document

    from app.rag.vector_store import get_vector_store
    vs = get_vector_store()
    try:
        current_count = vs.count()
    except Exception:
        current_count = -1

    with _docs_lock:
        if _docs_cache is not None and _docs_cache_count == current_count:
            return _docs_cache
        try:
            es = get_es()
            res = es.search(
                index=KB_ES_INDEX,
                query={"match_all": {}},
                size=10000,
                _source={"excludes": ["vec"]},
            )
            hits = res["hits"]["hits"]
            all_docs = [
                Document(
                    page_content=h["_source"].get("content", ""),
                    metadata={k: v for k, v in h["_source"].items() if k != "content"},
                )
                for h in hits
            ]
        except Exception as e:  # noqa
            logger.warning(f"从 ES 取全量失败: {e}")
            return _docs_cache if _docs_cache is not None else []
        _docs_cache = all_docs
        _docs_cache_count = current_count if current_count >= 0 else len(all_docs)
        logger.info(f"[DocsCache] 全量文档缓存已重建: {len(all_docs)} 条")
        return all_docs


# =====================================================
# 1) 混合检索：kNN + BM25 双查询 → Python RRF 融合
# =====================================================
def hybrid_retrieve(query: str, top_k: int | None = None, tags_filter: Optional[List[str]] = None):
    """混合召回：ES kNN（语义）+ match（BM25 关键词）两路查询，Python 层 RRF 融合。

    - tags_filter 下推为 filter context（knn.filter + bool.filter 双路同时过滤），
      避免「先取 top_k 再后过滤」的召回损失；
    - 融合用标准 RRF：score(d)=Σ 1/(RRF_K+rank_i(d))。注：ES 的 rank.rrf 需要
      白金 license（Basic 报 403 non-compliant），故由 Python 融合，查询本身
      两路各一次，数据量级下开销可忽略；
    - BM25 零命中时自然退化为纯向量排序，无附加逻辑；
    - ES 异常直接上抛：调用方（chains/kb_tool）各自有兜底；此前内部吞异常
      返回 []，导致「检索基础设施故障」伪装成「知识库没查到」，LLM 据此生成
      错误答案并被 L2 缓存放大（P2 验收发现的 Agent 链路 bug 根因）。
    """
    top_k = top_k or settings.RETRIEVER_TOP_K
    from langchain_core.documents import Document

    es = get_es()
    q_emb = get_embeddings().embed_query(query)
    tag_flt = es_tags_filter(tags_filter)
    src_excl = {"excludes": ["vec"]}

    # 两路查询：kNN 语义 + BM25 关键词
    knn_res = es.search(
        index=KB_ES_INDEX,
        knn={"field": "vec", "query_vector": q_emb, "k": top_k,
             "num_candidates": max(top_k * 5, 50), "filter": tag_flt},
        size=top_k, _source=src_excl,
    )
    bm25_res = es.search(
        index=KB_ES_INDEX,
        query={"bool": {"must": [{"match": {"content": {"query": query}}}],
                        "filter": tag_flt}},
        size=top_k, _source=src_excl,
    )

    # Python RRF 融合：按 hit 顺序取 rank，score = Σ 1/(K + rank)
    rrf_k = 60
    scores: dict = {}   # id -> rrf 分
    docs_by_id: dict = {}
    for res in (knn_res, bm25_res):
        for rank, hit in enumerate(res["hits"]["hits"]):
            hid = hit["_id"]
            scores[hid] = scores.get(hid, 0.0) + 1.0 / (rrf_k + rank + 1)
            if hid not in docs_by_id:
                src = hit["_source"]
                docs_by_id[hid] = Document(
                    page_content=src.get("content", ""),
                    metadata={k: v for k, v in src.items() if k != "content"},
                )
    ranked_ids = sorted(scores, key=lambda i: scores[i], reverse=True)[:top_k]
    return [docs_by_id[i] for i in ranked_ids]


# =====================================================
# 2) Rerank 精排：BGE CrossEncoder（主力）→ Embedding 余弦相似度（兜底）
# =====================================================
def _get_reranker():
    """懒加载 BGE Reranker（CrossEncoder），单例。加载失败返回 None。"""
    global _reranker_instance, _reranker_load_attempted
    if _reranker_load_attempted:
        return _reranker_instance
    _reranker_load_attempted = True
    if not settings.RERANKER_ENABLED:
        logger.info("[Reranker] RERANKER_ENABLED=False，跳过加载")
        return None
    try:
        from FlagEmbedding import FlagReranker
        _reranker_instance = FlagReranker(
            settings.RERANKER_MODEL_NAME,
            use_fp16=settings.RERANKER_USE_FP16,
            device=settings.RERANKER_DEVICE,
        )
        logger.info(f"[Reranker] BGE CrossEncoder 已加载: {settings.RERANKER_MODEL_NAME} "
                     f"device={settings.RERANKER_DEVICE} fp16={settings.RERANKER_USE_FP16}")
        return _reranker_instance
    except Exception as e:
        logger.warning(f"[Reranker] FlagReranker 加载失败，将回退到余弦相似度: {e}")
        return None


def _rerank_with_cross_encoder(reranker, query: str, docs, top_k: int) -> List[Tuple]:
    """用 BGE CrossEncoder 做 pairwise 精排。"""
    import numpy as np
    pairs = [[query, d.page_content] for d in docs]
    try:
        raw_scores = reranker.compute_score(
            pairs,
            batch_size=settings.RERANKER_BATCH_SIZE,
            normalize=True,
        )
    except TypeError:
        # 旧版 FlagEmbedding 不支持 normalize 参数
        raw_scores = reranker.compute_score(pairs, batch_size=settings.RERANKER_BATCH_SIZE)

    # compute_score 可能返回单个 float（只有 1 个 pair 时），统一为 list
    if isinstance(raw_scores, (int, float)):
        raw_scores = [float(raw_scores)]
    else:
        raw_scores = [float(s) for s in raw_scores]

    scored = list(zip(docs, raw_scores))
    scored.sort(key=lambda x: x[1], reverse=True)
    return scored[:top_k]


def _rerank_with_cosine(query: str, docs, top_k: int) -> List[Tuple]:
    """兜底方案：Embedding 余弦相似度打分。"""
    emb = get_embeddings()
    q_emb = emb.embed_query(query)
    import numpy as np
    contents = [d.page_content for d in docs]
    doc_embs = emb.embed_documents(contents)
    q_np = np.asarray(q_emb, dtype=np.float32)
    d_np = np.asarray(doc_embs, dtype=np.float32)
    if q_np.ndim == 1:
        q_np = q_np.reshape(1, -1)
    scores = (d_np @ q_np.T).ravel() / (
        np.linalg.norm(d_np, axis=1) * np.linalg.norm(q_np) + 1e-9
    )
    scored = list(zip(docs, [float(s) for s in scores.tolist()]))
    scored.sort(key=lambda x: x[1], reverse=True)
    thr = settings.SIMILARITY_THRESHOLD
    filtered = [(d, s) for (d, s) in scored if s >= thr]
    if not filtered:
        filtered = scored[:1]
    return filtered[:top_k]


def rerank(query: str, docs, top_k: int | None = None) -> List[Tuple]:
    """返回 [(doc, score)] 已按得分倒序；top_k=精排数量。

    优先使用 BGE CrossEncoder（FlagReranker），加载失败则回退到 Embedding 余弦相似度。
    """
    top_k = top_k or settings.RERANK_TOP_K
    if not docs:
        return []

    # 1) 主力：BGE CrossEncoder
    reranker = _get_reranker()
    if reranker is not None:
        try:
            return _rerank_with_cross_encoder(reranker, query, docs, top_k)
        except Exception as e:
            logger.warning(f"[Reranker] CrossEncoder 推理失败，回退余弦相似度: {e}")

    # 2) 兜底：Embedding 余弦相似度
    try:
        return _rerank_with_cosine(query, docs, top_k)
    except Exception as e:
        logger.warning(f"[Reranker] 精排异常，返回原始 docs: {e}")
        return [(d, 0.5) for d in docs[:top_k]]


# =====================================================
# 3) 查询缓存：基于 Embedding 相似度（lru + 向量匹配）
# =====================================================
class _QueryCache:
    """FAQ高频问题缓存：缓存 {query_embedding: (answer, sources, expire_at)}；命中靠向量相似度。"""

    def __init__(self, maxsize: int = 128, ttl_seconds: int | None = None):
        import threading
        import time
        self._lock = threading.Lock()
        self._time = time
        self._vecs = []  # list[embedding]
        self._vals = []  # list[(answer, sources, expire_at)]
        self._maxsize = maxsize
        self._ttl_seconds = ttl_seconds if ttl_seconds is not None else settings.QUERY_CACHE_TTL_SECONDS

    def _is_expired(self, val) -> bool:
        return self._time.time() >= val[2]

    def _evict_expired(self) -> None:
        """惰性清理过期条目，保持索引对齐。"""
        alive = [(v, val) for v, val in zip(self._vecs, self._vals) if not self._is_expired(val)]
        self._vecs, self._vals = ([x[0] for x in alive], [x[1] for x in alive]) if alive else ([], [])

    def get(self, query: str, threshold: float | None = None) -> Optional[Tuple[str, list]]:
        threshold = threshold or settings.QUERY_CACHE_SIM_THRESHOLD
        if self._ttl_seconds <= 0:
            return None
        # embed_query 走 embeddings 层 LRU：本请求若已算过同一 query（召回阶段），此处零成本
        emb = get_embeddings().embed_query(query)
        import numpy as np
        with self._lock:
            self._evict_expired()
            if not self._vecs:
                return None
            A = np.array(self._vecs)
            b = np.array(emb)
            sims = A @ b / (np.linalg.norm(A, axis=1) * np.linalg.norm(b) + 1e-9)
            idx = int(np.argmax(sims))
            if sims[idx] >= threshold:
                logger.info(f"[QueryCache] 命中，相似={float(sims[idx]):.3f}  query={query[:20]}")
                return self._vals[idx][0], self._vals[idx][1]
        return None

    def put(self, query: str, answer: str, sources: list) -> None:
        if self._ttl_seconds <= 0:
            return
        emb = get_embeddings().embed_query(query)
        expire_at = self._time.time() + self._ttl_seconds
        with self._lock:
            self._evict_expired()
            if len(self._vecs) >= self._maxsize:
                self._vecs.pop(0)
                self._vals.pop(0)
            self._vecs.append(emb)
            self._vals.append((answer, sources, expire_at))

    def clear(self) -> None:
        """知识库发生变更时调用，清空缓存避免返回过期答案。"""
        with self._lock:
            self._vecs.clear()
            self._vals.clear()


_query_cache = _QueryCache(maxsize=settings.QUERY_CACHE_MAXSIZE)


def get_query_cache() -> _QueryCache:
    return _query_cache
