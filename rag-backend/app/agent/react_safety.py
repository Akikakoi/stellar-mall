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

# ---- 工具名统一使用 graph 侧 current_tool 的逻辑名（与 nodes.py 一致）----

# 只读工具：无副作用，ReAct 可直接调用
READ_ONLY_TOOLS: Set[str] = {
    "kb_search",
    "query_order",
    "query_cart",
    "query_favorite",
    "query_wallet",
    "query_reviews",
    "product_search",
    "resolve_sku",
}

# 可逆写操作：放行，但强制参数来源约束 + 幂等
REVERSIBLE_WRITE_TOOLS: Set[str] = {
    "add_to_cart",
    "update_cart_item",
}

# 高危写操作：必须用户确认（确认词触发或显式 user_confirmed=True）
HIGH_RISK_WRITE_TOOLS: Set[str] = {
    "delete_cart_item",
    "clear_cart",
    "cancel_order",
    "confirm_receipt",
    "apply_after_sales",
}

# 风险等级聚合表，未知工具一律按最严格处理
_TOOL_LEVEL: Dict[str, str] = {}
for _t in READ_ONLY_TOOLS:
    _TOOL_LEVEL[_t] = "read_only"
for _t in REVERSIBLE_WRITE_TOOLS:
    _TOOL_LEVEL[_t] = "reversible_write"
for _t in HIGH_RISK_WRITE_TOOLS:
    _TOOL_LEVEL[_t] = "high_risk_write"

TOOL_RISK_LEVELS: Dict[str, str] = _TOOL_LEVEL


def tool_level(tool_name: str) -> str:
    """返回工具风险等级，未登记工具一律按 high_risk_write 处理（fail-closed）。"""
    return _TOOL_LEVEL.get(tool_name, "high_risk_write")


# ---- 标识类参数：必须来自"工具返回结果或用户本轮显式声明"，禁止 LLM 从历史推断 ----
# 这类参数是写操作的目标，也是最容易被 LLM 编造（从旧对话里猜出 order_id/sku_id）的字段。
IDENTIFIER_PARAMS: Set[str] = {"order_id", "sku_id", "cart_item_id", "spu_id"}

# 每个写操作依赖的标识参数（缺一即无法安全执行）
WRITE_IDENTIFIER_REQS: Dict[str, List[str]] = {
    "add_to_cart": ["sku_id"],
    "update_cart_item": ["cart_item_id"],
    "delete_cart_item": ["cart_item_id"],
    "clear_cart": [],
    "cancel_order": ["order_id"],
    "confirm_receipt": ["order_id"],
    "apply_after_sales": ["order_id"],
}

# ---- 高危写操作的确认词：命中即视为用户同意（防止 LLM 复述历史时误判）----
REQUIRED_CONFIRM_WORDS: Dict[str, List[str]] = {
    "delete_cart_item": ["删除", "确定删除", "确认删除"],
    "clear_cart": ["清空", "确定清空", "全部清空", "清空购物车"],
    "cancel_order": ["取消订单", "确定取消", "取消"],
    "confirm_receipt": ["确认收货", "确定收货"],
    "apply_after_sales": ["申请售后", "退款", "退货", "换货", "确定"],
}

# 高危写操作的确认话术模板，供 agent 生成"是否确认"提示
CONFIRM_MESSAGE_TEMPLATE: Dict[str, str] = {
    "delete_cart_item": "确定要从购物车删除该商品吗？（删除后不可恢复）",
    "clear_cart": "确定要清空购物车吗？此操作会移除全部购物车商品。",
    "cancel_order": "确定要取消该订单吗？（仅待付款/待发货订单可取消）",
    "confirm_receipt": "确定要确认收货吗？（确认后将触发结算，无法撤销）",
    "apply_after_sales": "您希望申请售后，请确认退款/退货/换货的类型。",
}


def _normalize_text(text: str) -> str:
    return (text or "").strip()


def _has_confirm_word(tool_name: str, confirm_text: str, user_confirmed: bool) -> bool:
    """判断是否获得确认：显式 user_confirmed 或确认文本命中确认词。"""
    if user_confirmed:
        return True
    text = _normalize_text(confirm_text)
    if not text:
        return False
    return any(w in text for w in REQUIRED_CONFIRM_WORDS.get(tool_name, []))


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
            "reason": f"可逆写操作，参数来源可信，放行（仍建议幂等）",
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


# ---- 供 ReAct 图接入的便捷入口 ----

def safe_invoke(
    tool_name: str,
    params: dict,
    base_tool,
    *,
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
    "REQUIRED_CONFIRM_WORDS",
    "CONFIRM_MESSAGE_TEMPLATE",
    "check_write_action",
    "IdempotencyGuard",
    "safe_invoke",
]