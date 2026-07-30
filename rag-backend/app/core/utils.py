"""通用工具函数集。

当前包含：
- escape_sql_like：将 SQL LIKE 通配符（% _ \\）转义，避免搜索关键字被当作通配符，
  防止弱 LIKE 注入（keyword='%' 匹配全表 / keyword='_x_' 匹配任意单字符）。
"""
from __future__ import annotations


def escape_sql_like(value: str) -> str:
    """SQL LIKE 通配符转义。

    转义规则（顺序严格：先转义自身作为 escape 字符的 '\\'，再转义 '%' 和 '_'）：
        \\  →  \\\\
        %   →  \\%
        _   →  \\_

    使用示例：
        keyword = "100%_正品"
        q.filter(Model.name.like(f"%{escape_sql_like(keyword)}%"))

    :param value: 原始用户输入（字符串），不能为 None
    :return: 经过转义、可以安全拼进 LIKE pattern 的字符串
    :raises TypeError/AttributeError: value 为 None 或不是字符串时（调用方保证类型）
    """
    # 先替换反斜杠本身（反斜杠是 LIKE 的 escape 字符，必须先处理）
    value = value.replace("\\", "\\\\")
    value = value.replace("%", "\\%")
    value = value.replace("_", "\\_")
    return value
