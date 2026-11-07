"""MCP 高危写工具 confirmation 闸门测试（AST 检查，不启动 MCP）。"""
from __future__ import annotations

import ast
from pathlib import Path
from typing import Optional

MCP = Path(__file__).resolve().parents[1] / "app" / "mcp_server.py"


def load_function(name: str):
    tree = ast.parse(MCP.read_text(encoding="utf-8"))
    node = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == name)
    node.decorator_list = []
    namespace = {"Optional": Optional}
    exec(compile(ast.Module(body=[node], type_ignores=[]), str(MCP), "exec"), namespace)
    return namespace[name]


def test_guard_rejects_without_confirm():
    result = load_function("_mcp_write_guard")(False)
    assert result["success"] is False
    assert "confirm=true" in result["message"]


def test_guard_allows_with_confirm():
    assert load_function("_mcp_write_guard")(True) is None


def test_high_risk_tools_expose_confirm_parameter():
    tree = ast.parse(MCP.read_text(encoding="utf-8"))
    functions = {n.name: n for n in tree.body if isinstance(n, ast.FunctionDef)}
    for name in ("cancel_order", "confirm_receipt", "delete_cart_item", "clear_cart", "apply_after_sales"):
        args = [a.arg for a in functions[name].args.args]
        assert "confirm" in args, f"{name} 缺少 confirm 参数"