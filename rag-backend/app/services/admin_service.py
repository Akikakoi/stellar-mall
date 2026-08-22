"""管理员服务（最小实现）。"""
from __future__ import annotations
from datetime import datetime, timedelta
from sqlalchemy import func, desc
from sqlalchemy.orm import Session

from app.config import settings
from app.core.utils import escape_sql_like
from app.models import User, Conversation, Message, KbDocument, MessageRole
from app.schemas import UserInfo, SystemSettingsReq


class AdminService:
    def __init__(self, db: Session):
        self.db = db

    # ---------- 仪表盘 ----------
    def dashboard(self) -> dict:
        since = datetime.now() - timedelta(hours=24)
        last_24h = self.db.query(func.count(Message.id)).filter(
            Message.role == MessageRole.USER, Message.created_at >= since
        ).scalar() or 0
        # 热门问题
        top_q = (self.db.query(Message.content, func.count(Message.id).label("cnt"))
                 .filter(Message.role == MessageRole.USER)
                 .group_by(Message.content)
                 .order_by(desc("cnt"))
                 .limit(10).all())
        kb_chunks = sum(d.chunk_count or 0 for d in self.db.query(KbDocument).all())
        return {
            "user_count": self.db.query(func.count(User.id)).scalar() or 0,
            "conversation_count": self.db.query(func.count(Conversation.id)).scalar() or 0,
            "message_count": self.db.query(func.count(Message.id)).scalar() or 0,
            "kb_doc_count": self.db.query(func.count(KbDocument.id)).scalar() or 0,
            "kb_chunk_count": kb_chunks,
            "last_24h_chat_count": int(last_24h),
            "top_questions": [{"q": r[0][:50], "count": int(r[1])} for r in top_q],
        }

    # ---------- 操作日志（写日志保留，读接口已移除：管理端页面已删除）----------

    # ---------- 系统设置 ----------
    @staticmethod
    def get_settings_from_env() -> dict:
        return {
            "llm_model_name": settings.LLM_MODEL_NAME,
            "llm_temperature": settings.LLM_TEMPERATURE,
            "llm_max_tokens": settings.LLM_MAX_TOKENS,
            "retriever_top_k": settings.RETRIEVER_TOP_K,
            "rerank_top_k": settings.RERANK_TOP_K,
            "similarity_threshold": settings.SIMILARITY_THRESHOLD,
            "chunk_size": settings.CHUNK_SIZE,
            "chunk_overlap": settings.CHUNK_OVERLAP,
            "query_rewrite_enabled": settings.QUERY_REWRITE_ENABLED,
            "agent_enabled": settings.AGENT_ENABLED,
        }

    @staticmethod
    def apply_runtime_settings(req: SystemSettingsReq) -> None:
        """修改当前运行时 settings（不写 .env，重启失效但不影响演示）。"""
        for f in req.model_fields:
            v = getattr(req, f)
            if v is not None and hasattr(settings, f):
                setattr(settings, f, v)

    # ---------- 用户列表 ----------
    def list_users(self, page: int = 1, page_size: int = 20, keyword: str = ""):
        q = self.db.query(User)
        if keyword:
            # LIKE 通配符必须先转义，避免用户输入 '%' / '_' 被当作 SQL 通配符
            safe_kw = f"%{escape_sql_like(keyword)}%"
            q = q.filter((User.username.like(safe_kw)) | (User.nickname.like(safe_kw)))
        total = q.count()
        rows = (q.order_by(User.created_at.desc())
                .offset((page - 1) * page_size).limit(page_size).all())
        return {
            "items": [UserInfo.model_validate(r).model_dump() for r in rows],
            "total": total, "page": page, "page_size": page_size,
        }
