"""Elasticsearch 客户端：连接单例、索引 ensure、bulk 工具与语义缓存适配器。

P2 迁移：知识库向量存储与 L2 语义缓存从 ChromaDB 迁至 ES 8.13.4（与商城
商品搜索共用同一 ES 实例，docker-composestellar-es，xpack security 关闭）。

两个索引：
  - stellar_kb_chunks    知识库 chunk（kb 文档 + mall_spu / mall_doc 同步数据）
  - stellar_llm_cache    L2 语义缓存（可丢弃数据，不做存量迁移）
"""
from __future__ import annotations

import threading
from typing import Any, Dict, List, Optional

from app.config import settings
from app.core.logger import logger

KB_ES_INDEX = "stellar_kb_chunks"
CACHE_ES_INDEX = "stellar_llm_cache"

# 与 DashScope text-embedding-v2 输出维度一致（P1 已实测 1536）
_ES_VECTOR_DIMS = 1536

_es_instance = None
_es_lock = threading.Lock()


def get_es():
    """返回 Elasticsearch 同步客户端单例（进程级）。"""
    global _es_instance
    if _es_instance is not None:
        return _es_instance
    with _es_lock:
        if _es_instance is not None:
            return _es_instance
        from elasticsearch import Elasticsearch
        es = Elasticsearch(
            settings.ES_URL,
            request_timeout=30,
            max_retries=2,
            retry_on_timeout=True,
        )
        if not es.ping():
            raise ConnectionError(f"Elasticsearch 连接失败: {settings.ES_URL}")
        _es_instance = es
        logger.info(f"[ES] 已连接 {settings.ES_URL}")
        return _es_instance


# =====================================================
# 索引 mapping（幂等 ensure）
# =====================================================
_KB_MAPPING = {
    "dynamic": True,
    "properties": {
        # chunk 正文：ik 精细分词建索引，搜索用粗粒度
        "content": {"type": "text", "analyzer": "ik_max_word", "search_analyzer": "ik_smart"},
        "vec": {"type": "dense_vector", "dims": _ES_VECTOR_DIMS,
                "index": True, "similarity": "cosine"},
        # 过滤/分组用 keyword（kb 文档 doc_id 是 int，写入时统一转 str）
        "doc_id": {"type": "keyword"},
        "source": {"type": "keyword"},
        "biz_type": {"type": "keyword"},
        "biz_id": {"type": "keyword"},
        # tags 存数组（逗号字符串拆分），过滤走 wildcard 子串语义，见 _tags_filter
        "tags": {"type": "keyword"},
        "chunk_index": {"type": "integer"},
        "total_chunks": {"type": "integer"},
        "status": {"type": "integer"},
        "spu_id": {"type": "integer"},
        "page": {"type": "integer"},
        "min_price": {"type": "float"},
    },
}

_CACHE_MAPPING = {
    "dynamic": True,
    "properties": {
        "vec": {"type": "dense_vector", "dims": _ES_VECTOR_DIMS,
                "index": True, "similarity": "cosine"},
        "cache_type": {"type": "keyword"},
        "model": {"type": "keyword"},
        "system_prompt_hash": {"type": "keyword"},
        "created_at": {"type": "float"},
        "hit_count": {"type": "integer"},
    },
}


def ensure_kb_index() -> None:
    """幂等创建知识库索引（已存在则跳过；mapping 变更需手动删除重建）。"""
    es = get_es()
    if not es.indices.exists(index=KB_ES_INDEX):
        es.indices.create(index=KB_ES_INDEX, mappings=_KB_MAPPING)
        logger.info(f"[ES] 已创建索引 {KB_ES_INDEX}（dims={_ES_VECTOR_DIMS}）")


def ensure_cache_index() -> None:
    """幂等创建 L2 语义缓存索引。"""
    es = get_es()
    if not es.indices.exists(index=CACHE_ES_INDEX):
        es.indices.create(index=CACHE_ES_INDEX, mappings=_CACHE_MAPPING)
        logger.info(f"[ES] 已创建索引 {CACHE_ES_INDEX}")


# =====================================================
# 通用工具
# =====================================================
def es_bulk_index(index: str, docs: List[Dict[str, Any]], ids: Optional[List[str]] = None) -> int:
    """bulk 写入文档（含向量字段）。ids 缺省时由 ES 自动生成。返回成功条数。"""
    if not docs:
        return 0
    from elasticsearch.helpers import bulk
    es = get_es()
    actions = []
    for i, doc in enumerate(docs):
        action: Dict[str, Any] = {"_index": index, "_source": doc}
        if ids is not None:
            action["_id"] = ids[i]
        actions.append(action)
    ok, _errors = bulk(es, actions, raise_on_error=False, refresh=False)
    failed = len(docs) - int(ok or 0)
    if failed:
        logger.warning(f"[ES] bulk 写入 {index} 有 {failed} 条失败")
    return int(ok or 0)


def es_refresh(index: str) -> None:
    """强制 refresh，让刚写入的数据立即可搜（迁移/同步场景用，常规写路径不调）。"""
    try:
        get_es().indices.refresh(index=index)
    except Exception as e:  # noqa
        logger.warning(f"[ES] refresh {index} 失败: {e}")


def _tags_should(tags_filter: List[str]) -> List[Dict[str, Any]]:
    """把 tags_filter 转成 wildcard should 子句，还原 Chroma 时代的「子串包含」语义。

    原实现（retriever._matches_tag_filter）对逗号 tags 字符串做 `t in tags` 判断，
    属于子串包含；ES terms 是精确匹配，无法命中 "智能手机" ⊃ "手机" 这类情况。
    keyword 数组上的 wildcard 逐元素匹配，任一元素含子串即命中，语义等价。
    """
    return [
        {"wildcard": {"tags": {"value": f"*{t}*"}}}
        for t in tags_filter if t
    ]


