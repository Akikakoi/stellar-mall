"""会话服务（最小实现）。"""
from __future__ import annotations
from datetime import datetime
from typing import List, Optional
from sqlalchemy.orm import Session

from app.core.exceptions import BizError
from app.models import Conversation, Message, User, MessageRole
from app.schemas import (
    ConversationCreateReq, ConversationInfo, ConversationDetail, MessageInfo
)


class ConversationService:
    """个人会话服务。

    语义范围：只用于"当前登录用户"管理自己的会话（/api/conversations/* 与 /api/chat）。
    即使调用者是 admin，也不能通过该服务读写其他用户的会话——管理员要做审计/数据治理，
    必须走独立的 AdminConversationService 或 /api/admin/* 接口，防止个人路由被误用来越权。
    """

    def __init__(self, db: Session, user: User):
        self.db = db
        self.user = user

    def _owns(self, conv: Conversation) -> None:
        """严格归属校验：必须是本人（admin 走本服务也不例外）。"""
        if not conv:
            raise BizError("会话不存在", code=40401, http_status=404)
        if conv.user_id != self.user.id:
            raise BizError("无权访问该会话", code=40302, http_status=403)

    # ---------- CRUD ----------
    def list_my_conversations(self) -> List[dict]:
        rows = (self.db.query(Conversation)
                .filter(Conversation.user_id == self.user.id)
                .order_by(Conversation.pinned.desc(), Conversation.updated_at.desc())
                .all())
        return [ConversationInfo.model_validate(r).model_dump() for r in rows]

    def create(self, req: ConversationCreateReq) -> dict:
        conv = Conversation(
            user_id=self.user.id,
            title=req.title or "新的对话",
        )
        self.db.add(conv)
        self.db.commit()
        self.db.refresh(conv)
        return ConversationInfo.model_validate(conv).model_dump()

    def get_detail(self, conv_id: int) -> dict:
        conv = self.db.query(Conversation).filter(Conversation.id == conv_id).first()
        self._owns(conv)
        return ConversationDetail.model_validate(conv).model_dump()

    def rename(self, conv_id: int, title: str) -> dict:
        conv = self.db.query(Conversation).filter(Conversation.id == conv_id).first()
        self._owns(conv)
        conv.title = title
        conv.updated_at = datetime.now()
        self.db.commit()
        self.db.refresh(conv)
        return ConversationInfo.model_validate(conv).model_dump()

    def delete(self, conv_id: int) -> None:
        conv = self.db.query(Conversation).filter(Conversation.id == conv_id).first()
        self._owns(conv)
        self.db.delete(conv)
        self.db.commit()

    def append_message(self, conv_id: int, role: MessageRole, content: str,
                       sources: Optional[list] = None, tokens_used: int = 0,
                       latency_ms: int = 0) -> Message:
        conv = self.db.query(Conversation).filter(Conversation.id == conv_id).first()
        self._owns(conv)
        msg = Message(
            conversation_id=conv.id,
            user_id=self.user.id,
            role=role,
            content=content,
            sources=sources,
            tokens_used=tokens_used,
            latency_ms=latency_ms,
        )
        self.db.add(msg)
        conv.updated_at = datetime.now()
        # 自动命名会话（首条用户消息作为标题）
        if role == MessageRole.USER and (conv.title == "新的对话" or not conv.title):
            conv.title = content[:30] + ("..." if len(content) > 30 else "")
        self.db.commit()
        self.db.refresh(msg)
        return msg

    def get_history(self, conv_id: int, limit: int = 20) -> List[Message]:
        conv = self.db.query(Conversation).filter(Conversation.id == conv_id).first()
        self._owns(conv)
        q = (self.db.query(Message)
             .filter(Message.conversation_id == conv_id)
             .order_by(Message.created_at.desc())
             .limit(limit))
        return list(reversed(q.all()))

    def set_feedback(self, conv_id: int, msg_id: int, fb: int) -> None:
        msg = self.db.query(Message).filter(Message.id == msg_id, Message.conversation_id == conv_id).first()
        if not msg or msg.user_id != self.user.id:
            raise BizError("消息不存在或无权操作", http_status=404)
        msg.feedback = int(fb)
        self.db.commit()
