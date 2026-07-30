"""检索器：向量召回 + BM25 混合 + Rerank 精排 + 缓存。"""
from __future__ import annotations
from typing import List, Optional, Tuple

from app.config import settings
from app.core.logger import logger
from app.rag.embeddings import get_embeddings
from app.rag.vector_store import get_vector_store


# =====================================================
# 1) 混合检索：EnsembleRetriever
# =====================================================
def _build_bm25_retriever_from_chroma(top_k: int, tags_filter: Optional[List[str]]):
    """从 Chroma 拿全部 docs 构建一个 BM25（量级不大时可行）。更高级可切到 Elasticsearch 等。"""
    try:
        from langchain_community.retrievers import BM25Retriever
    except Exception as e:  # noqa
        logger.warning(f"BM25Retriever 不可用: {e}")
        return None
    vs = get_vector_store()
    col = vs.lc._collection
    where = None
    if tags_filter:
        or_cond = [{"tags": {"$contains": t}} for t in tags_filter]
        where = {"$or": or_cond} if len(or_cond) > 1 else or_cond[0]
    try:
        batch = col.get(where=where, include=["documents", "metadatas"])
    except Exception as e:  # noqa
        logger.warning(f"从 Chroma 取全量失败: {e}")
        return None
    docs = batch.get("documents") or []
    metas = batch.get("metadatas") or []
    if not docs:
        return None
    from langchain_core.documents import Document
    bm25 = BM25Retriever.from_documents(
        [Document(page_content=d, metadata=m or {}) for d, m in zip(docs, metas)]
    )
    bm25.k = top_k
    return bm25


def hybrid_retrieve(query: str, top_k: int | None = None, tags_filter: Optional[List[str]] = None):
    """混合召回：向量 + BM25（若可用）；若 EnsembleRetriever 不可用则只走向量。"""
    top_k = top_k or settings.RETRIEVER_TOP_K
    vs = get_vector_store()
    vs.k = top_k  # type: ignore
    vector_ret = vs.as_retriever(top_k=top_k, tags_filter=tags_filter)
    bm25 = _build_bm25_retriever_from_chroma(top_k, tags_filter)
    if bm25 is None:
        return vector_ret.invoke(query)
    try:
        try:
            from langchain_community.retrievers import EnsembleRetriever  # langchain 1.x 新路径
        except Exception:
            from langchain.retrievers import EnsembleRetriever  # langchain 0.3 旧路径
        ensemble = EnsembleRetriever(retrievers=[bm25, vector_ret], weights=[0.4, 0.6])
        # EnsembleRetriever 不直接支持 top_k，由各内部 retriever 控制
        docs = ensemble.invoke(query)
        return docs[:top_k]
    except Exception as e:  # noqa
        logger.warning(f"EnsembleRetriever 不可用: {e}")
        return vector_ret.invoke(query)


# =====================================================
# 2) Rerank 精排：直接用 Embedding 余弦相似度 + DashScope（用户要求云端API）
# =====================================================
def rerank(query: str, docs, top_k: int | None = None) -> List[Tuple]:
    """返回 [(doc, score)] 已按得分倒序；top_k=精排数量。"""
    top_k = top_k or settings.RERANK_TOP_K
    if not docs:
        return []
    try:
        # 直接用 Embedding 余弦相似度打分（DashScope 向量）
        emb = get_embeddings()
        q_emb = emb.embed_query(query)
        import numpy as np
        # 批量 embedding 文档，减少调用次数
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
            # 若全部低于阈值，至少返回最相关的一条（让系统能给低置信度参考）
            filtered = scored[:1]
        return filtered[:top_k]
    except Exception as e:  # noqa
        logger.warning(f"精排异常，返回原始 docs: {e}")
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
