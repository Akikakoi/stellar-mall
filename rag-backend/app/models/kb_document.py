"""知识库文档模型（记录上传的文档元数据）。"""
from datetime import datetime
from sqlalchemy import Column, Integer, String, DateTime, BigInteger, Text

from app.core.database import Base


class KbDocument(Base):
    __tablename__ = "kb_documents"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    # 文件名（原始）
    filename = Column(String(255), nullable=False, index=True)
    # 保存到磁盘的相对路径
    file_path = Column(String(512), nullable=False)
    file_ext = Column(String(16), nullable=False)
    file_size = Column(BigInteger, default=0, nullable=False)
    # Chunk 数量
    chunk_count = Column(Integer, default=0, nullable=False)
    # 标签，逗号分隔
    tags = Column(String(512), nullable=True)
    # 状态：uploading / parsing / indexing / ready / error
    status = Column(String(32), default="uploading", nullable=False)
    error_msg = Column(Text, nullable=True)
    # 上传者
    created_by = Column(Integer, nullable=False)
    created_at = Column(DateTime, default=datetime.now, nullable=False, index=True)
    updated_at = Column(DateTime, default=datetime.now, onupdate=datetime.now, nullable=False)
