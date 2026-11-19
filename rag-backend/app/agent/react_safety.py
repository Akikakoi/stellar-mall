"""ReAct 安全防护层：工具分级、参数来源约束、写操作确认钩子与幂等检查。

用于把"能否调用写工具、参数是否可信、是否需用户确认、是否重复执行"统一收敛到一处，
避免 ReAct 迭代循环中 LLM 自主执行写操作带来的风险。

设计目标（对应 matplotlib 里的电商客服场景）：
1. 只读工具直接放行；
2. 可逆写操作（改购物车/加购）放行但强制参数来源约束 + 幂等；
3. 高危写操作（删购物车/清空/取消订单/确认收货/售后）必须经用户确认或确认词校验。

核心入口：
    decision = check_write_action(tool_name, params, user_confirmed, confirm_text, sourced_ids)
    result  = safe_invoke(tool_name, params, base_tool, user_confirmed, confirm_text, sourced_ids)
"""
from __future__ import annotations

import hashlib
from collections import OrderedDict
from typing import Dict, List, Optional, Set

from app.core.logger import logger

# ---- 工具风险元数据统一来自 tool_registry（单一来源）----
from app.agent.tool_registry import (  # noqa: E402
    HIGH_RISK_WRITE,
    CONFIRM_MESSAGE_TEMPLATE,
    IDENTIFIER_PARAMS,
    READ_ONLY_TOOLS as _REGISTRY_READ_ONLY_TOOLS,
    REVERSIBLE_WRITE_TOOLS as _REGISTRY_REVERSIBLE_WRITE_TOOLS,
    TOOL_LEVELS,
    WRITE_IDENTIFIER_REQS,
    HIGH_RISK_WRITE_TOOLS as _REGISTRY_HIGH_RISK_WRITE_TOOLS,
    is_confirmed,
)

READ_ONLY_TOOLS: Set[str] = set(_REGISTRY_READ_ONLY_TOOLS)
REVERSIBLE_WRITE_TOOLS: Set[str] = set(_REGISTRY_REVERSIBLE_WRITE_TOOLS)
HIGH_RISK_WRITE_TOOLS: Set[str] = set(_REGISTRY_HIGH_RISK_WRITE_TOOLS)
TOOL_RISK_LEVELS: Dict[str, str] = dict(TOOL_LEVELS)


def tool_level(tool_name: str) -> str:
    """返回工具风险等级，未登记工具一律按 high_risk_write 处理（fail-closed）。"""
    return TOOL_LEVELS.get(tool_name, HIGH_RISK_WRITE)


# ---- 标识类参数与确认词均来自 tool_registry，禁止 LLM 从历史推断 ----


def _has_confirm_word(tool_name: str, confirm_text: str, user_confirmed: bool) -> bool:
    """判断是否获得确认：显式 user_confirmed 或确认文本命中确认词。

    与流水线侧共用 tool_registry.is_confirmed，确保同一个商城确认标准一致。
    """
    if user_confirmed:
        return True
    return is_confirmed(tool_name, confirm_text)


def _identifier_can_trust(tool_name: str, params: dict, sourced_ids: Optional[Set[str]]) -> bool:
    """标识参数来源校验：所需的标识值必须出现在 sourced_ids 白名单中。

    sourced_ids 由调用方提供——应来自「query_cart / query_order / resolve_sku 等只读工具
    返回的结果 id」与「用户本轮显式声明」。若某标识值不在白名单，说明可能是 LLM 从
    历史对话推断而来，予以拒绝。
    """
    if not sourced_ids:
        return False
    for key in WRITE_IDENTIFIER_REQS.get(tool_name, []):
        value = params.get(key)
        if value is None or str(value) not in sourced_ids:
            return False
    return True


def check_write_action(
    tool_name: str,
    params: Optional[dict] = None,
    user_confirmed: bool = False,
    confirm_text: str = "",
    sourced_ids: Optional[Set[str]] = None,
) -> dict:
    """对一次写操作做安全检查，返回决策。

    Returns:
        {
            "allowed": bool,          # True 才允许执行
            "require_confirm": bool,  # 是否仍需用户确认
            "confirm_message": str,   # 需确认时给出话术
            "reason": str,            # 拦截原因/放行说明
            "idempotency_key": str|None,
        }
    """
    params = params or {}
    level = tool_level(tool_name)
    decision = {
        "allowed": False,
        "require_confirm": False,
        "confirm_message": "",
        "reason": "",
    }

    if level == "read_only":
        decision.update({"allowed": True, "reason": "只读工具，直接放行"})
        return decision

    # 写操作：先做参数来源约束
    if not _identifier_can_trust(tool_name, params, sourced_ids):
        decision.update({
            "require_confirm": True,
            "confirm_message": CONFIRM_MESSAGE_TEMPLATE.get(
                tool_name, "请确认您的操作信息（目标编号必须来自本人查询结果）。"
            ),
            "reason": f"标识参数来源不可信：{WRITE_IDENTIFIER_REQS.get(tool_name, [])} 必须来自已查询结果或用户显式说明",
        })
        return decision

    if level == "reversible_write":
        decision.update({
            "allowed": True,
            "reason": "可逆写操作，参数来源可信，放行（仍建议幂等）",
            "idempotency_key": _make_idempotency_key(tool_name, params),
        })
        return decision

    # high_risk_write：必须确认
    if _has_confirm_word(tool_name, confirm_text, user_confirmed):
        decision.update({
            "allowed": True,
            "reason": "高危写操作，已获得用户确认",
            "idempotency_key": _make_idempotency_key(tool_name, params),
        })
        return decision

    decision.update({
        "require_confirm": True,
        "confirm_message": CONFIRM_MESSAGE_TEMPLATE.get(
            tool_name, "此操作不可逆，请先向用户确认后再执行。"
        ),
        "reason": "高危写操作未确认，禁止执行",
    })
    return decision


