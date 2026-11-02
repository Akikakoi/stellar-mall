"""商城 API 响应解包：统一 `ok` 与业务 `code` 判定，减少各工具重复分支。"""
from __future__ import annotations

from typing import Any, Dict, Optional, Tuple

MALL_OK_CODES = (1, 200)


def unwrap_mall(result: Dict[str, Any], fallback: str = "商城接口返回失败") -> Tuple[Dict[str, Any], Optional[str]]:
    """返回 (业务数据, 错误信息)；成功时错误信息为 None。

    - 外层调用失败：使用 call_mall 返回的 message；
    - 业务 code 非 1/200：优先使用响应里的 msg，否则用 fallback；
    - 成功：返回 data 本体（仍是外层包裹结构，调用方按需取 data["data"]）。
    """
    if not result.get("ok"):
        return {}, result.get("message") or "商城服务调用失败"
    data = result.get("data") or {}
    if data.get("code") in MALL_OK_CODES:
        return data, None
    return data, data.get("msg") or fallback
