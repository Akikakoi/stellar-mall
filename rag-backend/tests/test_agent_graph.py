"""主图接入 ReAct 子图的回归测试。"""
from __future__ import annotations

import os

for _key in (
    "SECRET_KEY",
    "STELLAR_ADMIN_SECRET_KEY",
    "STELLAR_USER_SECRET_KEY",
    "STELLAR_RAG_INTERNAL_SYNC_SECRET",
):
    os.environ.setdefault(_key, "test-placeholder-key-0123456789abcdef")

from langchain_core.messages import HumanMessage  # noqa: E402

from app.agent import react_agent  # noqa: E402
from app.agent.graph import agent_graph  # noqa: E402
from app.agent.nodes import route_after_answer  # noqa: E402
from app.agent.react_subgraph import react_agent_node  # noqa: E402


def test_graph_exposes_react_subgraph_node():
    nodes = set(agent_graph.get_graph().nodes)
    assert "react_agent" in nodes
    assert "generate_answer" in nodes


def test_route_after_answer():
    assert route_after_answer({"uses_react": True}) == "react_agent"
    assert route_after_answer({"uses_react": False}) == "end"
    assert route_after_answer({}) == "end"


def test_react_subgraph_node_runs_inside_graph(monkeypatch):
    async def fake_astream(**_kwargs):
        yield {"type": "token", "content": "复杂问题的回答"}
        yield {"type": "sources", "data": [{"title": "kb"}]}
        yield {"type": "done", "tokens": 7, "tool_trace": []}

    monkeypatch.setattr(react_agent, "astream_react", fake_astream)
    result = react_agent_node({
        "messages": [HumanMessage(content="既查订单又问售后")],
        "user_id": 1,
        "mall_token": "token",
        "conversation_history": [],
    })
    assert result["final_answer"] == "复杂问题的回答"
    assert result["sources"] == [{"title": "kb"}]
    assert result["react_steps"] == 1
    assert result["uses_react"] is False
