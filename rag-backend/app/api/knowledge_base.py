"""知识库管理路由（仅管理员）。"""
from __future__ import annotations
from pathlib import Path
from urllib.parse import quote
from fastapi import APIRouter, Depends, Request
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session

from app.config import settings
from app.core.database import get_db
from app.core.exceptions import BizError, ok
from app.dependencies import require_admin
from app.models import User

router = APIRouter(prefix="/kb")


# ==================== 工具：中文文件名的 Content-Disposition ====================
def make_content_disposition(original_filename: str, ascii_fallback: str = "download") -> str:
    """返回纯 ASCII 的 Content-Disposition 值（attachment）。

    - RFC 5987 兼容：新浏览器用 filename*=UTF-8''{percent-encoded} 显示正确中文
    - 老浏览器兜底：filename="ascii_name.ext"（只保留 ASCII 可见字符，其余替换为 _）
    - 整个返回值 100% 可被 latin-1 编码，避免 Starlette init_headers 抛 UnicodeEncodeError。
    """
    p = Path(original_filename)
    ext = p.suffix
    stem = p.stem
    # 1. ASCII 兜底文件名：只保留 a-zA-Z0-9 和 -._
    safe_stem_chars = []
    for ch in stem:
        if (ch.isascii() and ch.isalnum()) or ch in '-._':
            safe_stem_chars.append(ch)
        else:
            safe_stem_chars.append('_')
    safe_stem = ''.join(safe_stem_chars).strip('._ ')
    if not safe_stem:
        safe_stem = ascii_fallback
    ascii_name = f'{safe_stem}{ext}'.replace('"', '_')
    # 2. RFC 5987：UTF-8 percent encode 全部非 ASCII
    raw_name = f'{stem}{ext}'
    rfc5987_enc = quote(raw_name, safe='', encoding='utf-8')
    return f'attachment; filename="{ascii_name}"; filename*=UTF-8\'\'{rfc5987_enc}'


@router.get("/documents")
async def list_docs(
    page: int = 1, page_size: int = 20, keyword: str = "",
    status: str | None = None,
    current_user: User = Depends(require_admin),
    db: Session = Depends(get_db),
):
    from app.services.kb_service import KbService
    return ok(KbService(db).list_docs(page=page, page_size=page_size,
                                     keyword=keyword, status=status))


@router.post("/documents")
async def upload_and_index(
    request: Request,
    current_user: User = Depends(require_admin),
    db: Session = Depends(get_db),
):
    """多部分 form 上传：file=文件, tags=标签逗号分隔, chunk_size=可选, chunk_overlap=可选。

    返回体语义：
      - success=true (HTTP 200 + code=0) 表示上传请求本身没有中断
      - data.indexed=true   表示向量化成功，data.status == ready
      - data.indexed=false  表示向量化失败，data.error_msg 包含具体原因，
                            前端可据此展示警告并提供「重新索引」操作
    """
    from app.services.kb_service import KbService
    from app.core.logger import logger
    form = await request.form()
    file = form.get("file")
    tags = form.get("tags") or ""
    chunk_size = form.get("chunk_size")
    chunk_overlap = form.get("chunk_overlap")
    if chunk_size is not None:
        chunk_size = int(chunk_size)
    if chunk_overlap is not None:
        chunk_overlap = int(chunk_overlap)
    logger.info(f"[KB upload] form keys={list(form.keys())} file_type={type(file).__name__ if file else 'None'} content_type={request.headers.get('content-type','')[:60] if request.headers else 'no-header'}")
    doc = await KbService(db).upload_and_index(
        file=file, tags=str(tags), operator=current_user, request=request,
        chunk_size=chunk_size, chunk_overlap=chunk_overlap)
    return ok(doc)


@router.get("/documents/{doc_id}")
async def get_doc(doc_id: int, current_user: User = Depends(require_admin),
                  db: Session = Depends(get_db)):
    from app.services.kb_service import KbService
    return ok(KbService(db).get_doc(doc_id))


@router.put("/documents/{doc_id}")
async def update_doc(
    doc_id: int, body: dict,
    current_user: User = Depends(require_admin), db: Session = Depends(get_db),
):
    from app.services.kb_service import KbService
    from app.schemas import KbDocUpdateReq
    req = KbDocUpdateReq(**(body or {}))
    return ok(KbService(db).update_doc(doc_id, req))


