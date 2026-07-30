from langchain_core.tools import tool
from typing import Optional

from app.agent.tools.mall_client import call_mall


@tool
def confirm_receipt_tool(
    order_id: str,
    mall_token: Optional[str] = None,
    user_id: Optional[int] = None,
) -> dict:
    """确认收货工具，用于确认已收到商品，将订单状态从待收货变为已完成。

    Args:
        order_id: 要确认收货的订单ID或订单号
        mall_token: 商城用户token，用于身份验证
        user_id: 用户ID
    """
    result = call_mall("POST", f"/user/order/{order_id}/confirm", mall_token=mall_token, json={})
    if not result["ok"]:
        return {"success": False, "order_id": order_id, "message": result["message"]}

    data = result["data"] or {}
    if data.get("code") in (1, 200):
        return {
            "success": True,
            "order_id": order_id,
            "message": "已确认收货，订单已完成",
        }
    else:
        return {
            "success": False,
            "order_id": order_id,
            "message": data.get("msg", "确认收货失败，请检查订单状态"),
        }
