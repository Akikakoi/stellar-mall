"""主图 → 服务层的字段契约：可观测字段必须在 run_agent 出口完整透传。

这类回归是 AST 形状检查抓不到的：节点写进 state 了、服务层也读了，
但中间出口漏字段，整条链路静默失真。
"""
from __future__ import annotations

import asyncio
import os
from types import SimpleNamespace

for _key in (
    "SECRET_KEY",
    "STELLAR_ADMIN_SECRET_KEY",
    "STELLAR_USER_SECRET_KEY",
    "STELLAR_RAG_INTERNAL_SYNC_SECRET",
):
    os.environ.setdefault(_key, "test-placeholder-key-0123456789abcdef")

from app.agent import graph as graph_module  # noqa: E402
from app.services import agent_service as service_module  # noqa: E402

OBSERVABILITY_FIELDS = {
    "intent_source": "cache",
    "intent_cache_hit": True,
    "intent_latency_ms": 12,
    "from_cache": True,
    "cache_source": "faq",
    "react_steps": 3,
}

FULL_STATE = {
    "final_answer": "缓存里的答案",
    "sources": [{"title": "知识库"}],
    "intent": "product_consult",
    "intent_confidence": 0.93,
    "tool_results": {"success": True},
    "missing_params": [],
    "current_tool": "kb_search",
    "required_params": {},
    "uses_react": False,
    "suggestions": ["还有疑问吗"],
    **OBSERVABILITY_FIELDS,
}

FULL_RESULT = dict(FULL_STATE, answer=FULL_STATE["final_answer"])


class _FakeGraph:
    def __init__(self, state: dict):
        self.state = state

    def invoke(self, _state):
        return self.state


def test_run_agent_propagates_observability_fields(monkeypatch):
    """反向破坏：删掉 graph.py 出口任意一个字段，此测试必须变红。"""
    monkeypatch.setattr(graph_module, "agent_graph", _FakeGraph(FULL_STATE))
    result = graph_module.run_agent("质保多久", user_id=1)

    assert result["answer"] == "缓存里的答案"
    for field, expected in OBSERVABILITY_FIELDS.items():
        assert result[field] == expected, f"run_agent 出口丢了 {field}"


def test_answer_sync_records_cache_and_react_metrics(monkeypatch):
    captured = {}
    monkeypatch.setattr(graph_module, "run_agent", lambda **_kwargs: dict(FULL_RESULT))
    monkeypatch.setattr(
        service_module, "record_agent_run",
        lambda **kwargs: captured.update(kwargs),
    )
    service = service_module.AgentChatService(db=None, user=SimpleNamespace(id=7))

    result = service.answer_sync("质保多久")

    assert captured["answer_cache_hit"] is True
    assert captured["cache_source"] == "faq"
    assert captured["react_steps"] == 3
    assert captured["uses_react"] is True
    assert result["answer_cache_hit"] is True
    assert result["cache_source"] == "faq"
    assert result["intent_source"] == "cache"


def test_astream_done_event_carries_metrics(monkeypatch):
    captured = {}
    monkeypatch.setattr(graph_module, "run_agent", lambda **_kwargs: dict(FULL_RESULT))
    monkeypatch.setattr(
        service_module, "record_agent_run",
        lambda **kwargs: captured.update(kwargs),
    )
    service = service_module.AgentChatService(db=None, user=SimpleNamespace(id=7))

    async def collect():
        return [
            event async for event in service.astream_answer(
                "质保多久", conversation_history=[], mall_token="token"
            )
        ]

    events = asyncio.run(collect())
    done = next(e for e in events if e["type"] == "done")

    assert done["answer_cache_hit"] is True
    assert done["cache_source"] == "faq"
    assert done["intent_source"] == "cache"
    assert done["intent_cache_hit"] is True
    assert done["intent_latency_ms"] == 12
    assert done["steps"] == 3
    assert captured["answer_cache_hit"] is True
