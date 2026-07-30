from langchain_core.tools import tool
from typing import List, Optional
from collections import defaultdict
import re

from app.config import settings
from app.core.logger import logger
from app.rag.retriever import hybrid_retrieve, rerank, _matches_tag_filter
from app.rag.chains import _fix_garbled_tags


def _clean_product_name(doc_name: str) -> str:
    """把文档文件名转成人能读的商品名。
    
    例: "玉米手机 10 Pro.md" → "玉米手机 10 Pro"
        "星耀X100ProMax-产品参数.md" → "星耀 X100 Pro Max"
        "stellar-x1-pro.md" → "Stellar X1 Pro"
        "充电器.md" → "充电器"
    """
    name = doc_name
    # 去掉 .md / .txt 等扩展名和 "产品参数/spec" 后缀
    name = re.sub(r'\.(md|txt|docx|pdf)$', '', name, flags=re.IGNORECASE)
    name = re.sub(r'[-_]?产品(参数|规格|信息|说明书)[-_]?', '', name)
    name = re.sub(r'[-_]?spec[s]?[-_]?', '', name, flags=re.IGNORECASE)
    # 把 kebab-case / snake_case 转空格
    name = re.sub(r'[-_]', ' ', name)
    # 压缩多余空格
    name = re.sub(r'\s+', ' ', name).strip()
    return name


@tool
def kb_search_tool(query: str, top_k: int = 5, tags_filter: Optional[List[str]] = None) -> dict:
    """知识库检索工具，用于查询商品信息、售后政策、使用说明等静态知识。"""
    try:
        recalled = hybrid_retrieve(query, top_k=settings.RETRIEVER_TOP_K, tags_filter=tags_filter)
        ranked = rerank(query, recalled, top_k=top_k)
        return _ranked_to_result(query, ranked)
    except Exception as e:
        return {"success": False, "query": query, "sources": [], "count": 0, "error": str(e)}


def _ranked_to_result(query: str, ranked) -> dict:
    sources = []
    for i, (doc, score) in enumerate(ranked, start=1):
        meta = doc.metadata or {}
        sources.append({
            "id": i,
            "doc_name": _clean_product_name(str(meta.get("doc_name", "未知文档"))),
            "chunk_index": int(meta.get("chunk_index", 0)),
            "page": meta.get("page"),
            "content": doc.page_content,
            "score": float(score),
            "tags": _fix_garbled_tags(meta.get("tags") or ""),
        })
    return {"success": True, "query": query, "sources": sources, "count": len(sources)}


def kb_spec_compare(query: str, tags_filter: List[str], spec_keywords: List[str],
                    per_product: int = 3) -> dict:
    """规格比较专用检索：直接从 ChromaDB 拉全部品类文档，不走任何向量搜索。

    向量搜索的 top-K 排名对自然语言问句 vs 结构化 spec 极不友好，
    经常漏掉大量品类内文档。改为：取全量 -> Python 标签过滤 ->
    关键词匹配 -> 按产品分组 -> 每产品 top N。

    Args:
        query: 原始用户问题（仅用于日志）
        tags_filter: 品类标签，如 ["智能手机"]、["平板电脑"]
        spec_keywords: 规格关键词列表，如 ["充电", "快充", "W", "电池"]
        per_product: 每个产品最多返回的 chunk 数，默认 3
    """
    try:
        # 1) 直接从 ChromaDB 取出全部文档，Python 过滤 tags
        from app.rag.vector_store import get_vector_store
        from langchain_core.documents import Document

        vs = get_vector_store()
        col = vs.lc._collection
        batch = col.get(include=["documents", "metadatas"])
        raw_docs = batch.get("documents") or []
        raw_metas = batch.get("metadatas") or []
        all_docs = [Document(page_content=d, metadata=m or {})
                    for d, m in zip(raw_docs, raw_metas)]

        tagged_docs = [d for d in all_docs if _matches_tag_filter(d.metadata, tags_filter)] if tags_filter else all_docs
        logger.info(
            f"[kb_spec_compare] 全库 %d 条 -> tags_filter=%s -> %d 条",
            len(all_docs), tags_filter, len(tagged_docs)
        )

        if not tagged_docs:
            return {"success": True, "query": query, "sources": [], "count": 0,
                    "product_count": 0, "product_names": []}

        # 2) 关键词打分：每个 chunk 对 spec_keywords 的命中数
        keyword_lower = [k.lower() for k in spec_keywords]
        scored = []
        for doc in tagged_docs:
            content_lower = doc.page_content.lower()
            hits = sum(1 for k in keyword_lower if k in content_lower)
            if hits > 0:
                scored.append((doc, hits))

        logger.info(
            f"[kb_spec_compare] 关键词匹配后 %d 条（品类共 %d 条）",
            len(scored), len(tagged_docs)
        )

        # 3) 按产品分组，每个产品取命中数最高的前 per_product 个 chunk
        product_chunks = defaultdict(list)
        for doc, hits in scored:
            product_name = _clean_product_name(str(doc.metadata.get("doc_name", "")))
            product_chunks[product_name].append((doc, hits))

        # 4) 每个产品内按命中数排序，取 top N
        sources = []
        for prod_name, chunks in sorted(product_chunks.items()):
            chunks.sort(key=lambda x: x[1], reverse=True)
            for doc, hits in chunks[:per_product]:
                meta = doc.metadata or {}
                sources.append({
                    "id": len(sources) + 1,
                    "doc_name": prod_name,  # _clean_product_name 已在上方处理
                    "chunk_index": int(meta.get("chunk_index", 0)),
                    "page": meta.get("page"),
                    "content": doc.page_content,
                    "score": float(hits),
                    "tags": _fix_garbled_tags(meta.get("tags") or ""),
                })

        product_names = sorted(product_chunks.keys())
        logger.info(
            f"[kb_spec_compare] 最终返回 %d 条，覆盖 %d 款产品: %s",
            len(sources), len(product_chunks), product_names
        )
        return {
            "success": True,
            "query": query,
            "sources": sources,
            "count": len(sources),
            "product_count": len(product_chunks),
            "product_names": product_names,
        }

    except Exception as e:
        logger.warning(f"[kb_spec_compare] 失败: {e}")
        return {"success": False, "query": query, "sources": [], "count": 0,
                "error": str(e)}
