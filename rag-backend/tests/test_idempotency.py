"""幂等守卫与 user_id fail-closed 回归测试。"""
from __future__ import annotations

import pytest

from app.agent import react_safety
from app.agent.react_safety import RedisIdempotencyGuard, safe_invoke


class FakeRedis:
    def __init__(self):
        self.store = {}
        self.calls = []

    def set(self, key, value, nx=False, ex=None):
        self.calls.append((key, nx, ex))
        if nx and key in self.store:
            return False
        self.store[key] = value
        return True


class FakeTool:
    def invoke(self, params):
        return {"success": True, "params": params}


def test_redis_guard_allows_once_and_blocks_repeat(monkeypatch):
    fake = FakeRedis()
    monkeypatch.setattr(react_safety, "_get_redis_client", lambda: fake)
    guard = RedisIdempotencyGuard(user_id=42, ttl_seconds=300)

    assert guard.is_repeated("add_to_cart|abc") is False
    assert guard.is_repeated("add_to_cart|abc") is True
    assert guard.is_repeated("add_to_cart|def") is False
    assert all(key.startswith("agent:idem:42:") for key, _, _ in fake.calls)
    assert all(ex == 300 for _, _, ex in fake.calls)


def test_redis_guard_falls_back_to_memory(monkeypatch):
    monkeypatch.setattr(react_safety, "_get_redis_client", lambda: None)
    guard = RedisIdempotencyGuard(user_id=42)

    assert guard.is_repeated("cancel_order|1") is False
    assert guard.is_repeated("cancel_order|1") is True


def test_redis_guard_requires_user_id():
    with pytest.raises(ValueError):
        RedisIdempotencyGuard(user_id=0)


def test_write_blocked_without_user_id():
    result = safe_invoke(
        "add_to_cart",
        {"sku_id": "123"},
        FakeTool(),
        sourced_ids={"123"},
        idempotency_guard=RedisIdempotencyGuard(user_id=7),
    )
    assert result["blocked"] is True
    assert result["reason"] == "missing_user_id"


def test_read_tool_still_works_without_user_id():
    result = safe_invoke("kb_search", {"query": "手机"}, FakeTool())
    assert result["success"] is True
