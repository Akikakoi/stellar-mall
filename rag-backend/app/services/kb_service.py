"""知识库服务（最小实现：DB层CRUD + 空索引器）。后续由 RAG 模块补齐向量化。"""
from __future__ import annotations
import os
import shutil
import tempfile
import uuid
from datetime import datetime
from pathlib import Path
from typing import Optional, List

from fastapi import UploadFile, Request
from sqlalchemy.orm import Session

from app.config import settings
from app.core.exceptions import BizError
from app.core.logger import logger
from app.core.utils import escape_sql_like
from app.models import KbDocument, User
from app.rag.retriever import get_query_cache
from app.schemas import KbDocInfo, KbDocUpdateReq, KbPreviewResp
from app.services.operation_log_service import log_operation


class KbService:
    def __init__(self, db: Session):
        self.db = db
        self.upload_dir = Path(settings.UPLOAD_DIR).resolve()
        self.upload_dir.mkdir(parents=True, exist_ok=True)

    @staticmethod
    def _clear_query_cache() -> None:
        """知识库内容发生变更后清空 FAQ 查询缓存，避免返回过期答案。"""
        try:
            get_query_cache().clear()
            logger.info("知识库变更，已清空查询缓存")
        except Exception as e:  # noqa
            logger.warning(f"清空查询缓存失败: {e}")

    # ---------- 列表 ----------
    def list_docs(self, page: int = 1, page_size: int = 20, keyword: str = "",
                  status: Optional[str] = None):
        q = self.db.query(KbDocument)
        if keyword:
            # LIKE 通配符必须先转义，避免用户输入 '%' / '_' 被当作 SQL 通配符
            safe_keyword = f"%{escape_sql_like(keyword)}%"
            q = q.filter(KbDocument.filename.like(safe_keyword))
        if status:
            status_list = [s.strip() for s in status.split(",") if s.strip()]
            if len(status_list) == 1:
                q = q.filter(KbDocument.status == status_list[0])
            elif status_list:
                q = q.filter(KbDocument.status.in_(status_list))
        total = q.count()
        rows = (q.order_by(KbDocument.created_at.desc())
                .offset((page - 1) * page_size)
                .limit(page_size).all())
        return {
            "items": [KbDocInfo.model_validate(r).model_dump() for r in rows],
            "total": total, "page": page, "page_size": page_size,
        }

    # ---------- 详情 ----------
    def get_doc(self, doc_id: int) -> dict:
        doc = self.db.query(KbDocument).filter(KbDocument.id == doc_id).first()
        if not doc:
            raise BizError("文档不存在", code=40402, http_status=404)
        return KbDocInfo.model_validate(doc).model_dump()

    # ---------- 更新 ----------
    def update_doc(self, doc_id: int, req: KbDocUpdateReq) -> dict:
        doc = self.db.query(KbDocument).filter(KbDocument.id == doc_id).first()
        if not doc:
            raise BizError("文档不存在", http_status=404)
        if req.tags is not None:
            doc.tags = req.tags
        if req.filename:
            doc.filename = req.filename
        doc.updated_at = datetime.now()
        self.db.commit()
        self.db.refresh(doc)
        return KbDocInfo.model_validate(doc).model_dump()

    # ---------- 上传 + 入库（向量化通过 rag 模块执行） ----------
    async def upload_and_index(self, file, tags: str, operator: User, request: Optional[Request] = None,
                               chunk_size: int = None, chunk_overlap: int = None):
        if not file or not getattr(file, "filename", None):
            raise BizError("请上传文件", code=40020)
        filename = str(file.filename)
        ext = Path(filename).suffix.lower()
        if ext not in settings.ALLOWED_EXT_LIST:
            raise BizError(f"不支持的文件类型: {ext}，允许 {settings.ALLOWED_EXT_LIST}", code=40021)
        # 保存文件
        uid = uuid.uuid4().hex[:10]
        safe_name = f"{uid}_{filename}"
        save_path = self.upload_dir / safe_name
        content = await file.read()
        if len(content) > settings.MAX_UPLOAD_SIZE:
            raise BizError(f"文件过大，最大 {settings.MAX_UPLOAD_SIZE // 1024 // 1024}MB", code=40022)
        save_path.write_bytes(content)
        # 先插入 DB
        doc = KbDocument(
            filename=filename,
            file_path=str(save_path.relative_to(settings.BASE_DIR)),
            file_ext=ext,
            file_size=len(content),
            tags=tags,
            status="parsing",
            created_by=operator.id,
        )
        self.db.add(doc)
        self.db.commit()
        self.db.refresh(doc)
        log_operation(self.db, user=operator, action="kb_upload", resource=f"doc_id={doc.id} {filename}",
                      detail=f"size={len(content)}", request=request)
        # 异步执行向量化
        try:
            from app.rag.vector_store import get_vector_store
            vs = get_vector_store()
            chunks = vs.add_document(doc, chunk_size=chunk_size, chunk_overlap=chunk_overlap)
            doc.status = "ready"
            doc.chunk_count = len(chunks)
            self.db.commit()
            self._clear_query_cache()
        except Exception as e:  # noqa
            logger.exception(f"文档向量化失败 doc_id={doc.id}: {e}")
            doc.status = "error"
            doc.error_msg = str(e)[:1000]
            self.db.commit()
        self.db.refresh(doc)
        return KbDocInfo.model_validate(doc).model_dump()

    # ---------- 删除 ----------
    def delete_doc(self, doc_id: int, operator: User, request: Optional[Request] = None) -> None:
        doc = self.db.query(KbDocument).filter(KbDocument.id == doc_id).first()
        if not doc:
            raise BizError("文档不存在", http_status=404)
        # 删除向量库
        try:
            from app.rag.vector_store import get_vector_store
            get_vector_store().delete_by_doc_id(doc_id)
        except Exception as e:  # noqa
            logger.warning(f"从向量库删除 doc_id={doc_id} 失败(可能未入库): {e}")
        # 删除物理文件
        try:
            fp = Path(settings.BASE_DIR) / doc.file_path
            if fp.exists():
                fp.unlink()
        except Exception as e:  # noqa
            logger.warning(f"删除物理文件失败: {e}")
        self.db.delete(doc)
        self.db.commit()
        self._clear_query_cache()
        log_operation(self.db, user=operator, action="kb_delete", resource=f"doc_id={doc_id} {doc.filename}",
                      request=request)

    def stats(self) -> dict:
        total_docs = self.db.query(KbDocument).count()
        total_chunks = sum((d.chunk_count or 0) for d in self.db.query(KbDocument).all())
        by_ext = {}
        for d in self.db.query(KbDocument).all():
            by_ext[d.file_ext] = by_ext.get(d.file_ext, 0) + 1
        return {"total_docs": total_docs, "total_chunks": total_chunks, "by_ext": by_ext}

    # ---------- 文件切分预览（不落库，不上传） ----------
    async def preview_file(self, file, chunk_size: Optional[int] = None,
                           chunk_overlap: Optional[int] = None,
                           limit: int = 50) -> dict:
        if not file or not getattr(file, "filename", None):
            raise BizError("请上传文件", code=40020)
        filename = str(file.filename)
        ext = Path(filename).suffix.lower()
        if ext not in settings.ALLOWED_EXT_LIST:
            raise BizError(f"不支持的文件类型: {ext}，允许 {settings.ALLOWED_EXT_LIST}", code=40021)
        # 临时保存文件内容
        content = await file.read()
        if len(content) > settings.MAX_UPLOAD_SIZE:
            raise BizError(f"文件过大，最大 {settings.MAX_UPLOAD_SIZE // 1024 // 1024}MB", code=40022)
        if not content:
            raise BizError("文件为空", code=40023)
        # 写入临时文件供 loader 读取
        with tempfile.NamedTemporaryFile(suffix=ext, delete=False) as tmp:
            tmp.write(content)
            tmp_path = Path(tmp.name)
        try:
            # 构造伪 KbDocument（仅用于 _doc_load_and_split）
            pseudo_doc = KbDocument(
                id=0, filename=filename, file_path=str(tmp_path),
                file_ext=ext, tags="", status="parsing",
            )
            # 临时覆盖 chunk 配置
            saved_size, saved_overlap = settings.CHUNK_SIZE, settings.CHUNK_OVERLAP
            try:
                if chunk_size and 128 <= chunk_size <= 4096:
                    settings.CHUNK_SIZE = chunk_size
                if chunk_overlap and 0 <= chunk_overlap <= 1024:
                    settings.CHUNK_OVERLAP = chunk_overlap
                from app.rag.vector_store import _doc_load_and_split
                splits = _doc_load_and_split(pseudo_doc)
            finally:
                settings.CHUNK_SIZE, settings.CHUNK_OVERLAP = saved_size, saved_overlap
        finally:
            try:
                tmp_path.unlink()
            except Exception:
                pass

        total = len(splits)
        shown = splits[:limit]
        chunks_payload = [
            {
                "index": i,
                "content": s.page_content,
                "char_count": len(s.page_content),
                "page": s.metadata.get("page"),
            }
            for i, s in enumerate(shown)
        ]
        return KbPreviewResp(
            total_chunks=total,
            estimated_chunk_count=total,
            chunks=chunks_payload,
        ).model_dump()

    # ---------- 重新索引（已有文件） ----------
    def reindex(self, doc_id: int, operator: User,
                request: Optional[Request] = None) -> dict:
        doc = self.db.query(KbDocument).filter(KbDocument.id == doc_id).first()
        if not doc:
            raise BizError("文档不存在", code=40402, http_status=404)
        # 先清除旧向量
        try:
            from app.rag.vector_store import get_vector_store
            get_vector_store().delete_by_doc_id(doc_id)
        except Exception as e:  # noqa
            logger.warning(f"reindex 前清旧向量失败 doc_id={doc_id}: {e}")
        doc.status = "indexing"
        doc.error_msg = None
        doc.chunk_count = 0
        self.db.commit()
        # 执行向量化
        try:
            from app.rag.vector_store import get_vector_store
            vs = get_vector_store()
            chunks = vs.add_document(doc)
            doc.status = "ready"
            doc.chunk_count = len(chunks)
            doc.error_msg = None
            self.db.commit()
            self._clear_query_cache()
        except Exception as e:  # noqa
            logger.exception(f"重新向量化失败 doc_id={doc.id}: {e}")
            doc.status = "error"
            doc.error_msg = str(e)[:1000]
            self.db.commit()
        self.db.refresh(doc)
        log_operation(self.db, user=operator, action="kb_reindex",
                      resource=f"doc_id={doc.id} {doc.filename}",
                      detail=f"status={doc.status} chunks={doc.chunk_count}", request=request)
        return KbDocInfo.model_validate(doc).model_dump()

    # ---------- 列出文档 chunks（用于前端预览已入库 Chunk） ----------
    def list_chunks(self, doc_id: int, limit: int = 100) -> dict:
        doc = self.db.query(KbDocument).filter(KbDocument.id == doc_id).first()
        if not doc:
            raise BizError("文档不存在", code=40402, http_status=404)
        try:
            from app.rag.vector_store import get_vector_store
            vs = get_vector_store()
            col = vs.lc._collection
            where = {"doc_id": int(doc_id)}
            res = col.get(where=where, include=["documents", "metadatas"])
            ids = res.get("ids", [])
            docs = res.get("documents", [])
            metas = res.get("metadatas", [])
            chunks = []
            for _id, txt, meta in zip(ids, docs, metas):
                idx = (meta or {}).get("chunk_index", 0)
                try:
                    idx = int(idx)
                except Exception:
                    idx = 0
                chunks.append({
                    "id": _id,
                    "index": idx,
                    "content": txt,
                    "char_count": len(txt or ""),
                    "page": (meta or {}).get("page"),
                })
            chunks.sort(key=lambda c: c["index"])
            return {
                "doc_id": doc_id,
                "filename": doc.filename,
                "total": len(chunks),
                "chunks": chunks[:limit],
            }
        except Exception as e:  # noqa
            logger.warning(f"读取 chunks 失败 doc_id={doc_id}: {e}")
            return {"doc_id": doc_id, "filename": doc.filename,
                    "total": 0, "chunks": []}
