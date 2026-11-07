"""高危写操作二次确认的纯函数回归测试（AST 加载，不导入 app 配置）。"""
from __future__ import annotations

import ast
import re
from pathlib import Path

NODES = Path(__file__).resolve().parents[1] / "app" / "agent" / "nodes.py"


def load_function(name: str, namespace: dict):
    tree = ast.parse(NODES.read_text(encoding="utf-8"))
    node = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == name)
    node.decorator_list = []
    exec(compile(ast.Module(body=[node], type_ignores=[]), str(NODES), "exec"), namespace)
    return namespace[name]


def test_confirmation_words():
    fn = load_function("_is_write_confirmed", {"re": re})
    assert fn("取消订单 87") is False
    assert fn("确认取消订单 87") is True
    assert fn("确定清空购物车") is True


def test_confirmation_text_contains_action_and_param():
    text = load_function("_write_confirmation_text", {})({
        "intent": "cancel_order", "params": {"order_id": "87"},
    })
    assert "87" in text
    assert "确认取消订单" in text


def test_confirmation_suggestions_keep_after_sales_params():
    fn = load_function("_write_confirmation_suggestions", {})
    suggestions = fn({
        "intent": "after_sales",
        "params": {"order_id": "87", "after_sales_type": "refund", "reason": "质量问题"},
    })
    assert suggestions[0].startswith("确认申请售后")
    assert "退款" in suggestions[0]
    assert "质量问题" in suggestions[0]

    return_suggestions = fn({
        "intent": "after_sales",
        "params": {"order_id": "87", "after_sales_type": "return", "reason": "质量问题"},
    })
    assert "退货" in return_suggestions[0]
    assert "退款" not in return_suggestions[0]


def test_route_after_params_waits_for_confirmation():
    fn = load_function("route_after_params", {"AgentState": dict})
    assert fn({"confirmation_required": True}) == "ask_params"
    assert fn({"missing_params": ["order_id"]}) == "ask_params"
    assert fn({"missing_params": []}) == "execute_tool"