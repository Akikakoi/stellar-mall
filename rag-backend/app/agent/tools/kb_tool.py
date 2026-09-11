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


def _norm_name(s: str) -> str:
    """商品名归一化：去空白/连字符/下划线并转小写，用于 KB 文档名与商城商品名的宽松匹配。"""
    return re.sub(r'[\s\-_·・]+', '', (s or '')).lower()


def kb_specs_for_products(query: str, product_names: List[str],
                          spec_keywords: List[str], per_product: int = 3) -> dict:
    """多商品规格批量检索：一次全量拉取 ChromaDB 后按商品名分组打分。

    用于替代「逐商品调用 hybrid_retrieve」的 N+1 模式（原方案 1/2/3 最多
    13 次向量检索）：与 kb_spec_compare 同思路，全库只 get 一次，不走向量搜索。

    Args:
        query: 原始用户问题（仅用于日志与返回）
        product_names: 要补充规格的商品名列表（来自商城搜索结果）
        spec_keywords: 打分关键词，如 ["充电", "快充", "电池"]
        per_product: 每个商品最多返回的 chunk 数，默认 3
    """
    try:
        from app.rag.vector_store import get_vector_store
        from langchain_core.documents import Document

        vs = get_vector_store()
        col = vs.lc._collection
        batch = col.get(include=["documents", "metadatas"])
        raw_docs = batch.get("documents") or []
        raw_metas = batch.get("metadatas") or []

        # 按商品分组（归一化名称 -> 该商品的文档列表）。
        # 注意：库里存在 doc_name 缺失的 chunk，归一化后为空串——空串是任意字符串
        # 的子串，会参与下面的双向子串匹配导致误命中，必须跳过。
        group_docs = defaultdict(list)
        for d, m in zip(raw_docs, raw_metas):
            cname = _clean_product_name(str((m or {}).get("doc_name", "")))
            n = _norm_name(cname)
            if not n:
                continue
            group_docs[n].append(Document(page_content=d, metadata=m or {}))

        keyword_lower = [k.lower() for k in spec_keywords if k]
        sources = []
        seen_keys = set()
        matched, unmatched = [], []

        for pname in product_names:
            np_ = _norm_name(pname)
            if not np_:
                continue
            # 匹配策略：归一化精确相等优先，其次双向子串（取最长匹配）。
            # 子串匹配要求较短一方 >= 4 字符且 >= 较长一方的一半，容忍
            # "星耀X100ProMax" vs "星耀 X100 Pro Max" 这类空格差异，
            # 同时防止 "手机" 之类短词误命中任意包含它的长名字。
            best_key = None
            if np_ in group_docs:
                best_key = np_
            else:
                for n in group_docs:
                    shorter, longer = (np_, n) if len(np_) <= len(n) else (n, np_)
                    if len(shorter) >= 4 and len(shorter) * 2 >= len(longer) \
                            and (np_ in n or n in np_):
                        if best_key is None or len(n) > len(best_key):
                            best_key = n
            if best_key is None:
                unmatched.append(pname)
                continue
            matched.append(pname)

            docs = group_docs[best_key]
            scored = []
            for doc in docs:
                content_lower = doc.page_content.lower()
                hits = sum(1 for k in keyword_lower if k in content_lower)
                scored.append((doc, hits))
            # 命中数降序，无命中兜底取 1 条（商品文档用词可能不在关键词表内）
            scored.sort(key=lambda x: x[1], reverse=True)
            top = scored[:per_product] if scored and scored[0][1] > 0 else scored[:1]

            for doc, hits in top:
                meta = doc.metadata or {}
                key = (best_key, int(meta.get("chunk_index", 0)))
                if key in seen_keys:
                    continue
                seen_keys.add(key)
                sources.append({
                    "id": len(sources) + 1,
                    "doc_name": pname,  # 用商城侧商品名，保持与原 matched_product 语义一致
                    "chunk_index": int(meta.get("chunk_index", 0)),
                    "page": meta.get("page"),
                    "content": doc.page_content,
                    "score": float(hits),
                    "tags": _fix_garbled_tags(meta.get("tags") or ""),
                    "matched_product": pname,
                })

        logger.info(
            f"[kb_specs_for_products] 目标 {len(product_names)} 款 -> 命中 {len(matched)} 款，"
            f"返回 {len(sources)} 条（关键词={keyword_lower}）"
        )
        return {
            "success": True,
            "query": query,
            "sources": sources,
            "count": len(sources),
            "matched": matched,
            "unmatched": unmatched,
        }

    except Exception as e:
        logger.warning(f"[kb_specs_for_products] 失败: {e}")
        return {"success": False, "query": query, "sources": [], "count": 0,
                "matched": [], "unmatched": list(product_names or []),
                "error": str(e)}


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
        unscored_by_product = {}  # 兜底：每产品保底 1 条无命中 chunk
        for doc in tagged_docs:
            content_lower = doc.page_content.lower()
            hits = sum(1 for k in keyword_lower if k in content_lower)
            if hits > 0:
                scored.append((doc, hits))
            else:
                prod = _clean_product_name(str(doc.metadata.get("doc_name", "")))
                if prod not in unscored_by_product:
                    unscored_by_product[prod] = doc

        # 兜底：未被 scored 覆盖的产品，塞入 1 条无命中 chunk
        # 解决某些产品文档用词（如"电池"而非"电池容量"）不匹配关键词被整版排除的问题
        products_in_scored = set()
        for doc, hits in scored:
            products_in_scored.add(
                _clean_product_name(str(doc.metadata.get("doc_name", ""))))
        for prod, doc in unscored_by_product.items():
            if prod not in products_in_scored:
                scored.append((doc, 0))

        logger.info(
            f"[kb_spec_compare] 关键词匹配后 %d 条（品类共 %d 条，兜底补充 %d 款产品）",
            len(scored), len(tagged_docs),
            sum(1 for p in unscored_by_product if p not in products_in_scored)
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
