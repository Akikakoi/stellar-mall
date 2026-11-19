"""指代改写开关一致性：意图兜底重分类必须遵守 QUERY_REWRITE_ENABLED。"""
from __future__ import annotations

import ast
from pathlib import Path

NODES = Path(__file__).resolve().parents[1] / "app" / "agent" / "nodes.py"


def load_function(name: str):
    tree = ast.parse(NODES.read_text(encoding="utf-8"))
    node = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == name)
    node.decorator_list = []
    namespace: dict = {}
    exec(compile(ast.Module(body=[node], type_ignores=[]), str(NODES), "exec"), namespace)
    return namespace[name]


def test_reclassify_requires_rewrite_switch_on():
    fn = load_function("_should_reclassify")
    assert fn("other", True, True) is True
    # 开关关掉：即使 other + 有历史，也不得触发改写重分类
    assert fn("other", True, False) is False
    assert fn("other", False, True) is False
    assert fn("product_consult", True, True) is False
