"""RAG Chat 服务：封装非流式/流式 RAG 问答、会话管理与查询缓存。"""
from __future__ import annotations
import time
from typing import Any, AsyncGenerator

from fastapi import Request
from sqlalchemy.orm import Session

from app.core.logger import logger
from app.models import User, MessageRole
from app.rag.chains import astream_answer_with_sources
from app.rag.retriever import get_query_cache
from app.schemas import ChatReq


class RagChatService:
    def __init__(self, db: Session, user: User):
        self.db = db
        self.user = user

    async def answer_to_response(self, req: ChatReq, request: Request | None = None) -> dict:
        """非流式 RAG 问答：消费流式事件后返回完整 JSON。"""
        answer_parts: list[str] = []
        sources_data: list[Any] = []
        tokens = 0
        conversation_id: int | None = None
        message_id: int | None = None
        latency_ms = 0

        async for ev in self.astream_answer(req, request):
            ev_type = ev.get("type")
            if ev_type == "token":
                answer_parts.append(ev.get("content", ""))
            elif ev_type == "sources":
                sources_data = ev.get("data", [])
            elif ev_type == "done":
                tokens = ev.get("tokens", 0)
                conversation_id = ev.get("conversation_id")
                message_id = ev.get("message_id")
                latency_ms = ev.get("latency_ms", 0)

        return {
            "conversation_id": conversation_id,
            "message_id": message_id,
            "answer": "".join(answer_parts),
            "sources": sources_data,
            "tokens_used": tokens,
            "latency_ms": latency_ms,
        }

    async def astream_answer(
        self, req: ChatReq, request: Request | None = None
    ) -> AsyncGenerator[dict, None]:
        """流式 RAG 问答：yield sources/token/done 事件，并负责会话写入与缓存。"""
        from app.services.conversation_service import ConversationService

        conv_svc = ConversationService(self.db, self.user)

        # 1) 创建或确认会话
        conv_id = req.conversation_id
        if not conv_id:
            created = conv_svc.create(type("C", (), {"title": None})())
            conv_id = created["id"]

        # 2) 写入用户消息
        user_msg = conv_svc.append_message(conv_id, MessageRole.USER, req.query)
        t0 = time.time()

        # 3) 构建历史（去掉最后一条用户消息避免重复）
        history_tuples = [(m.role.value, m.content) for m in conv_svc.get_history(conv_id, limit=12)]
        if history_tuples and history_tuples[-1][0] == "user":
            history_tuples = history_tuples[:-1]

        cache = get_query_cache()
        cached = None
        try:
            cached = cache.get(req.query)
        except Exception as e:  # noqa
            logger.warning(f"查询缓存读取异常: {e}")

        answer_parts: list[str] = []
        sources_data: list[Any] = []
        tokens = 0

        if cached:
            # 命中缓存：直接流式输出缓存结果
            answer, srcs = cached
            sources_data = srcs
            yield {"type": "sources", "data": sources_data}
            for ch in answer:
                answer_parts.append(ch)
                yield {"type": "token", "content": ch}
            yield {
                "type": "done",
                "tokens": 0,
                "conversation_id": conv_id,
                "message_id": user_msg.id,
                "latency_ms": int((time.time() - t0) * 1000),
            }
        else:
            try:
                async for ev in astream_answer_with_sources(
                    query=req.query,
                    history=history_tuples,
                    top_k=req.top_k,
                    tags_filter=req.tags_filter,
                    use_rewrite=req.use_rewrite,
                ):
                    ev_type = ev.get("type")
                    if ev_type == "token":
                        answer_parts.append(ev.get("content", ""))
                        yield ev
                    elif ev_type == "sources":
                        sources_data = ev.get("data", [])
                        yield ev
                    elif ev_type == "done":
                        tokens = ev.get("tokens", 0)
            except Exception as e:  # noqa
                logger.exception(f"RAG 生成失败: {e}")
                fallback = "（大模型服务暂时不可用，请稍后重试。如持续失败请联系管理员。）"
                answer_parts.append(fallback)
                yield {"type": "token", "content": fallback}

            latency = int((time.time() - t0) * 1000)
            full_ans = "".join(answer_parts)

            # 4) 写入 assistant 消息
            try:
                conv_svc.append_message(
                    conv_id, MessageRole.ASSISTANT, full_ans,
                    sources=sources_data, tokens_used=tokens, latency_ms=latency,
                )
            except Exception as e:  # noqa
                logger.warning(f"写入消息历史失败: {e}")

            # 5) 写入缓存（仅非兜底回答且有来源时）
            if not cached and sources_data and full_ans and not full_ans.startswith("（大模型服务暂时不可用"):
                try:
                    cache.put(req.query, full_ans, sources_data)
                except Exception as e:  # noqa
                    logger.warning(f"写查询缓存失败: {e}")

            yield {
                "type": "done",
                "tokens": tokens,
                "conversation_id": conv_id,
                "message_id": user_msg.id,
                "latency_ms": latency,
            }
