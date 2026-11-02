from langchain_core.tools import tool
from typing import Optional

from app.agent.tools.mall_client import call_mall
from app.agent.tools._mall_response import unwrap_mall


@tool
def cancel_order_tool(
    order_id: str,
    mall_token: Optional[str] = None,
    user_id: Optional[int] = None,
) -> dict:
    """取消订单工具，用于取消待付款或待发货的订单。

    Args:
        order_id: 要取消的订单ID或订单号
        mall_token: 商城用户token，用于身份验证
        user_id: 用户ID
    """
    result = call_mall("POST", f"/user/order/{order_id}/cancel", mall_token=mall_token, json={})
    _, err = unwrap_mall(result, "取消订单失败，请检查订单状态")
    if err:
        return {"success": False, "order_id": order_id, "message": err}

    return {
        "success": True,
        "order_id": order_id,
        "message": "订单已成功取消，库存已恢复",
    }
