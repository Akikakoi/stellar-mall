"""review_query 的 SPU 参数解析回归测试（AST 加载，不导入 app 配置）。"""
from __future__ import annotations

import ast
import re
from pathlib import Path
from types import SimpleNamespace

NODES = Path(__file__).resolve().parents[1] / "app" / "agent" / "nodes.py"


def load_function(name: str, namespace: dict):
    tree = ast.parse(NODES.read_text(encoding="utf-8"))
    node = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == name)
    node.decorator_list = []
    exec(compile(ast.Module(body=[node], type_ignores=[]), str(NODES), "exec"), namespace)
    return namespace[name]


class FakeAIMessage:
    def __init__(self, content: str):
        self.content = content


def test_extract_spu_id_explicit():
    fn = load_function("_extract_spu_id", {"re": re, "AIMessage": FakeAIMessage})
    assert fn("SPU 编号 123", []) == "123"
    assert fn("商品ID：45", []) == "45"


def test_extract_spu_id_numeric_only_after_spu_prompt():
    fn = load_function("_extract_spu_id", {"re": re, "AIMessage": FakeAIMessage})
    assert fn("87", [FakeAIMessage("请提供商品编号（SPU）")]) == "87"
    assert fn("87", [FakeAIMessage("你好")]) is None


def test_auto_resolve_spu_unique_match():
    namespace = {
        "re": re,
        "AgentState": dict,
        "logger": SimpleNamespace(warning=lambda *args, **kwargs: None),
    }
    fn = load_function("_auto_resolve_spu", namespace)
    namespace["_get_last_user_query"] = lambda state: "星耀 X100 评价怎么样"
    namespace["product_search_tool"] = SimpleNamespace(
        invoke=lambda args: {"success": True, "data": [{"id": 8, "name": "星耀 X100"}]}
    )

    assert fn({"mall_token": "t", "user_id": 1}) == "8"


def test_auto_resolve_spu_ambiguous_returns_none():
    namespace = {
        "re": re,
        "AgentState": dict,
        "logger": SimpleNamespace(warning=lambda *args, **kwargs: None),
    }
    fn = load_function("_auto_resolve_spu", namespace)
    namespace["_get_last_user_query"] = lambda state: "手机的评价怎么样"
    namespace["product_search_tool"] = SimpleNamespace(
        invoke=lambda args: {"success": True, "data": [{"id": 1}, {"id": 2}]}
    )

    assert fn({"mall_token": "t", "user_id": 1}) is None