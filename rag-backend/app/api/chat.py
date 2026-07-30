"""问答路由：统一走 Agent 智能体模式，AI 自动判断是否调用工具。"""
from __future__ import annotations
import json
import time

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session

from app.config import settings
from app.core.database import get_db
from app.core.exceptions import ok
from app.core.logger import logger
from app.core.rate_limiter import rate_limit
from app.dependencies import get_current_user
from app.models import User, MessageRole
from app.schemas import ChatReq

router = APIRouter(prefix="/chat")


def _format_sse(event: str, data: dict | list | str | None) -> str:
    """将事件数据格式化为 SSE 文本块。"""
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


@router.post("")
@rate_limit(max_calls=settings.RATE_LIMIT_PER_MINUTE_CHAT)
async def chat(
    req: ChatReq, request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    # 统一使用 Agent 模式：AI 自动判断是纯知识库检索还是调用业务工具
    from app.services.conversation_service import ConversationService
    from app.services.agent_service import AgentChatService

    # 从 header 中提取 mall token
    mall_token = None
    auth_header = request.headers.get("authorization", "")
    if auth_header.startswith("Bearer "):
        mall_token = auth_header[7:]
    if not mall_token:
        mall_token = request.headers.get("stellar-token") or request.headers.get("authentication")

    conv_svc = ConversationService(db, current_user)

    # 若无 conv 则新建
    conv_id = req.conversation_id
    if not conv_id:
        created = conv_svc.create(type("C", (), {"title": None})())
        conv_id = created["id"]

    # 写用户消息
    user_msg = conv_svc.append_message(conv_id, MessageRole.USER, req.query)
    t0 = time.time()

    # 构建历史（去掉最后一条用户消息避免重复）
    history_tuples = [(m.role.value, m.content) for m in conv_svc.get_history(conv_id, limit=12)]
    if history_tuples and history_tuples[-1][0] == "user":
        history_tuples = history_tuples[:-1]

    agent_svc = AgentChatService(db, current_user)

    async def event_generator():
        answer_parts = []
        sources_data = []
        tokens = 0
        intent = "other"
        extra_meta = {}

        try:
            async for ev in agent_svc.astream_answer(
                query=req.query,
                conversation_id=conv_id,
                conversation_history=history_tuples,
                mall_token=mall_token,
            ):
                ev_type = ev["type"]
                if ev_type == "token":
                    c = ev.get("content", "")
                    answer_parts.append(c)
                    yield _format_sse("token", {"content": c})
                elif ev_type == "sources":
                    sources_data = ev.get("data", [])
                    yield _format_sse("sources", sources_data)
                elif ev_type == "intent":
                    intent = ev.get("intent", "other")
                    yield _format_sse("intent", {"intent": intent, "confidence": ev.get("confidence", 0)})
                elif ev_type == "tool_call":
                    yield _format_sse("tool_call", {"tool": ev.get("tool"), "params": ev.get("params", {})})
                elif ev_type == "tool_result":
                    yield _format_sse("tool_result", {"tool": ev.get("tool"), "success": ev.get("success")})
                elif ev_type == "agent_thought":
                    yield _format_sse("agent_thought", {"content": ev.get("content", "")})
                elif ev_type == "missing_params":
                    yield _format_sse("missing_params", {"params": ev.get("data", [])})
                elif ev_type == "done":
                    tokens = ev.get("tokens", 0)
                    extra_meta = {
                        "intent": ev.get("intent", intent),
                        "tool_used": ev.get("tool_used"),
                        "missing_params": ev.get("missing_params", []),
                    }
        except Exception as e:  # noqa
            logger.exception(f"Agent 生成失败: {e}")
            fallback = "（智能体服务暂时不可用，请稍后重试。如持续失败请联系管理员。）"
            answer_parts.append(fallback)
            yield _format_sse("token", {"content": fallback})

        latency = int((time.time() - t0) * 1000)
        full_ans = "".join(answer_parts)
        try:
            conv_svc.append_message(
                conv_id, MessageRole.ASSISTANT, full_ans,
                sources=sources_data, tokens_used=tokens, latency_ms=latency,
            )
        except Exception as e:  # noqa
            logger.warning(f"写入消息历史失败: {e}")

        yield _format_sse("done", {
            "tokens": tokens,
            "conversation_id": conv_id,
            "message_id": user_msg.id,
            "latency_ms": latency,
            **extra_meta,
        })

    if not req.stream:
        answer_parts: list[str] = []
        sources_data: list = []
        tokens = 0
        extra_meta: dict = {}
        async for chunk in event_generator():
            for block in chunk.split("\n\n"):
                if not block:
                    continue
                ev = None
                data = None
                for line in block.split("\n"):
                    if not line:
                        continue
                    if line.startswith("event:"):
                        ev = line[6:].strip()
                    elif line.startswith("data:"):
                        try:
                            data = json.loads(line[5:].strip())
                        except Exception:
                            data = line[5:].strip()
                if ev == "sources":
                    sources_data = data if isinstance(data, list) else []
                elif ev == "token":
                    if isinstance(data, dict) and "content" in data:
                        answer_parts.append(data["content"])
                elif ev == "done":
                    if isinstance(data, dict):
                        tokens = data.get("tokens", 0)
                        extra_meta = {k: data[k] for k in (
                            "conversation_id", "message_id", "latency_ms",
                            "intent", "tool_used", "missing_params",
                        ) if k in data}
        full_ans = "".join(answer_parts)
        return ok({
            "answer": full_ans,
            "sources": sources_data,
            "tokens_used": tokens,
            "mode": "agent",
            **extra_meta,
        })

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
