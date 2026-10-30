"""Elasticsearch 向量存储：持久化、文档入库、按 doc_id 删除等。

P2 迁移：原 ChromaDB 实现整体替换为 ES（与商城商品搜索共用同一 ES 8.13.4 实例）。
对外接口保持与原 ChromaVectorStore 一致（add_texts / add_document /
delete_by_doc_id / delete_by_metadata_filter / count），调用方无需感知底层变化。
"""
from __future__ import annotations

import re
from pathlib import Path
from typing import Any, Dict, List, Optional

from app.config import settings
from app.core.logger import logger
from app.models import KbDocument
from app.rag.embeddings import get_embeddings
from app.rag.es_client import KB_ES_INDEX, es_bulk_index, es_refresh, ensure_kb_index, get_es

_es_vs_instance = None


def _split_tags(raw: Any) -> List[str]:
    """把 tags 统一拆成数组：兼容英文/中文逗号分隔字符串与已有 list。

    ES mapping 里 tags 是 keyword 数组，检索过滤走 wildcard 子串语义
    （见 es_client.es_tags_filter），拆分数组后子串匹配逐元素进行，
    与原 Chroma「逗号字符串 in 判断」语义等价。
    """
    if not raw:
        return []
    if isinstance(raw, list):
        return [str(t).strip() for t in raw if str(t).strip()]
    return [t.strip() for t in re.split(r"[,，]", str(raw)) if t.strip()]


def _normalize_doc(meta: Dict[str, Any]) -> Dict[str, Any]:
    """规整一条 metadata 为可写入 ES 的文档字段（不含向量）。"""
    doc = dict(meta or {})
    if "tags" in doc:
        doc["tags"] = _split_tags(doc.get("tags"))
    # doc_id 在 kb 文档是 int、mall_doc 是字符串；mapping 为 keyword，统一转 str
    if doc.get("doc_id") is not None:
        doc["doc_id"] = str(doc["doc_id"])
    # Chroma 时代允许 None 元数据值；ES 显式剔除，避免 dynamic mapping 噪声
    return {k: v for k, v in doc.items() if v is not None}


def _doc_load_and_split(kb_doc: KbDocument, chunk_size: int = None, chunk_overlap: int = None) -> List:
    """根据扩展名加载文档并切分，返回 LangChain Document 列表。
    每个 Document 的 metadata 带上 doc_id / doc_name / chunk_index / tags。
    """
    from langchain_text_splitters import RecursiveCharacterTextSplitter
    from langchain_core.documents import Document

    file_path = Path(settings.BASE_DIR) / kb_doc.file_path
    ext = kb_doc.file_ext.lower()
    if not file_path.exists():
        raise FileNotFoundError(f"文档文件不存在: {file_path}")
    docs: List[Document] = []
    if ext == ".pdf":
        # PyMuPDFLoader 比 PyPDFLoader 对表格/排版的保留效果好得多，避免行列错位
        try:
            from langchain_community.document_loaders import PyMuPDFLoader
            loader = PyMuPDFLoader(str(file_path))
            docs = loader.load()
        except ImportError:
            from langchain_community.document_loaders import PyPDFLoader
            loader = PyPDFLoader(str(file_path))
            docs = loader.load()
    elif ext == ".docx":
        from langchain_community.document_loaders import Docx2txtLoader
        docs = Docx2txtLoader(str(file_path)).load()
    elif ext in (".md", ".txt"):
        from langchain_community.document_loaders import TextLoader
        docs = TextLoader(str(file_path), encoding="utf-8").load()
    elif ext == ".csv":
        from langchain_community.document_loaders import CSVLoader
        docs = CSVLoader(str(file_path), encoding="utf-8-sig").load()
    else:
        # 兜底：按文本读
        from langchain_community.document_loaders import UnstructuredFileLoader
        docs = UnstructuredFileLoader(str(file_path)).load()

    splitter = RecursiveCharacterTextSplitter(
        chunk_size=chunk_size if chunk_size else settings.CHUNK_SIZE,
        chunk_overlap=chunk_overlap if chunk_overlap is not None else settings.CHUNK_OVERLAP,
        separators=["\n\n", "\n", "。", "！", "？", ";", "；", " ", ""],
    )
    splits = splitter.split_documents(docs)
    # 统一 metadata
    for i, s in enumerate(splits):
        page = s.metadata.get("page")
        s.metadata = {
            "doc_id": kb_doc.id,
            "doc_name": kb_doc.filename,
            "tags": kb_doc.tags or "",
            "chunk_index": i,
            "page": int(page) if page is not None else None,
            "source_file": kb_doc.file_path,
        }
    logger.info(f"文档 {kb_doc.filename} 切分得到 {len(splits)} 个 Chunk")
    return splits


