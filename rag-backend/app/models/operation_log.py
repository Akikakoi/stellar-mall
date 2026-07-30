"""操作日志审计模型。"""
from datetime import datetime
from sqlalchemy import Column, Integer, String, DateTime, Text

from app.core.database import Base


class OperationLog(Base):
    __tablename__ = "operation_logs"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    user_id = Column(Integer, index=True, nullable=True)
    username = Column(String(64), nullable=True)
    action = Column(String(64), index=True, nullable=False)  # 如: login/kb_upload/kb_delete/chat
    resource = Column(String(255), nullable=True)
    detail = Column(Text, nullable=True)
    ip = Column(String(64), nullable=True)
    status = Column(String(16), default="success")  # success / fail
    created_at = Column(DateTime, default=datetime.now, nullable=False, index=True)
