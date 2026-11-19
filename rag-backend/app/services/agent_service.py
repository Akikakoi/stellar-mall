"""Agent 智能体服务层。"""
from __future__ import annotations
import asyncio
import time
from typing import AsyncGenerator, Optional

from sqlalchemy.orm import Session

from app.core.logger import logger
from app.models import User
from app.agent.metrics import record_agent_run


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
        from app.agent.nodes import set_agent_event_emitter, reset_agent_event_emitter

        t0 = time.perf_counter()
        try:
            yield {"type": "agent_thought", "content": "正在分析您的问题..."}

            loop = asyncio.get_running_loop()
            event_queue: asyncio.Queue = asyncio.Queue()

            def emit(event: dict) -> None:
                loop.call_soon_threadsafe(event_queue.put_nowait, event)

            emitter_token = set_agent_event_emitter(emit)
            task = asyncio.create_task(asyncio.to_thread(
                run_agent,
                query=query,
                user_id=self.user.id,
                mall_token=mall_token,
                conversation_history=conversation_history or [],
            ))
            emitted_types: set[str] = set()
            answer_streamed = False
            try:
                # 图执行期间边产生事件边下发：工具调用、来源与真实 LLM token 不再等整段完成
                while True:
                    get_task = asyncio.create_task(event_queue.get())
                    done, _ = await asyncio.wait(
                        {get_task, task}, return_when=asyncio.FIRST_COMPLETED
                    )
                    if get_task in done:
                        event = get_task.result()
                        emitted_types.add(event.get("type", ""))
                        if event.get("type") == "token":
                            answer_streamed = True
                        yield event
                        continue
                    get_task.cancel()
                    break

                while not event_queue.empty():
                    event = event_queue.get_nowait()
                    emitted_types.add(event.get("type", ""))
                    if event.get("type") == "token":
                        answer_streamed = True
                    yield event

                result = task.result()
            finally:
                reset_agent_event_emitter(emitter_token)

            intent = result.get("intent", "other")
            intent_confidence = result.get("intent_confidence", 0)
            if "intent" not in emitted_types:
                yield {"type": "intent", "intent": intent, "confidence": intent_confidence}

            # ReAct 已收敛进主图的 react_subgraph：图执行期间事件实时下发，
            # 服务层不再做二次调用，也不再自行拼装 tool/token 事件。
            current_tool = result.get("current_tool")
            tool_results = result.get("tool_results", {})
            if current_tool and tool_results and "tool_call" not in emitted_types:
                yield {"type": "tool_call", "tool": current_tool, "params": result.get("required_params", {})}
            if current_tool and tool_results and "tool_result" not in emitted_types:
                yield {"type": "tool_result", "tool": current_tool, "success": tool_results.get("success", False)}

            sources = result.get("sources", [])
            if sources and "sources" not in emitted_types:
                yield {"type": "sources", "data": sources}

            answer = result.get("answer", "")
            # 已实时推过 token（或走了缓存/闲聊等一次性路径）时不重复发送完整答案
            if answer and not answer_streamed:
                yield {"type": "token", "content": answer}

            missing_params = result.get("missing_params", [])
            if missing_params:
                yield {"type": "missing_params", "data": missing_params}

            react_steps = int(result.get("react_steps") or 0)
            uses_react = react_steps > 0

            # 推荐追问：图中已生成（缓存命中或随答案生成）；ReAct 路径现生成；其余模板兜底
            suggestions = result.get("suggestions") or []
            if not suggestions and uses_react and answer:
                try:
                    from app.agent.suggestions import gen_suggestions_sync
                    suggestions = await asyncio.to_thread(
                        gen_suggestions_sync, query, answer, intent
                    )
                except Exception:  # noqa: BLE001
                    from app.agent.suggestions import rule_suggestions
                    suggestions = rule_suggestions(intent)
            elif not suggestions:
                from app.agent.suggestions import rule_suggestions
                suggestions = rule_suggestions(intent)
            yield {"type": "suggestions", "data": suggestions}

            answer_cache_hit = bool(result.get("from_cache"))
            cache_source = result.get("cache_source") or ""
            latency_ms = int((time.perf_counter() - t0) * 1000)
            record_agent_run(
                intent=intent, uses_react=uses_react, answer_cache_hit=answer_cache_hit,
                latency_ms=latency_ms, react_steps=react_steps, cache_source=cache_source,
            )
            yield {
                "type": "done",
                "tokens": len(answer),
                "intent": intent,
                "tool_used": current_tool,
                "missing_params": missing_params,
                "intent_source": result.get("intent_source"),
                "intent_cache_hit": bool(result.get("intent_cache_hit")),
                "intent_latency_ms": result.get("intent_latency_ms"),
                "answer_cache_hit": answer_cache_hit,
                "cache_source": cache_source,
                "steps": react_steps or 1,
                "latency_ms": latency_ms,
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

        t0 = time.perf_counter()
        result = run_agent(
            query=query,
            user_id=self.user.id,
            mall_token=mall_token,
            conversation_history=conversation_history or [],
        )
        latency = int((time.perf_counter() - t0) * 1000)
        answer = result.get("answer", "")
        react_steps = int(result.get("react_steps") or 0)
        uses_react = react_steps > 0
        answer_cache_hit = bool(result.get("from_cache"))
        cache_source = result.get("cache_source") or ""

        record_agent_run(
            intent=result.get("intent", "other"), uses_react=uses_react,
            answer_cache_hit=answer_cache_hit, latency_ms=latency,
            react_steps=react_steps, cache_source=cache_source,
        )
        return {
            "answer": answer,
            "sources": result.get("sources", []),
            "intent": result.get("intent", "other"),
            "tool_used": result.get("current_tool"),
            "tool_results": result.get("tool_results", {}),
            "missing_params": result.get("missing_params", []),
            "tokens_used": len(answer),
            "latency_ms": latency,
            "uses_react": uses_react,
            "react_steps": react_steps,
            "intent_source": result.get("intent_source"),
            "intent_cache_hit": bool(result.get("intent_cache_hit")),
            "answer_cache_hit": answer_cache_hit,
            "cache_source": cache_source,
        }
