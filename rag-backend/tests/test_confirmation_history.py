"""确认轮参数补全回归测试：只回复确认语时从上一轮用户消息补参数。"""
from __future__ import annotations

from langchain_core.messages import AIMessage, HumanMessage

from app.agent.nodes import _confirmation_history_query, check_params_node

ORDER = "SO20261031120003874B0D641"
INIT = f"我要申请售后，订单号 {ORDER}，退款，不想要了"
CONFIRM_TEXT = (
    f"即将提交售后申请（订单 {ORDER}，类型 退款）。"
    "请回复「确认申请售后」后执行；回复其他内容将不会执行。"
)


def make_state(messages: list, intent: str = "after_sales") -> dict:
    return {"intent": intent, "messages": messages, "required_params": {}}


def test_short_confirmation_reuses_previous_user_params():
    state = make_state([HumanMessage(INIT), AIMessage(CONFIRM_TEXT), HumanMessage("确认申请售后")])
    result = check_params_node(state)
    assert result["current_tool"] == "apply_after_sales"
    assert result["required_params"]["order_id"] == ORDER
    assert result["required_params"]["after_sales_type"] == "refund"
    assert result["required_params"]["reason"]
    assert not result.get("confirmation_required")


def test_no_fallback_without_confirmation_prompt():
    state = make_state([
        HumanMessage(INIT),
        AIMessage("请问您想申请哪种售后类型？"),
        HumanMessage("确认申请售后"),
    ])
    result = check_params_node(state)
    assert result["current_tool"] == "apply_after_sales"
    assert "after_sales_type" in result["missing_params"]
    assert "reason" in result["missing_params"]


def test_no_confirmation_word_does_not_reuse_params():
    state = make_state([HumanMessage(INIT), AIMessage(CONFIRM_TEXT), HumanMessage("申请售后")])
    result = check_params_node(state)
    assert result["current_tool"] == "apply_after_sales"
    assert "after_sales_type" in result["missing_params"]
    assert "reason" in result["missing_params"]


def test_confirmation_helper_requires_prompt_and_high_risk_intent():
    state = make_state([HumanMessage(INIT), AIMessage("这是普通回答"), HumanMessage("确认申请售后")])
    assert _confirmation_history_query(state, "after_sales", "确认申请售后") == ""

    state = make_state([HumanMessage(INIT), AIMessage(CONFIRM_TEXT), HumanMessage("确认申请售后")])
    assert _confirmation_history_query(state, "after_sales", "确认申请售后") == INIT
    assert _confirmation_history_query(state, "product_consult", "确认申请售后") == ""


def test_does_not_reuse_params_from_another_write_intent():
    state = make_state([
        HumanMessage("帮我取消订单 20261116001"),
        AIMessage(CONFIRM_TEXT),
        HumanMessage("确认申请售后"),
    ])
    assert _confirmation_history_query(state, "after_sales", "确认申请售后") == ""
