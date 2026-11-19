"""Agent 工具注册表：工具元数据与风险分级的单一来源。

设计约定：
- 这里只放"描述性元数据"，不 import 任何工具实现，避免把 app.config 的密钥校验
  带进纯元数据测试。
- graph 侧（nodes.py）的意图映射/必填参数、ReAct 侧（react_agent.py）的可见工具集、
  安全侧（react_safety.py）的风险分级，全部从本文件派生。
- 新增工具时：在本文件加一条 ToolSpec；若图侧需要执行，再在 nodes.py 的
  _tool_execution_impl 增加分派分支（该分支含结果后处理，暂不纳入注册表）。
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, FrozenSet, Tuple

READ_ONLY = "read_only"
REVERSIBLE_WRITE = "reversible_write"
HIGH_RISK_WRITE = "high_risk_write"


@dataclass(frozen=True)
class ToolSpec:
    name: str
    risk: str
    intents: Tuple[str, ...] = ()
    required_params: Tuple[str, ...] = ()
    optional_params: Tuple[str, ...] = ()
    allowed_in_react: bool = False
    identifier_reqs: Tuple[str, ...] = ()
    confirm_message: str = ""


def _spec(**kwargs) -> ToolSpec:
    return ToolSpec(**kwargs)


SPECS: Tuple[ToolSpec, ...] = (
    _spec(
        name="kb_search",
        risk=READ_ONLY,
        intents=("product_consult",),
        required_params=("query",),
        optional_params=("top_k", "tags_filter"),
        allowed_in_react=True,
    ),
    _spec(
        name="query_order",
        risk=READ_ONLY,
        intents=("order_query",),
        required_params=(),
        optional_params=("order_id", "status"),
        allowed_in_react=True,
    ),
    _spec(
        name="apply_after_sales",
        risk=HIGH_RISK_WRITE,
        intents=("after_sales",),
        required_params=("order_id", "after_sales_type", "reason"),
        optional_params=("sku_id", "description", "contact_phone"),
        identifier_reqs=("order_id",),
        confirm_message="您希望申请售后，请确认退款/退货/换货的类型。",
    ),
    _spec(
        name="query_cart",
        risk=READ_ONLY,
        intents=("cart_query",),
        allowed_in_react=True,
    ),
    _spec(
        name="clear_cart",
        risk=HIGH_RISK_WRITE,
        intents=("cart_clear",),
        confirm_message="确定要清空购物车吗？此操作会移除全部购物车商品。",
    ),
    _spec(
        name="delete_cart_item",
        risk=HIGH_RISK_WRITE,
        intents=("cart_delete",),
        required_params=("cart_item_id",),
        identifier_reqs=("cart_item_id",),
        confirm_message="确定要从购物车删除该商品吗？（删除后不可恢复）",
    ),
    _spec(
        name="update_cart_item",
        risk=REVERSIBLE_WRITE,
        intents=("cart_update",),
        required_params=("cart_item_id",),
        optional_params=("qty", "checked"),
        identifier_reqs=("cart_item_id",),
        allowed_in_react=True,
    ),
    _spec(
        name="cancel_order",
        risk=HIGH_RISK_WRITE,
        intents=("cancel_order",),
        required_params=("order_id",),
        identifier_reqs=("order_id",),
        confirm_message="确定要取消该订单吗？（仅待付款/待发货订单可取消）",
    ),
    _spec(
        name="confirm_receipt",
        risk=HIGH_RISK_WRITE,
        intents=("confirm_receipt",),
        required_params=("order_id",),
        identifier_reqs=("order_id",),
        confirm_message="确定要确认收货吗？（确认后将触发结算，无法撤销）",
    ),
    _spec(
        name="query_favorite",
        risk=READ_ONLY,
        intents=("favorite_query",),
        allowed_in_react=True,
    ),
    _spec(
        name="add_to_cart",
        risk=REVERSIBLE_WRITE,
        intents=("cart_add",),
        required_params=("sku_id",),
        optional_params=("qty",),
        identifier_reqs=("sku_id",),
        allowed_in_react=True,
    ),
    _spec(
        name="query_wallet",
        risk=READ_ONLY,
        intents=("wallet_query",),
        allowed_in_react=True,
    ),
    _spec(
        name="product_search",
        risk=READ_ONLY,
        intents=("product_search",),
        required_params=("query",),
        optional_params=("page", "page_size", "category"),
        allowed_in_react=True,
    ),
    _spec(
        name="query_reviews",
        risk=READ_ONLY,
        intents=("review_query",),
        required_params=("spu_id",),
        optional_params=("page", "page_size"),
        allowed_in_react=True,
    ),
    # ReAct 专用工具：不在图侧意图映射里，由 SKU 解析提示词主动调用
    _spec(
        name="resolve_sku",
        risk=READ_ONLY,
        required_params=("product_name",),
        optional_params=("spec_description",),
        allowed_in_react=True,
    ),
)

TOOL_LEVELS: Dict[str, str] = {spec.name: spec.risk for spec in SPECS}

READ_ONLY_TOOLS: FrozenSet[str] = frozenset(
    spec.name for spec in SPECS if spec.risk == READ_ONLY
)
REVERSIBLE_WRITE_TOOLS: FrozenSet[str] = frozenset(
    spec.name for spec in SPECS if spec.risk == REVERSIBLE_WRITE
)
HIGH_RISK_WRITE_TOOLS: FrozenSet[str] = frozenset(
    spec.name for spec in SPECS if spec.risk == HIGH_RISK_WRITE
)

INTENT_TO_TOOL: Dict[str, str] = {
    intent: spec.name
    for spec in SPECS
    for intent in spec.intents
}

TOOL_REQUIRED_PARAMS: Dict[str, Dict[str, list]] = {
    spec.name: {
        "required": list(spec.required_params),
        "optional": list(spec.optional_params),
    }
    for spec in SPECS
}

IDENTIFIER_PARAMS: FrozenSet[str] = frozenset((
    "order_id", "sku_id", "cart_item_id", "spu_id",
))

WRITE_IDENTIFIER_REQS: Dict[str, list] = {
    spec.name: list(spec.identifier_reqs)
    for spec in SPECS
    if spec.risk in (REVERSIBLE_WRITE, HIGH_RISK_WRITE)
}

CONFIRM_MESSAGE_TEMPLATE: Dict[str, str] = {
    spec.name: spec.confirm_message
    for spec in SPECS
    if spec.risk == HIGH_RISK_WRITE
}

# 意图中文描述：nodes 生成追问话术用，单一来源
INTENT_DESC_MAP: Dict[str, str] = {
    "product_consult": "咨询商品信息",
    "order_query": "查询订单",
    "cancel_order": "取消订单",
    "confirm_receipt": "确认收货",
    "cart_query": "查询购物车",
    "cart_add": "加入购物车",
    "cart_clear": "清空购物车",
    "cart_delete": "删除购物车商品",
    "cart_update": "修改购物车商品",
    "after_sales": "申请售后",
    "favorite_query": "查询收藏夹",
    "wallet_query": "查询钱包余额",
    "product_search": "搜索商品",
    "review_query": "查询商品评价",
    "small_talk": "闲聊",
    "other": "其他问题",
}

# 通用确认词：流水线侧按 intent 判定（拿不到具体 tool_name 时的兜底）。
# 刻意不含"继续"——"继续查下一条"会被误判为确认执行，两条链路都不该认。
GENERIC_CONFIRM_WORDS: Tuple[str, ...] = ("确认", "确定", "同意", "是的", "嗯", "好的")

REACT_TOOL_NAMES: FrozenSet[str] = frozenset(
    spec.name for spec in SPECS if spec.allowed_in_react
)

HIGH_RISK_WRITE_INTENTS: FrozenSet[str] = frozenset(
    intent for spec in SPECS if spec.risk == HIGH_RISK_WRITE for intent in spec.intents
)


def is_confirmed(tool_name: str, text: str) -> bool:
    """统一的确认词判定：流水线侧与 ReAct 侧共用同一套通用确认词。

    不使用按工具定制的动作词（如"取消""退款"）——那些是发起指令的一部分，
    命中它们会把"取消订单 87"这类原始请求误判为已确认。
    高危操作必须出现明确的确认词（确认/确定/同意/是的/嗯/好的）才放行；
    "继续"也不认——"继续查下一条"会被误判为确认执行。
    """
    normalized = (text or "").strip()
    return bool(normalized) and any(w in normalized for w in GENERIC_CONFIRM_WORDS)


__all__ = [
    "ToolSpec",
    "SPECS",
    "TOOL_LEVELS",
    "READ_ONLY_TOOLS",
    "REVERSIBLE_WRITE_TOOLS",
    "HIGH_RISK_WRITE_TOOLS",
    "INTENT_TO_TOOL",
    "INTENT_DESC_MAP",
    "GENERIC_CONFIRM_WORDS",
    "is_confirmed",
    "TOOL_REQUIRED_PARAMS",
    "IDENTIFIER_PARAMS",
    "WRITE_IDENTIFIER_REQS",
    "CONFIRM_MESSAGE_TEMPLATE",
    "REACT_TOOL_NAMES",
    "HIGH_RISK_WRITE_INTENTS",
]
