"""工具注册表单一来源的回归测试（不触发 app.config 密钥校验）。"""
from __future__ import annotations

from app.agent import react_safety
from app.agent.tool_registry import (
    HIGH_RISK_WRITE,
    HIGH_RISK_WRITE_TOOLS,
    INTENT_TO_TOOL,
    READ_ONLY_TOOLS,
    REACT_TOOL_NAMES,
    REVERSIBLE_WRITE,
    REVERSIBLE_WRITE_TOOLS,
    SPECS,
    TOOL_LEVELS,
    TOOL_REQUIRED_PARAMS,
)


def test_registry_covers_all_business_intents():
    expected = {
        "product_consult": "kb_search",
        "order_query": "query_order",
        "cancel_order": "cancel_order",
        "confirm_receipt": "confirm_receipt",
        "cart_query": "query_cart",
        "cart_add": "add_to_cart",
        "cart_clear": "clear_cart",
        "cart_delete": "delete_cart_item",
        "cart_update": "update_cart_item",
        "after_sales": "apply_after_sales",
        "favorite_query": "query_favorite",
        "wallet_query": "query_wallet",
        "product_search": "product_search",
        "review_query": "query_reviews",
    }
    assert INTENT_TO_TOOL == expected


def test_required_params_match_existing_contract():
    assert TOOL_REQUIRED_PARAMS["kb_search"]["required"] == ["query"]
    assert TOOL_REQUIRED_PARAMS["query_order"]["required"] == []
    assert TOOL_REQUIRED_PARAMS["apply_after_sales"]["required"] == [
        "order_id", "after_sales_type", "reason",
    ]
    assert TOOL_REQUIRED_PARAMS["add_to_cart"]["required"] == ["sku_id"]


def test_risk_levels_unchanged_and_fail_closed():
    assert len(READ_ONLY_TOOLS) == 8
    assert len(REVERSIBLE_WRITE_TOOLS) == 2
    assert len(HIGH_RISK_WRITE_TOOLS) == 5
    assert len(SPECS) == 15
    assert react_safety.tool_level("not_registered") == HIGH_RISK_WRITE
    assert react_safety.tool_level("add_to_cart") == REVERSIBLE_WRITE
    assert react_safety.tool_level("cancel_order") == HIGH_RISK_WRITE
    assert TOOL_LEVELS == react_safety.TOOL_RISK_LEVELS


def test_confirmation_standard_is_shared():
    """流水线按 intent、ReAct 按 tool_name，最终都走同一套确认词。"""
    from app.agent.tool_registry import INTENT_TO_TOOL, is_confirmed

    for intent, tool in INTENT_TO_TOOL.items():
        assert is_confirmed(tool, "确认") is True
        assert is_confirmed(tool, "继续查下一条") is False
    # 动作词不是确认；"确定取消"里的"确定"才是确认词，两条链路一致
    assert is_confirmed("cancel_order", "确定取消") is True
    assert is_confirmed(INTENT_TO_TOOL["cancel_order"], "确定取消") is True
    assert is_confirmed("cancel_order", "取消订单 87") is False


def test_high_risk_tools_never_exposed_to_react():
    assert REACT_TOOL_NAMES == READ_ONLY_TOOLS | REVERSIBLE_WRITE_TOOLS
    assert not (REACT_TOOL_NAMES & HIGH_RISK_WRITE_TOOLS)
    for spec in SPECS:
        if spec.risk == HIGH_RISK_WRITE:
            assert spec.confirm_message
