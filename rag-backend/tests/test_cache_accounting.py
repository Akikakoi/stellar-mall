"""缓存命中记账：FAQ 旁路/LLM 缓存必须标记来源并计入指标。"""
from __future__ import annotations

import ast
from pathlib import Path

from app.agent import metrics

NODES = Path(__file__).resolve().parents[1] / "app" / "agent" / "nodes.py"


def test_cache_hit_returns_are_marked():
    """从源码 AST 校验：FAQ 旁路和 LLM 缓存命中的 return 都带 from_cache + cache_source。"""
    tree = ast.parse(NODES.read_text(encoding="utf-8"))
    marked_returns = 0
    for node in ast.walk(tree):
        if not isinstance(node, ast.Return) or not isinstance(node.value, ast.Dict):
            continue
        keys = {
            k.value for k in node.value.keys
            if isinstance(k, ast.Constant) and isinstance(k.value, str)
        }
        if {"from_cache", "cache_source"} <= keys:
            marked_returns += 1
    # FAQ 旁路(faq) + LLM 缓存( llm ) 两处
    assert marked_returns >= 2


class FakePipe:
    def __init__(self):
        self.calls = []

    def hincrby(self, key, field, n=1):
        self.calls.append((key, field, n))
        return self

    def expire(self, key, ttl):
        return self

    def execute(self):
        return []


class FakeRedis:
    def __init__(self):
        self.pipe = FakePipe()

    def pipeline(self):
        return self.pipe


def test_record_agent_run_counts_cache_source(monkeypatch):
    fake = FakeRedis()
    monkeypatch.setattr(metrics, "get_redis_sync", lambda: fake)
    metrics.record_agent_run(
        intent="product_consult", uses_react=False,
        answer_cache_hit=True, latency_ms=12, cache_source="faq",
    )
    fields = [call[1] for call in fake.pipe.calls]
    assert "answer_cache_hit" in fields
    assert "cache_source:faq" in fields
    assert "runs" in fields


def test_record_agent_run_no_source_when_miss(monkeypatch):
    fake = FakeRedis()
    monkeypatch.setattr(metrics, "get_redis_sync", lambda: fake)
    metrics.record_agent_run(
        intent="order_query", uses_react=False,
        answer_cache_hit=False, latency_ms=50,
    )
    fields = [call[1] for call in fake.pipe.calls]
    assert "answer_cache_hit" not in fields
    assert not any(f.startswith("cache_source:") for f in fields)
