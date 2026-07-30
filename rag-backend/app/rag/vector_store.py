"""ChromaDB 向量存储：持久化、文档入库、按 doc_id 删除等。"""
from __future__ import annotations
from pathlib import Path
from typing import Any, Dict, List, Optional, Union

from app.config import settings
from app.core.logger import logger
from app.models import KbDocument
from app.rag.embeddings import get_embeddings


def _normalize_chroma_where(raw: Dict[str, Any]) -> Dict[str, Any]:
    """把「简单 key→value dict」统一转换成 Chroma 原生 where 语法。

    Chroma where 严格要求：
      - 单条件：{"field": {"$eq": value}}
      - 多条件 AND：{"$and": [{"f1": {"$eq": v1}}, {"f2": {"$eq": v2}}, ...]}
    简单 dict {"a": 1, "b": 2} 会抛「Expected where to have exactly one operator」。
    """
    if not raw:
        return {}
    # 已经是操作符形式（$and/$or/$eq 等顶级 key）→ 直接用
    top_keys = list(raw.keys())
    if len(top_keys) == 1 and top_keys[0].startswith("$"):
        return raw

    def _eq(v: Any) -> Dict[str, Any]:
        return {"$eq": v}

    conds: List[Dict[str, Any]] = [
        {k: _eq(v)} for k, v in raw.items() if not k.startswith("$")
    ]
    if not conds:
        return {}
    if len(conds) == 1:
        return conds[0]
    return {"$and": conds}


_chroma_instance = None


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


class ChromaVectorStore:
    """封装 ChromaDB 持久化实例与 CRUD。"""

    def __init__(self):
        import chromadb
        from langchain_community.vectorstores import Chroma
        persist_dir = str((Path(settings.BASE_DIR) / settings.CHROMA_PERSIST_DIR).resolve())
        Path(persist_dir).mkdir(parents=True, exist_ok=True)
        # 新版 ChromaDB（>=0.5.x，Rust Bindings）首次使用空持久化目录会校验 tenant/database，
        # 缺省时抛 ValueError/Could not connect to tenant；另外在 client.__init__ 里异常释放时
        # 还可能抛 AttributeError: 'RustBindingsAPI' object has no attribute 'bindings。
        # 另外 SharedSystemClient 会按 persist_dir 做全局缓存：若第一次 client settings 不同，
        # 后续会报 "An instance of Chroma already exists ... with different settings"。
        # 所以策略：保持一个 settings（默认），失败一次就清库重试，避免 settings 切换冲突。
        client = None
        try:
            client = chromadb.PersistentClient(path=persist_dir)
            _ = client.list_collections()
        except Exception as _first_err:
            _msg = f"{type(_first_err).__name__}: {_first_err}"
            _need_recover = ("tenant" in _msg or "database" in _msg
                             or "bindings" in _msg
                             or "An instance of Chroma already exists" in _msg)
            if not _need_recover:
                raise
            logger.info(f"[ChromaDB] 首次初始化异常（{_msg[:120]}），清库重建重试")
            import shutil as _shutil
            _sqlite = Path(persist_dir) / "chroma.sqlite3"
            if _sqlite.exists():
                try:
                    _sqlite.unlink()
                except Exception:
                    pass
            for _sub in ("chroma_data", "data"):
                _d = Path(persist_dir) / _sub
                if _d.exists():
                    _shutil.rmtree(str(_d), ignore_errors=True)
            # 释放同名缓存，否则第二次仍可能复用前一次失败的 instance
            try:
                from chromadb.api.shared_system_client import SharedSystemClient as _SSC
                _SSC.clear_system_cache()
            except Exception:
                pass
            client = chromadb.PersistentClient(path=persist_dir)
            _ = client.list_collections()
        self._client = client
        self.embeddings = get_embeddings()
        self._lc = Chroma(
            client=client,
            collection_name=settings.CHROMA_COLLECTION_NAME,
            embedding_function=self.embeddings,
        )
        logger.info(f"[ChromaDB] 使用持久化目录: {persist_dir}  collection={settings.CHROMA_COLLECTION_NAME}")

    @property
    def lc(self):
        """返回 LangChain VectorStore 接口。"""
        return self._lc

    def as_retriever(self, top_k: int, tags_filter: Optional[List[str]] = None,
                     filter: Optional[dict] = None):
        kwargs = {"k": top_k}
        if filter:
            # 显式传入 filter dict 优先级最高（用于 source / biz_id / status 等精确匹配）
            kwargs["filter"] = _normalize_chroma_where(filter)
        # 注意：tags_filter 不再通过 ChromaDB where 传递，改为在 retriever.py 的
        # hybrid_retrieve() 中做 Python 后过滤，兼容 ChromaDB 0.x/1.x 所有版本。
        return self._lc.as_retriever(search_kwargs=kwargs)

    def add_texts(self, texts: List[str], metadatas: Optional[List[dict]] = None,
                  ids: Optional[List[str]] = None):
        """直接写入文本（Mall 同步 SPU/DOC 用）。"""
        if not texts:
            return []
        return self._lc.add_texts(texts=texts, metadatas=metadatas, ids=ids)

    def delete_by_metadata_filter(self, where_filter: dict) -> int:
        """按任意 metadata 条件删除向量，返回删除条数。"""
        if not where_filter:
            return 0
        col = self._lc._collection
        where = _normalize_chroma_where(where_filter)
        try:
            res = col.get(where=where, include=[])
            ids = res.get("ids", [])
            if ids:
                col.delete(ids=ids)
                logger.info(f"[ChromaDB] 按 filter={where_filter} 删除 {len(ids)} 条向量")
            return len(ids)
        except Exception as e:  # noqa
            logger.warning(f"[ChromaDB] 按 filter={where_filter} 删除失败: {e}")
            return 0

    def add_document(self, kb_doc: KbDocument, chunk_size: int = None, chunk_overlap: int = None):
        splits = _doc_load_and_split(kb_doc, chunk_size=chunk_size, chunk_overlap=chunk_overlap)
        if not splits:
            return []
        texts = [d.page_content for d in splits]
        metas = [d.metadata for d in splits]
        ids = [f"doc{kb_doc.id}_chunk{m['chunk_index']}" for m in metas]
        self._lc.add_texts(texts=texts, metadatas=metas, ids=ids)
        logger.info(f"已入库 doc_id={kb_doc.id}  chunks={len(splits)}")
        return splits

    def delete_by_doc_id(self, doc_id: int) -> None:
        col = self._lc._collection
        where = {"doc_id": int(doc_id)}
        # 先查再删
        try:
            res = col.get(where=where, include=[])
            ids = res.get("ids", [])
            if ids:
                col.delete(ids=ids)
                logger.info(f"从 Chroma 删除 doc_id={doc_id} 共 {len(ids)} 条向量")
        except Exception as e:  # noqa
            logger.warning(f"Chroma 删除失败: {e}")

    def count(self) -> int:
        try:
            return int(self._lc._collection.count())
        except Exception:
            return 0

    def collection_empty(self) -> bool:
        return self.count() == 0


def get_vector_store() -> ChromaVectorStore:
    global _chroma_instance
    if _chroma_instance is None:
        _chroma_instance = ChromaVectorStore()
    return _chroma_instance
