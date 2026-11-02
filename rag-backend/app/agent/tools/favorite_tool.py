from langchain_core.tools import tool
from typing import Optional

from app.agent.tools.mall_client import call_mall
from app.agent.tools._mall_response import unwrap_mall


@tool
def query_favorite_tool(
    mall_token: Optional[str] = None,
    user_id: Optional[int] = None,
) -> dict:
    """收藏夹查询工具，用于查询当前用户的收藏夹商品列表。

    Args:
        mall_token: 商城用户token，用于身份验证
        user_id: 用户ID
    """
    result = call_mall("GET", "/user/favorite", mall_token=mall_token)
    if not result["ok"]:
        return {
            "success": False,
            "type": "list",
            "data": [],
            "count": 0,
            "message": result["message"],
        }

    data, err = unwrap_mall(result, "收藏夹查询失败")
    if err:
        return {
            "success": False,
            "type": "list",
            "data": [],
            "count": 0,
            "message": err,
        }
    items = data.get("data", [])
    normalized = [_normalize_favorite_item(item) for item in items]
    return {
        "success": True,
        "type": "list",
        "data": normalized,
        "count": len(normalized),
        "message": f"收藏夹共 {len(normalized)} 件商品",
    }


def _normalize_favorite_item(item: dict) -> dict:
    """标准化收藏夹条目数据。"""
    return {
        "id": item.get("id"),
        "spu_id": item.get("spuId"),
        "spu_name": item.get("spuName", ""),
        "spu_image": item.get("spuImage", ""),
        "min_price": str(item.get("minPrice", "0")),
        "create_time": item.get("createTime", ""),
    }
