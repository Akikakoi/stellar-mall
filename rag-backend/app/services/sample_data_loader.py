"""首次启动：若知识库为空，自动把 sample_data_dir 的样例文档入库。"""
from __future__ import annotations
from pathlib import Path

from app.config import settings
from app.core.database import SessionLocal
from app.core.logger import logger
from app.models import KbDocument, User, UserRole


def load_sample_data_if_empty() -> None:
    """仅当 Chroma 为空 + 样例数据目录有文件时，自动入库（创建一个内置 admin 上传记录）。"""
    from app.rag.vector_store import get_vector_store

    vs = get_vector_store()
    if not vs.collection_empty():
        logger.info("[SampleData] Chroma 已有数据，跳过样例导入")
        return
    sample_dir = Path(settings.BASE_DIR) / settings.SAMPLE_DATA_DIR
    files = [p for p in sample_dir.glob("*") if p.suffix.lower() in settings.ALLOWED_EXT_LIST]
    if not files:
        logger.info("[SampleData] 无样例数据文件")
        return
    db = SessionLocal()
    try:
        admin = db.query(User).filter(User.role == UserRole.ADMIN).first()
        if not admin:
            logger.warning("[SampleData] 无 admin 用户，放弃导入")
            return
        logger.info(f"[SampleData] 检测到 {len(files)} 个样例文档，开始自动导入...")
        for fp in files:
            try:
                # 复制到上传目录（便于后续删除/管理）
                import shutil, uuid
                uid = uuid.uuid4().hex[:10]
                uploads_dir = Path(settings.BASE_DIR) / settings.UPLOAD_DIR
                uploads_dir.mkdir(parents=True, exist_ok=True)
                target = uploads_dir / f"{uid}_{fp.name}"
                shutil.copy2(fp, target)
                doc = KbDocument(
                    filename=fp.name,
                    file_path=str(target.relative_to(settings.BASE_DIR)),
                    file_ext=fp.suffix.lower(),
                    file_size=fp.stat().st_size,
                    tags=_guess_tags(fp.name),
                    status="parsing",
                    created_by=admin.id,
                )
                db.add(doc)
                db.commit()
                db.refresh(doc)
                chunks = vs.add_document(doc)
                doc.status = "ready"
                doc.chunk_count = len(chunks)
                db.commit()
                logger.info(f"[SampleData] 导入完成: {fp.name}  chunks={len(chunks)}")
            except Exception as e:  # noqa
                logger.warning(f"[SampleData] 导入 {fp.name} 失败: {e}")
    finally:
        db.close()


def _guess_tags(filename: str) -> str:
    f = filename.lower()
    tags = []
    for kw in ["手机", "phone", "笔记本", "电脑", "laptop", "家电", "冰箱", "空调", "洗衣机", "tv", "电视", "售后", "保修", "政策", "csv"]:
        if kw in f:
            tags.append(kw)
    return ",".join(tags[:4]) if tags else "样例数据"
