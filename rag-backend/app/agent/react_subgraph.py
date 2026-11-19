"""ReAct 子图：把手写 ReAct 循环收进 LangGraph，统一执行入口与事件链路。

主图在 generate_answer 后按 uses_react 条件路由到这里；子图执行完成后直接 END。
事件通过 nodes._emit_agent_event 发送，服务层不再做二次 ReAct 调用与事件拼装。
"""
from __future__ import annotations

import asyncio

from langgraph.graph import StateGraph, END

from app.agent.nodes import _emit_agent_event, _get_last_user_query
from app.agent.state import AgentState
from app.config import settings
from app.core.logger import logger


def react_agent_node(state: AgentState) -> dict:
    """在子图内运行 ReAct，并把流式事件转发给服务层。"""
    from app.agent.react_agent import astream_react, run_react_agent

    query = _get_last_user_query(state)
    user_id = state.get("user_id")
    mall_token = state.get("mall_token")
    history = state.get("conversation_history") or []

    if not settings.AGENT_ENABLED:
        fallback = "智能体模式已关闭，暂时无法处理这类复杂问题。"
        _emit_agent_event({"type": "token", "content": fallback})
        return {
            "final_answer": fallback,
            "sources": [],
            "stream_chunks": [fallback],
            "react_steps": 0,
            "uses_react": False,
        }

    events: list[dict] = []

    async def _stream() -> None:
        async for ev in astream_react(
            query=query,
            user_id=user_id,
            mall_token=mall_token,
            conversation_history=history,
        ):
            events.append(ev)
            _emit_agent_event(ev)

    try:
        # run_agent 由服务层放到工作线程执行，通常没有运行中的事件循环
        asyncio.run(_stream())
        final_answer = "".join(
            ev.get("content", "") for ev in events if ev.get("type") == "token"
        )
        sources: list = []
        steps = 1
        for ev in events:
            if ev.get("type") == "sources":
                sources = ev.get("data") or []
            if ev.get("type") == "done":
                steps = len(ev.get("tool_trace", [])) + 1
        return {
            "final_answer": final_answer,
            "sources": sources,
            "stream_chunks": [
                ev.get("content", "") for ev in events if ev.get("type") == "token"
            ],
            "react_steps": steps,
            "uses_react": False,
        }
    except RuntimeError as e:
        # 已被外层事件循环占用时退回同步 ReAct，仍把结果作为单一 token 发出
        logger.warning(f"[ReactSubgraph] 无法异步流式执行，退回同步 ReAct: {e}")
        rr = run_react_agent(
            query=query,
            user_id=user_id,
            mall_token=mall_token,
            conversation_history=history,
        )
        answer = rr.get("answer", "")
        _emit_agent_event({"type": "agent_thought", "content": "正在分析复杂问题..."})
        _emit_agent_event({"type": "token", "content": answer})
        return {
            "final_answer": answer,
            "sources": rr.get("sources", []),
            "stream_chunks": [answer] if answer else [],
            "react_steps": int(rr.get("steps", 1)),
            "uses_react": False,
        }


def build_react_subgraph():
    workflow = StateGraph(AgentState)
    workflow.add_node("react", react_agent_node)
    workflow.set_entry_point("react")
    workflow.add_edge("react", END)
    return workflow.compile()


react_subgraph = build_react_subgraph()


__all__ = ["react_agent_node", "build_react_subgraph", "react_subgraph"]
