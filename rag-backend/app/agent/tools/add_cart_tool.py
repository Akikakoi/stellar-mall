from langchain_core.tools import tool
from typing import Optional

from app.agent.tools.mall_client import call_mall


@tool
def add_to_cart_tool(
    sku_id: str,
    qty: int = 1,
    mall_token: Optional[str] = None,
    user_id: Optional[int] = None,
) -> dict:
    """加入购物车工具，用于将指定商品加入用户的购物车。

    Args:
        sku_id: SKU ID，商品规格的唯一标识
        qty: 购买数量，默认为1
        mall_token: 商城用户token，用于身份验证
        user_id: 用户ID
    """
    body = {"skuId": int(sku_id), "qty": qty}
    result = call_mall("POST", "/user/cart", mall_token=mall_token, json=body)
    if not result["ok"]:
        return {"success": False, "sku_id": sku_id, "qty": qty, "message": result["message"]}

    data = result["data"] or {}
    if data.get("code") in (1, 200):
        return {
            "success": True,
            "sku_id": sku_id,
            "qty": qty,
            "message": "已成功加入购物车",
        }
    else:
        return {
            "success": False,
            "sku_id": sku_id,
            "qty": qty,
            "message": data.get("msg", "加入购物车失败"),
        }
