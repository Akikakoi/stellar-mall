from langchain_core.tools import tool
from typing import List, Optional

from app.config import settings
from app.rag.retriever import hybrid_retrieve, rerank
from app.rag.chains import _fix_garbled_tags


@tool
def kb_search_tool(query: str, top_k: int = 5, tags_filter: Optional[List[str]] = None) -> dict:
    """知识库检索工具，用于查询商品信息、售后政策、使用说明等静态知识。

    Args:
        query: 用户的查询问题
        top_k: 返回的最相关文档数量，默认5条
        tags_filter: 按标签过滤检索范围，可选
    """
    try:
        recalled = hybrid_retrieve(query, top_k=settings.RETRIEVER_TOP_K, tags_filter=tags_filter)
        ranked = rerank(query, recalled, top_k=top_k)

        sources = []
        for i, (doc, score) in enumerate(ranked, start=1):
            meta = doc.metadata or {}
            sources.append({
                "id": i,
                "doc_name": str(meta.get("doc_name", "未知文档")),
                "chunk_index": int(meta.get("chunk_index", 0)),
                "page": meta.get("page"),
                "content": doc.page_content,
                "score": float(score),
                "tags": _fix_garbled_tags(meta.get("tags") or ""),
            })

        return {
            "success": True,
            "query": query,
            "sources": sources,
            "count": len(sources),
        }
    except Exception as e:
        return {
            "success": False,
            "query": query,
            "sources": [],
            "count": 0,
            "error": str(e),
        }
