"""会话管理路由（先占位空实现，下个模块填充）。"""
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.exceptions import ok
from app.dependencies import get_current_user
from app.models import User

router = APIRouter(prefix="/conversations")


@router.get("")
async def list_conversations(current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    from app.services.conversation_service import ConversationService
    return ok(ConversationService(db, current_user).list_my_conversations())


@router.post("")
async def create_conversation(
    body: dict,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    from app.services.conversation_service import ConversationService
    from app.schemas import ConversationCreateReq
    req = ConversationCreateReq(**(body or {}))
    conv = ConversationService(db, current_user).create(req)
    return ok(conv)


@router.get("/{conv_id}")
async def get_conversation(conv_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    from app.services.conversation_service import ConversationService
    return ok(ConversationService(db, current_user).get_detail(conv_id))


@router.put("/{conv_id}")
async def rename_conversation(
    conv_id: int, body: dict,
    current_user: User = Depends(get_current_user), db: Session = Depends(get_db),
):
    from app.services.conversation_service import ConversationService
    from app.schemas import ConversationRenameReq
    req = ConversationRenameReq(**(body or {}))
    return ok(ConversationService(db, current_user).rename(conv_id, req.title))


@router.delete("/{conv_id}")
async def delete_conversation(conv_id: int, current_user: User = Depends(get_current_user), db: Session = Depends(get_db)):
    from app.services.conversation_service import ConversationService
    ConversationService(db, current_user).delete(conv_id)
    return ok(message="删除成功")


@router.post("/{conv_id}/messages/{msg_id}/feedback")
async def feedback(
    conv_id: int, msg_id: int, body: dict,
    current_user: User = Depends(get_current_user), db: Session = Depends(get_db),
):
    from app.services.conversation_service import ConversationService
    fb = int(body.get("feedback", 0))
    ConversationService(db, current_user).set_feedback(conv_id, msg_id, fb)
    return ok()
