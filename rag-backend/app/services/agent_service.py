"""Agent 智能体服务层。"""
from __future__ import annotations
import asyncio
import time
from typing import AsyncGenerator, Optional

from sqlalchemy.orm import Session

from app.config import settings
from app.core.logger import logger
from app.models import User, MessageRole


def _pseudo_stream_chunks(text: str, chunk_size: int = 24, interval: float = 0.005):
    """伪流式分块：按标点/空白优先、固定长度兜底切成小块逐块 yield。
    相较逐字 sleep(0.01)，更新次数更少、间隔更短，显著降低纯表现层延迟。
    """
    punct = "。！？，、；,.!?;:：\n\t "
    buf = ""
    for ch in text:
        buf += ch
        if ch in punct or len(buf) >= chunk_size:
            yield buf
            buf = ""
    if buf:
        yield buf


class AgentChatService:
    def __init__(self, db: Session, user: User):
        self.db = db
        self.user = user

    async def astream_answer(
        self,
        query: str,
        conversation_id: Optional[int] = None,
        conversation_history: list = None,
        mall_token: Optional[str] = None,
    ) -> AsyncGenerator[dict, None]:
        """流式调用 Agent，按事件 yield。

        Yields:
            - {"type": "agent_thought", "content": "..."}  智能体思考过程
            - {"type": "tool_call", "tool": "tool_name", "params": {...}}  工具调用
            - {"type": "tool_result", "tool": "tool_name", "success": bool}  工具结果
            - {"type": "token", "content": "..."}  流式回答 token
            - {"type": "sources", "data": [...]}  引用来源
            - {"type": "intent", "intent": "...", "confidence": 0.9}  意图识别结果
            - {"type": "done", "tokens": N}  完成
        """
        from app.agent.graph import run_agent

        try:
            yield {"type": "agent_thought", "content": "正在分析您的问题..."}

            result = await asyncio.to_thread(
                run_agent,
                query=query,
                user_id=self.user.id,
                mall_token=mall_token,
                conversation_history=conversation_history or [],
            )

            intent = result.get("intent", "other")
            intent_confidence = result.get("intent_confidence", 0)
            yield {"type": "intent", "intent": intent, "confidence": intent_confidence}

            # other 场景：图内仅打标记，走 ReAct 真流式
            if result.get("uses_react"):
                try:
                    from app.agent.react_agent import astream_react

                    async for ev in astream_react(
                        query=query,
                        user_id=self.user.id,
                        mall_token=mall_token,
                        conversation_history=conversation_history or [],
                    ):
                        yield ev
                except Exception as e:  # noqa: BLE001
                    logger.exception(f"ReAct 流式失败: {e}")
                    fallback = "（智能体服务暂时不可用，请稍后重试。）"
                    yield {"type": "token", "content": fallback}
                    yield {"type": "done", "tokens": len(fallback), "error": str(e)}
                return

            current_tool = result.get("current_tool")
            tool_results = result.get("tool_results", {})
            if current_tool and tool_results:
                yield {"type": "tool_call", "tool": current_tool, "params": result.get("required_params", {})}
                yield {"type": "tool_result", "tool": current_tool, "success": tool_results.get("success", False)}

            sources = result.get("sources", [])
            if sources:
                yield {"type": "sources", "data": sources}

            answer = result.get("answer", "")
            if answer:
                for chunk in _pseudo_stream_chunks(answer):
                    yield {"type": "token", "content": chunk}
                    await asyncio.sleep(0.005)

            missing_params = result.get("missing_params", [])
            if missing_params:
                yield {"type": "missing_params", "data": missing_params}

            yield {
                "type": "done",
                "tokens": len(answer),
                "intent": intent,
                "tool_used": current_tool,
                "missing_params": missing_params,
            }

        except Exception as e:
            logger.exception(f"Agent 调用失败: {e}")
            fallback = "（智能体服务暂时不可用，请稍后重试。）"
            yield {"type": "token", "content": fallback}
            yield {"type": "done", "tokens": len(fallback), "error": str(e)}

    def answer_sync(
        self,
        query: str,
        conversation_id: Optional[int] = None,
        conversation_history: list = None,
        mall_token: Optional[str] = None,
    ) -> dict:
        """同步调用 Agent。"""
        from app.agent.graph import run_agent

        t0 = time.time()
        result = run_agent(
            query=query,
            user_id=self.user.id,
            mall_token=mall_token,
            conversation_history=conversation_history or [],
        )
        latency = int((time.time() - t0) * 1000)

        # other 场景：图内仅打标记，交给 ReAct agent 同步执行
        if result.get("uses_react"):
            from app.agent.react_agent import run_react_agent

            rr = run_react_agent(
                query=query,
                user_id=self.user.id,
                mall_token=mall_token,
                conversation_history=conversation_history or [],
            )
            return {
                "answer": rr.get("answer", ""),
                "sources": rr.get("sources", []),
                "intent": result.get("intent", "other"),
                "tool_used": None,
                "tool_results": {},
                "missing_params": [],
                "tokens_used": len(rr.get("answer", "")),
                "latency_ms": latency,
                "react_tool_trace": rr.get("tool_trace", []),
            }

        return {
            "answer": result.get("answer", ""),
            "sources": result.get("sources", []),
            "intent": result.get("intent", "other"),
            "tool_used": result.get("current_tool"),
            "tool_results": result.get("tool_results", {}),
            "missing_params": result.get("missing_params", []),
            "tokens_used": len(result.get("answer", "")),
            "latency_ms": latency,
        }