@router.delete("/documents/{doc_id}")
async def delete_doc(
    doc_id: int, request: Request,
    current_user: User = Depends(require_admin), db: Session = Depends(get_db),
):
    from app.services.kb_service import KbService
    KbService(db).delete_doc(doc_id, operator=current_user, request=request)
    return ok(message="删除成功")


@router.get("/stats")
async def kb_stats(current_user: User = Depends(require_admin),
                   db: Session = Depends(get_db)):
    from app.services.kb_service import KbService
    return ok(KbService(db).stats())


# --------------------- 新增：文件切分预览（不上传，不落库） ---------------------
@router.post("/preview")
async def preview_document(
    request: Request,
    current_user: User = Depends(require_admin),
    db: Session = Depends(get_db),
):
    """form: file, chunk_size(可选), chunk_overlap(可选), limit(可选)"""
    from app.services.kb_service import KbService
    from app.core.logger import logger
    form = await request.form()
    file = form.get("file")
    logger.info(f"[KB preview] form keys={list(form.keys())} file_type={type(file).__name__ if file else 'None'} content_type={request.headers.get('content-type','')[:60] if request.headers else 'no-header'}")
    try:
        chunk_size = int(form.get("chunk_size")) if form.get("chunk_size") else None
    except Exception:
        chunk_size = None
    try:
        chunk_overlap = int(form.get("chunk_overlap")) if form.get("chunk_overlap") else None
    except Exception:
        chunk_overlap = None
    try:
        limit = int(form.get("limit")) if form.get("limit") else 50
    except Exception:
        limit = 50
    data = await KbService(db).preview_file(
        file=file, chunk_size=chunk_size, chunk_overlap=chunk_overlap,
        limit=max(1, min(limit, 500)))
    return ok(data)


# --------------------- 新增：重新索引 ---------------------
@router.post("/documents/{doc_id}/reindex")
async def reindex_doc(
    doc_id: int, request: Request,
    current_user: User = Depends(require_admin),
    db: Session = Depends(get_db),
):
    from app.services.kb_service import KbService
    return ok(KbService(db).reindex(doc_id, operator=current_user, request=request))


# --------------------- 新增：文件下载 ---------------------
@router.get("/documents/{doc_id}/download")
async def download_doc(
    doc_id: int,
    current_user: User = Depends(require_admin),
    db: Session = Depends(get_db),
):
    from app.services.kb_service import KbService
    doc_info = KbService(db).get_doc(doc_id)
    # 注意：doc_info 来自 KbDocInfo，需要原始 file_path 字段 —— 我们直接再查一次 DB
    from app.models import KbDocument
    doc = db.query(KbDocument).filter(KbDocument.id == doc_id).first()
    if not doc:
        raise BizError("文档不存在", http_status=404, code=40402)
    fp = Path(settings.BASE_DIR) / doc.file_path
    if not fp.exists():
        raise BizError("物理文件不存在，可能已被清理", http_status=404, code=40403)
    filename = doc.filename
    ext = doc.file_ext.lower()
    media_map = {
        ".pdf": "application/pdf",
        ".docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        ".md": "text/markdown; charset=utf-8",
        ".txt": "text/plain; charset=utf-8",
        ".csv": "text/csv; charset=utf-8-sig",
    }
    media = media_map.get(ext, "application/octet-stream")
    # 注意：
    #   1) 自定义的 Content-Disposition 必须是纯 ASCII（RFC 5987 percent-encode 中文）
    #   2) 不要传 filename= 参数给 FileResponse，否则 Starlette 会再生成一份中文 header，
    #      与我们自定义的 headers 合并时触发 latin-1 UnicodeEncodeError。
    headers = {"Content-Disposition": make_content_disposition(filename)}
    return FileResponse(path=str(fp), media_type=media, headers=headers)


# --------------------- 新增：文档 Chunk 列表 ---------------------
@router.get("/documents/{doc_id}/chunks")
async def list_doc_chunks(
    doc_id: int, limit: int = 100,
    current_user: User = Depends(require_admin),
    db: Session = Depends(get_db),
):
    from app.services.kb_service import KbService
    return ok(KbService(db).list_chunks(doc_id, limit=max(1, min(limit, 500))))