def _make_idempotency_key(tool_name: str, params: dict) -> str:
    """按 工具名+规范化参数 生成幂等 key，用于防止 ReAct 重复执行同一写操作。"""
    raw = {
        k: params.get(k)
        for k in sorted(IDENTIFIER_PARAMS)
        if params.get(k) is not None
    }
    payload = f"{tool_name}|{hashlib.sha256(str(sorted(raw.items())).encode()).hexdigest()}"
    return payload


class IdempotencyGuard:
    """简单的内存幂等守卫（LRU），防止同一写操作被 ReAct 循环重复调用。

    说明：单进程内存实现，够用即可；如需跨实例可改换成 Redis。
    """

    def __init__(self, max_entries: int = 16, ttl_seconds: int = 300):
        self._max = max(1, max_entries)
        self._ttl = ttl_seconds
        self._order: "OrderedDict[str, float]" = OrderedDict()  # key -> timestamp
        import time
        self._time = time

    def _evict(self) -> None:
        now = self._time.time()
        while self._order:
            oldest_key, ts = next(iter(self._order.items()))
            if now - ts > self._ttl:
                self._order.popitem(last=False)
            else:
                break
        while len(self._order) >= self._max:
            self._order.popitem(last=False)

    def is_repeated(self, idempotency_key: str) -> bool:
        """若 key 在窗口内已记录，返回 True（视为重复）。"""
        now = self._time.time()
        self._evict()
        if idempotency_key in self._order:
            return True
        self._order[idempotency_key] = now
        return False


def _get_redis_client():
    """惰性获取同步 Redis 客户端，避免安全模块在导入期触发配置校验。"""
    from app.rag.llm_cache import get_redis_sync

    return get_redis_sync()


class RedisIdempotencyGuard:
    """跨进程幂等守卫：SET key NX EX，Redis 不可用时降级为内存 LRU。"""

    def __init__(self, user_id: int, ttl_seconds: int = 300,
                 namespace: str = "agent:idem"):
        if not user_id:
            raise ValueError("user_id 缺失时不允许构造 Redis 幂等守卫")
        self.user_id = user_id
        self.ttl_seconds = ttl_seconds
        self.namespace = namespace
        self._fallback = IdempotencyGuard(max_entries=32, ttl_seconds=ttl_seconds)

    def is_repeated(self, idempotency_key: str) -> bool:
        client = _get_redis_client()
        if client is None:
            logger.warning("[Idempotency] Redis 不可用，降级为内存幂等守卫")
            return self._fallback.is_repeated(idempotency_key)
        key = f"{self.namespace}:{self.user_id}:{idempotency_key}"
        try:
            acquired = client.set(key, "1", nx=True, ex=self.ttl_seconds)
            return not bool(acquired)
        except Exception as e:  # noqa: BLE001
            logger.warning(f"[Idempotency] Redis 写入失败，降级为内存守卫: {e}")
            return self._fallback.is_repeated(idempotency_key)


# ---- 供 ReAct 图接入的便捷入口 ----

def safe_invoke(
    tool_name: str,
    params: dict,
    base_tool,
    *,
    user_id: Optional[int] = None,
    user_confirmed: bool = False,
    confirm_text: str = "",
    sourced_ids: Optional[Set[str]] = None,
    idempotency_guard: Optional[IdempotencyGuard] = None,
) -> dict:
    """带完整防护的工具调用入口。

    规则：
        1. 只读工具直接执行；
        2. 可逆写/高危写先过 check_write_action，未放行则返回拦截消息（不执行原工具）；
        3. 写操作若命中幂等 key，直接返回"重复请求"提示。

    Returns:
        拦截时: {"success": False, "blocked": True, "message": ..., "reason": ...}
        放行时: base_tool.invoke(params) 的原样结果
    """
    if tool_level(tool_name) == "read_only":
        return base_tool.invoke(params)

    # 写操作必须有真实用户身份，否则多副本幂等 key 无法隔离，直接 fail-closed
    if not user_id:
        return {
            "success": False,
            "blocked": True,
            "message": "无法确认当前用户身份，写操作未执行。",
            "reason": "missing_user_id",
            "require_confirm": False,
        }

    decision = check_write_action(
        tool_name, params,
        user_confirmed=user_confirmed,
        confirm_text=confirm_text,
        sourced_ids=sourced_ids,
    )
    if not decision["allowed"]:
        return {
            "success": False,
            "blocked": True,
            "message": decision["confirm_message"],
            "reason": decision["reason"],
            "require_confirm": decision["require_confirm"],
        }

    key = decision.get("idempotency_key")
    if key and idempotency_guard and idempotency_guard.is_repeated(key):
        return {
            "success": False,
            "blocked": True,
            "message": "该操作与上一条重复，已自动跳过（如果您确实要再次执行，请稍后再试）。",
            "reason": "idempotency",
        }

    try:
        return base_tool.invoke(params)
    except Exception as e:  # noqa: BLE001
        return {"success": False, "blocked": False, "message": f"工具执行失败: {e}"}


__all__ = [
    "READ_ONLY_TOOLS",
    "REVERSIBLE_WRITE_TOOLS",
    "HIGH_RISK_WRITE_TOOLS",
    "TOOL_RISK_LEVELS",
    "tool_level",
    "IDENTIFIER_PARAMS",
    "WRITE_IDENTIFIER_REQS",
    "CONFIRM_MESSAGE_TEMPLATE",
    "check_write_action",
    "IdempotencyGuard",
    "RedisIdempotencyGuard",
    "safe_invoke",
]