from langchain_core.tools import tool
from typing import Optional

from app.agent.tools.mall_client import call_mall


@tool
def query_order_tool(
    order_id: Optional[str] = None,
    status: Optional[int] = None,
    mall_token: Optional[str] = None,
    user_id: Optional[int] = None,
) -> dict:
    """订单查询工具，用于查询订单状态、物流信息、订单详情、我的订单列表等。

    Args:
        order_id: 订单ID或订单号，查询特定订单时提供
        status: 订单状态过滤：1待付款/3待收货/5已完成/0已取消，不填则查询全部
        mall_token: 商城用户token，用于身份验证
        user_id: 用户ID
    """
    if order_id:
        result = call_mall("GET", f"/user/order/{order_id}", mall_token=mall_token)
        if result["ok"]:
            data = result["data"] or {}
            if data.get("code") in (1, 200) and data.get("data"):
                order = data["data"]
                return {
                    "success": True,
                    "type": "detail",
                    "data": _normalize_order(order),
                    "message": "订单查询成功",
                }
            else:
                return {
                    "success": False,
                    "type": "detail",
                    "data": None,
                    "message": data.get("msg", "订单查询失败或订单不存在"),
                }
        # 详情查询失败时降级查列表

    params = {}
    if status is not None:
        params["status"] = status

    result = call_mall("GET", "/user/order/list", mall_token=mall_token, params=params)
    if not result["ok"]:
        return {
            "success": False,
            "type": "error" if result.get("status_code") is None else "list",
            "data": None if result.get("status_code") is None else [],
            "count": 0,
            "message": result["message"],
        }

    data = result["data"] or {}
    if data.get("code") in (1, 200):
        orders = data.get("data", [])
        normalized = [_normalize_order(o) for o in orders]
        return {
            "success": True,
            "type": "list",
            "data": normalized,
            "count": len(normalized),
            "message": f"查询到 {len(normalized)} 个订单",
        }
    else:
        return {
            "success": False,
            "type": "list",
            "data": [],
            "count": 0,
            "message": data.get("msg", "订单列表查询失败"),
        }


def _normalize_order(order: dict) -> dict:
    status_map = {
        "PENDING": "待付款",
        "PAID": "待发货/已付款",
        "CANCELLED": "已取消",
        "COMPLETED": "已完成",
    }
    status_code = order.get("statusCode")
    status_code_map = {
        0: "已取消",
        1: "待付款",
        2: "待发货",
        3: "待收货",
        4: "待评价",
        5: "已完成",
        6: "退款中",
    }

    status_text = status_map.get(order.get("status", ""), order.get("status", "未知"))
    if status_code and status_code in status_code_map:
        status_text = status_code_map[status_code]

    items = []
    for item in order.get("items", []) or []:
        items.append({
            "name": item.get("spuName") or item.get("name", ""),
            "sku_spec": item.get("skuSpec") or item.get("spec", ""),
            "price": str(item.get("price", "0")),
            "quantity": item.get("quantity", 0),
        })

    return {
        "id": order.get("id"),
        "order_no": order.get("orderNo"),
        "status": order.get("status"),
        "status_text": status_text,
        "status_code": status_code,
        "total_amount": str(order.get("totalAmount", "0")),
        "pay_amount": str(order.get("payAmount", "0")),
        "address": order.get("address", ""),
        "create_time": order.get("createTime", ""),
        "items": items,
    }