class EsVectorStore:
    """封装 Elasticsearch 知识库索引的写入与删除（接口对齐原 ChromaVectorStore）。"""

    def __init__(self):
        ensure_kb_index()
        self.embeddings = get_embeddings()
        logger.info(f"[ES] 知识库向量存储就绪: index={KB_ES_INDEX}")

    def add_texts(self, texts: List[str], metadatas: Optional[List[dict]] = None,
                  ids: Optional[List[str]] = None):
        """写入文本（embedding + bulk 入 ES，Mall 同步 SPU/DOC 与 kb 上传共用）。"""
        if not texts:
            return []
        metadatas = metadatas or [{} for _ in texts]
        vectors = self.embeddings.embed_documents(texts)
        docs = []
        for text, meta, vec in zip(texts, metadatas, vectors):
            d = _normalize_doc(meta)
            d["content"] = text
            d["vec"] = vec
            docs.append(d)
        es_bulk_index(KB_ES_INDEX, docs, ids=ids)
        logger.info(f"[ES] 入库 {len(docs)} 条 chunk（index={KB_ES_INDEX}）")
        return list(ids) if ids else []

    def delete_by_metadata_filter(self, where_filter: dict) -> int:
        """按任意 metadata 条件删除（term 精确匹配，全 AND），返回删除条数。"""
        if not where_filter:
            return 0
        es = get_es()
        must = []
        for k, v in where_filter.items():
            if v is None:
                continue
            must.append({"term": {k: str(v) if k in ("doc_id", "biz_id") else v}})
        if not must:
            return 0
        try:
            res = es.delete_by_query(
                index=KB_ES_INDEX,
                query={"bool": {"must": must}},
                refresh=True,
            )
            deleted = int(res.get("deleted", 0))
            logger.info(f"[ES] 按 filter={where_filter} 删除 {deleted} 条 chunk")
            return deleted
        except Exception as e:  # noqa
            logger.warning(f"[ES] 按 filter={where_filter} 删除失败: {e}")
            return 0

    def add_document(self, kb_doc: KbDocument, chunk_size: int = None, chunk_overlap: int = None):
        splits = _doc_load_and_split(kb_doc, chunk_size=chunk_size, chunk_overlap=chunk_overlap)
        if not splits:
            return []
        texts = [d.page_content for d in splits]
        metas = [d.metadata for d in splits]
        ids = [f"doc{kb_doc.id}_chunk{m['chunk_index']}" for m in metas]
        self.add_texts(texts=texts, metadatas=metas, ids=ids)
        logger.info(f"已入库 doc_id={kb_doc.id}  chunks={len(splits)}")
        return splits

    def delete_by_doc_id(self, doc_id: int) -> None:
        deleted = self.delete_by_metadata_filter({"doc_id": int(doc_id)})
        if deleted:
            logger.info(f"从 ES 删除 doc_id={doc_id} 共 {deleted} 条向量")

    def get_by_doc_id(self, doc_id: int) -> Dict[str, Any]:
        """拉取某文档的全部 chunk（list_chunks 预览用）。

        返回结构对齐原 Chroma col.get：{"ids", "documents", "metadatas"}。
        """
        es = get_es()
        res = es.search(
            index=KB_ES_INDEX,
            query={"term": {"doc_id": str(doc_id)}},
            size=10000,
            _source={"excludes": ["vec"]},
        )
        hits = res["hits"]["hits"]
        return {
            "ids": [h["_id"] for h in hits],
            "documents": [h["_source"].get("content", "") for h in hits],
            "metadatas": [{k: v for k, v in h["_source"].items() if k not in ("content", "vec")}
                          for h in hits],
        }

    def count(self) -> int:
        try:
            return int(get_es().count(index=KB_ES_INDEX)["count"])
        except Exception:
            return 0

    def collection_empty(self) -> bool:
        return self.count() == 0

    def refresh(self) -> None:
        """强制 refresh（写入后立即可搜，同步/迁移场景用）。"""
        es_refresh(KB_ES_INDEX)


def get_vector_store() -> EsVectorStore:
    global _es_vs_instance
    if _es_vs_instance is None:
        _es_vs_instance = EsVectorStore()
    return _es_vs_instance
