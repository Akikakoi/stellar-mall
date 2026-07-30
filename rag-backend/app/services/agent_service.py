"""Agent 智能体服务层。"""
from __future__ import annotations
import asyncio
import time
from typing import AsyncGenerator, Optional

from sqlalchemy.orm import Session

from app.config import settings
from app.core.logger import logger
from app.models import User, MessageRole


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
                for ch in answer:
                    yield {"type": "token", "content": ch}
                    await asyncio.sleep(0.01)

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
