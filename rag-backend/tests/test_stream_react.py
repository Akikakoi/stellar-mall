"""ReAct 流式事件顺序：工具调用轮的文字不得作为最终答案 token 下发。"""
from __future__ import annotations

import asyncio
import os

for _key in (
    "SECRET_KEY",
    "STELLAR_ADMIN_SECRET_KEY",
    "STELLAR_USER_SECRET_KEY",
    "STELLAR_RAG_INTERNAL_SYNC_SECRET",
):
    os.environ.setdefault(_key, "test-placeholder-key-0123456789abcdef")

from langchain_core.messages import AIMessageChunk  # noqa: E402

from app.agent import react_agent  # noqa: E402


class _FakeBound:
    def __init__(self, rounds):
        self.rounds = list(rounds)
        self.cursor = 0

    async def astream(self, _messages):
        round_chunks = self.rounds[self.cursor]
        self.cursor += 1
        for chunk in round_chunks:
            yield chunk


class _FakeLLM:
    def __init__(self, rounds):
        self.rounds = rounds

    def bind_tools(self, _tools):
        return _FakeBound(self.rounds)


def test_tool_call_round_text_is_thought_not_token():
    thought = AIMessageChunk(
        content="我先查一下订单",
        tool_call_chunks=[{
            "name": "unknown_tool", "args": "{}", "id": "call-1", "index": 0,
        }],
    )
    final = AIMessageChunk(content="最终答案")
    llm = _FakeLLM([[thought], [final]])

    async def collect():
        return [
            ev async for ev in react_agent.astream_react(
                "我的订单", user_id=1, llm=llm, max_steps=3,
            )
        ]

    events = asyncio.run(collect())
    tokens = [ev.get("content", "") for ev in events if ev.get("type") == "token"]
    thoughts = [ev.get("content", "") for ev in events if ev.get("type") == "agent_thought"]

    assert "".join(tokens) == "最终答案"
    assert not any("我先查一下" in t for t in tokens)
    assert any("我先查一下" in t for t in thoughts)
    assert any(ev.get("type") == "tool_call" for ev in events)
