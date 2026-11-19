"""核心 LangGraph 节点的真正单测（非 AST），覆盖确认/工具执行/缓存记账。"""
from __future__ import annotations

import json
import os

for _key in (
    "SECRET_KEY",
    "STELLAR_ADMIN_SECRET_KEY",
    "STELLAR_USER_SECRET_KEY",
    "STELLAR_RAG_INTERNAL_SYNC_SECRET",
):
    os.environ.setdefault(_key, "test-placeholder-key-0123456789abcdef")

from langchain_core.messages import HumanMessage  # noqa: E402

from app.agent import nodes  # noqa: E402


def _state(intent: str, query: str, **extra) -> dict:
    return {
        "messages": [HumanMessage(content=query)],
        "intent": intent,
        "user_id": 1,
        "mall_token": "t",
        "required_params": {},
        **extra,
    }


def test_check_params_high_risk_write_requires_confirmation():
    state = _state("cancel_order", "取消订单")
    result = nodes.check_params_node(state)
    # 缺 order_id，先追问参数；不应执行
    assert "order_id" in result["missing_params"]
    assert result["current_tool"] == "cancel_order"

    # 参数齐全但无确认词 → 等待确认，不执行
    state2 = _state("cancel_order", "订单 87", required_params={"order_id": "87"})
    result2 = nodes.check_params_node(state2)
    assert result2.get("confirmation_required") is True
    assert result2["pending_write"]["intent"] == "cancel_order"

    # 带确认词 → 放行执行
    state3 = _state("cancel_order", "确认取消订单 87", required_params={"order_id": "87"})
    result3 = nodes.check_params_node(state3)
    assert not result3.get("confirmation_required")


def test_tool_execution_dispatches_and_wraps_result(monkeypatch):
    called = {}

    class FakeTool:
        def invoke(self, args):
            called.update(args)
            return {"success": True, "data": [{"id": 1, "name": "X100"}]}

    monkeypatch.setattr(nodes, "query_order_tool", FakeTool())
    state = _state("order_query", "查订单", current_tool="query_order",
                   required_params={"order_id": "87"})
    events = []
    token = nodes.set_agent_event_emitter(events.append)
    try:
        result = nodes.tool_execution_node(state)
    finally:
        nodes.reset_agent_event_emitter(token)

    assert called["order_id"] == "87"
    assert result["tool_results"]["success"] is True
    assert any(e.get("type") == "tool_call" for e in events)
    assert any(e.get("type") == "tool_result" and e["success"] for e in events)


def test_intent_confidence_from_cache_prefers_json_answer():
    cached = {
        "answer": json.dumps({"intent": "order_query", "confidence": 0.87}),
        "tokens_used": 0,
    }
    assert nodes._intent_confidence_from_cache(cached) == 0.87
    # 兼容旧条目：置信度曾编码进 tokens_used
    assert nodes._intent_confidence_from_cache({"answer": "", "tokens_used": 85}) == 0.85
    assert nodes._intent_confidence_from_cache({}) == 0.9


def test_intent_cache_hit_uses_real_confidence(monkeypatch):
    cached = {
        "answer": json.dumps({"intent": "product_consult", "confidence": 0.78}),
        "intent": "product_consult",
        "tokens_used": 0,
    }
    monkeypatch.setattr(nodes, "get_cache_sync", lambda **_kwargs: cached)
    result = nodes.intent_classification_node(
        _state("product_consult", "这款质保政策是怎样的")
    )
    assert result["intent_confidence"] == 0.78


def test_intent_cache_write_does_not_encode_confidence_as_tokens(monkeypatch):
    captured = {}

    class FakeLLM:
        def invoke(self, _messages):
            class Resp:
                content = json.dumps({"intent": "product_consult", "confidence": 0.66})

            return Resp()

    monkeypatch.setattr(nodes, "get_cache_sync", lambda **_kwargs: None)
    monkeypatch.setattr(nodes, "get_langchain_chat", lambda: FakeLLM())
    monkeypatch.setattr(
        nodes, "put_cache_sync",
        lambda **kwargs: captured.update(kwargs),
    )

    result = nodes.intent_classification_node(
        _state("product_consult", "这款质保政策是怎样的")
    )

    assert result["intent_confidence"] == 0.66
    assert captured["tokens_used"] == 0
    assert json.loads(captured["answer"])["confidence"] == 0.66


def test_generate_answer_faq_cache_hit_marked(monkeypatch):
    class FakeQueryCache:
        def get(self, query):
            return ("缓存答案", [{"title": "来源"}])

    # get_query_cache 内部用的是模块级单例，直接打桩 get_query_cache
    monkeypatch.setattr(nodes, "get_query_cache", lambda: FakeQueryCache())

    state = _state(
        "product_consult",
        "星耀X100的电池容量是多少",
        current_tool="kb_search",
        tool_results={"success": True, "data": []},
    )
    result = nodes.generate_answer_node(state)
    assert result.get("final_answer") == "缓存答案"
    assert result.get("from_cache") is True
    assert result.get("cache_source") == "faq"


def test_generate_answer_llm_cache_hit_marked(monkeypatch):
    """非 KB 意图命中 LLM 缓存时标记 cache_source=llm。"""
    monkeypatch.setattr(
        nodes, "get_cache_sync",
        lambda **kwargs: {
            "answer": "LLM 缓存答案", "sources": [], "suggestions": ["然后呢"],
        },
    )
    state = _state(
        "order_query", "我的订单",
        current_tool="query_order",
        tool_results={"success": True, "data": []},
    )
    result = nodes.generate_answer_node(state)
    assert result.get("final_answer") == "LLM 缓存答案"
    assert result.get("from_cache") is True
    assert result.get("cache_source") == "llm"
