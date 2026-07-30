from langchain_core.tools import tool
from typing import Optional

from app.agent.tools.mall_client import call_mall


@tool
def query_cart_tool(
    mall_token: Optional[str] = None,
    user_id: Optional[int] = None,
) -> dict:
    """购物车查询工具，用于查询当前用户的购物车商品列表。

    Args:
        mall_token: 商城用户token，用于身份验证
        user_id: 用户ID
    """
    result = call_mall("GET", "/user/cart", mall_token=mall_token)
    if not result["ok"]:
        return {
            "success": False,
            "type": "error" if result.get("status_code") is None else "list",
            "data": [],
            "count": 0,
            "message": result["message"],
        }

    data = result["data"] or {}
    if data.get("code") in (1, 200):
        items = data.get("data", [])
        normalized = [_normalize_cart_item(item) for item in items]
        total_price = sum(
            float(item.get("sku_price", 0)) * item.get("qty", 0)
            for item in normalized
        )
        return {
            "success": True,
            "type": "list",
            "data": normalized,
            "count": len(normalized),
            "total_price": f"{total_price:.2f}",
            "message": f"购物车共 {len(normalized)} 件商品",
        }
    else:
        return {
            "success": False,
            "type": "list",
            "data": [],
            "count": 0,
            "message": data.get("msg", "购物车查询失败"),
        }


def _normalize_cart_item(item: dict) -> dict:
    """标准化购物车条目数据。"""
    return {
        "id": item.get("id"),
        "spu_name": item.get("spuName", ""),
        "sku_specs": item.get("skuSpecs", ""),
        "sku_price": str(item.get("skuPrice", "0")),
        "qty": item.get("qty", 0),
        "checked": item.get("checked", 0),
        "spu_image": item.get("spuImage", ""),
        "subtotal": f"{float(item.get('skuPrice', 0)) * item.get('qty', 0):.2f}",
    }


@tool
def clear_cart_tool(
    mall_token: Optional[str] = None,
    user_id: Optional[int] = None,
) -> dict:
    """清空购物车工具，用于清空当前用户购物车中的所有商品。

    Args:
        mall_token: 商城用户token，用于身份验证
        user_id: 用户ID
    """
    result = call_mall("DELETE", "/user/cart/clear", mall_token=mall_token)
    if not result["ok"]:
        return {"success": False, "message": result["message"]}

    data = result["data"] or {}
    if data.get("code") in (1, 200):
        return {
            "success": True,
            "message": "购物车已清空",
        }
    else:
        return {
            "success": False,
            "message": data.get("msg", "清空购物车失败"),
        }


@tool
def delete_cart_item_tool(
    cart_item_id: str,
    mall_token: Optional[str] = None,
    user_id: Optional[int] = None,
) -> dict:
    """删除购物车单项工具，用于删除购物车中的指定商品。

    Args:
        cart_item_id: 购物车记录ID
        mall_token: 商城用户token，用于身份验证
        user_id: 用户ID
    """
    result = call_mall("DELETE", f"/user/cart/{cart_item_id}", mall_token=mall_token)
    if not result["ok"]:
        return {"success": False, "cart_item_id": cart_item_id, "message": result["message"]}

    data = result["data"] or {}
    if data.get("code") in (1, 200):
        return {
            "success": True,
            "cart_item_id": cart_item_id,
            "message": "已从购物车中移除该商品",
        }
    else:
        return {
            "success": False,
            "cart_item_id": cart_item_id,
            "message": data.get("msg", "删除购物车商品失败"),
        }


@tool
def update_cart_item_tool(
    cart_item_id: str,
    qty: Optional[int] = None,
    checked: Optional[int] = None,
    mall_token: Optional[str] = None,
    user_id: Optional[int] = None,
) -> dict:
    """修改购物车商品工具，用于修改购物车中商品的数量或勾选状态。

    Args:
        cart_item_id: 购物车记录ID
        qty: 新的数量（不传则不改）
        checked: 是否勾选：1勾选/0不勾选（不传则不改）
        mall_token: 商城用户token，用于身份验证
        user_id: 用户ID
    """
    body = {"id": int(cart_item_id)}
    if qty is not None:
        body["qty"] = qty
    if checked is not None:
        body["checked"] = checked

    result = call_mall("PUT", "/user/cart", mall_token=mall_token, json=body)
    if not result["ok"]:
        return {"success": False, "cart_item_id": cart_item_id, "message": result["message"]}

    data = result["data"] or {}
    if data.get("code") in (1, 200):
        return {
            "success": True,
            "cart_item_id": cart_item_id,
            "qty": qty,
            "checked": checked,
            "message": "购物车商品已更新",
        }
    else:
        return {
            "success": False,
            "cart_item_id": cart_item_id,
            "message": data.get("msg", "更新购物车商品失败"),
        }