def es_tags_filter(tags_filter: Optional[List[str]]) -> List[Dict[str, Any]]:
    """生成 tags 过滤子句（放进 bool.filter / knn.filter）。"""
    if not tags_filter:
        return []
    should = _tags_should(tags_filter)
    return [{"bool": {"should": should, "minimum_should_match": 1}}]


# =====================================================
# L2 语义缓存适配器：Chroma Collection 风格接口 → ES 实现
# =====================================================
class ESCacheCollection:
    """以 Chroma Collection 同构接口封装 ES 语义缓存索引。

    提供 count / query / add / update / get / delete 六个方法，参数与返回值
    结构对齐 Chroma（query/get 返回「列表套列表」），llm_cache.py 的检索、
    精排、失效逻辑无需改动，仅替换 collection 获取入口。
    """

    def __init__(self):
        ensure_cache_index()
        self._index = CACHE_ES_INDEX

    # ---- count ----
    def count(self) -> int:
        return int(get_es().count(index=self._index)["count"])

    # ---- kNN 查询（返回结构对齐 Chroma query）----
    def query(self, query_embeddings: List[List[float]], n_results: int,
              include: Optional[List[str]] = None, **_kw) -> Dict[str, Any]:
        include = include or ["documents", "metadatas", "distances", "embeddings"]
        q_vec = query_embeddings[0]
        body = {
            "knn": {
                "field": "vec",
                "query_vector": q_vec,
                "k": n_results,
                "num_candidates": max(n_results * 5, 50),
            },
            "size": n_results,
            "_source": True,  # _source 含 vec，供 Python 余弦精排复用
        }
        res = get_es().search(index=self._index, **body)
        hits = res["hits"]["hits"]
        ids: List[str] = []
        metas: List[Dict[str, Any]] = []
        embs: List[List[float]] = []
        distances: List[float] = []
        for hit in hits:
            src = hit["_source"]
            ids.append(hit["_id"])
            metas.append({k: v for k, v in src.items() if k != "vec"})
            embs.append(src.get("vec") or [])
            # ES cosine: _score=(1+cos)/2 → cos=2*score-1 → distance=1-cos
            score = float(hit.get("_score") or 0.0)
            distances.append(max(0.0, min(2.0, 1.0 - (2.0 * score - 1.0))))
        # Chroma 返回「每查询一行」，这里单查询保持同构
        out: Dict[str, Any] = {"ids": [ids]}
        out["metadatas"] = [metas] if "metadatas" in include else [[]]
        out["embeddings"] = [embs] if "embeddings" in include else [[]]
        out["distances"] = [distances] if "distances" in include else [[]]
        return out

    # ---- 写入 ----
    def add(self, ids: List[str], embeddings: List[List[float]],
            metadatas: List[Dict[str, Any]], documents: Optional[List[str]] = None) -> None:
        docs = []
        for i, meta in enumerate(metadatas):
            doc = dict(meta or {})
            doc["vec"] = embeddings[i]
            if documents is not None:
                doc["content"] = documents[i]
            docs.append(doc)
        es_bulk_index(self._index, docs, ids=ids)
        es_refresh(self._index)

    # ---- 局部更新（hit_count 等字段）----
    def update(self, ids: List[str], metadatas: List[Dict[str, Any]]) -> None:
        es = get_es()
        for _id, meta in zip(ids, metadatas):
            # Chroma update 是 metadata 整体覆盖；ES 用全量 doc 覆盖（不含 vec 字段，
            # 缺省字段不会删除已有值，与 Chroma「只更新 metadata、向量不动」语义一致）
            es.update(index=self._index, id=_id, doc=dict(meta or {}),
                      retry_on_conflict=3, refresh=False)

    # ---- 全量/条件拉取（当前调用方只用全量拉取 + include 过滤）----
    def get(self, where: Optional[Dict[str, Any]] = None,
            include: Optional[List[str]] = None, **_kw) -> Dict[str, Any]:
        include = include or []
        body: Dict[str, Any] = {
            "query": {"match_all": {}},
            "size": 10000,
            "_source": {"excludes": ["vec"]},
        }
        if where:
            must = [{"term": {k: v}} for k, v in where.items()]
            body["query"] = {"bool": {"must": must}}
        res = get_es().search(index=self._index, **body)
        hits = res["hits"]["hits"]
        out: Dict[str, Any] = {"ids": [h["_id"] for h in hits]}
        if "documents" in include:
            out["documents"] = [h["_source"].get("content") for h in hits]
        if "metadatas" in include:
            out["metadatas"] = [h["_source"] for h in hits]
        if "embeddings" in include:
            # 需要 embedding 的场景改走 query；此处显式再查一次按 id 批量取
            es = get_es()
            mget = es.mget(index=self._index, ids=out["ids"], _source_includes=["vec"])
            out["embeddings"] = [
                (doc.get("_source") or {}).get("vec") or []
                for doc in mget["docs"]
            ] if out["ids"] else []
        return out

    # ---- 删除 ----
    def delete(self, ids: List[str]) -> None:
        if not ids:
            return
        es = get_es()
        from elasticsearch.helpers import bulk as _bulk
        actions = [{"_op_type": "delete", "_index": self._index, "_id": i} for i in ids]
        _bulk(es, actions, raise_on_error=False)
        es_refresh(self._index)
