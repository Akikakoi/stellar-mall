from langchain_core.tools import tool
from typing import Optional

from app.agent.tools.mall_client import call_mall
from app.core.logger import logger


@tool
def product_search_tool(
    query: str,
    page: int = 1,
    page_size: int = 10,
    category: Optional[str] = None,
    mall_token: Optional[str] = None,
    user_id: Optional[int] = None,
) -> dict:
    """商品搜索工具，用于在商城中搜索商品，支持按名称、分类搜索。

    Args:
        query: 搜索关键词，商品名称或分类名称
        page: 页码，默认第1页
        page_size: 每页数量，默认10条
        category: 商品分类名称（如"平板电脑""笔记本电脑""智能穿戴"），可选
        mall_token: 商城用户token（可选）
        user_id: 用户ID（可选）
    """
    params = {
        "name": query,
        "page": page,
        "pageSize": page_size,
    }

    # 如果传了分类名，尝试解析出 categoryId
    if category:
        cat_info = _resolve_category(category, mall_token)
        if cat_info:
            params["categoryId"] = cat_info["id"]
            # 分类名已经精确到搜索参数里，name 可以放宽
            if not query or query == category:
                params["name"] = ""

    result = call_mall("GET", "/user/spu/page", mall_token=mall_token, params=params)
    logger.info(f"[product_search] name='{query}' category='{category}' total={result.get('data',{}).get('data',{}).get('total',0) if result.get('ok') else 'err'}")

    # 兜底：如果没传 category 且搜索结果为空，尝试把 query 当作分类名再搜一次
    if not category and result.get("ok"):
        data = result.get("data") or {}
        inner = data.get("data", {}) if isinstance(data, dict) else {}
        if inner.get("total", 0) == 0:
            cat_info = _resolve_category(query, mall_token)
            logger.info(f"[product_search] fallback category resolve: '{query}' -> {cat_info}")
            if cat_info:
                params["categoryId"] = cat_info["id"]
                params["name"] = ""
                result = call_mall("GET", "/user/spu/page", mall_token=mall_token, params=params)

    if not result["ok"]:
        return {"success": False, "message": result["message"]}

    data = result["data"] or {}
    if data.get("code") in (1, 200):
        inner = data.get("data", {})
        records = inner.get("records", []) if isinstance(inner, dict) else []
        total = inner.get("total", 0) if isinstance(inner, dict) else 0
        normalized = [_normalize_spu(item) for item in records]
        return {
            "success": True,
            "query": query,
            "category": category,
            "data": normalized,
            "count": len(normalized),
            "total": total,
            "page": page,
            "message": f"搜索「{query}」共找到 {total} 件商品（当前第{page}页，显示{len(normalized)}件）",
        }
    else:
        return {
            "success": False,
            "message": data.get("msg", "商品搜索失败"),
        }


def _resolve_category(category_name: str, mall_token: Optional[str] = None) -> Optional[dict]:
    """根据分类名称查找分类 ID 和级别信息（使用 C 端接口）。
    支持从较长的查询文本中提取分类关键词。
    """
    try:
        result = call_mall("GET", "/user/category/list", mall_token=mall_token)
        if not result["ok"]:
            return None
        data = result["data"] or {}
        if data.get("code") in (1, 200):
            categories = data.get("data", [])
            if not isinstance(categories, list):
                return None

            def _flatten(cats: list) -> list:
                flat = []
                for c in cats:
                    flat.append({"id": c.get("id"), "name": c.get("name"), "level": c.get("level")})
                    children = c.get("children", [])
                    if children:
                        flat.extend(_flatten(children))
                return flat

            flat_list = _flatten(categories)
            # 按分类名长度降序排列，优先匹配更长的分类名（如"平板电脑"优先于"电脑"）
            flat_list.sort(key=lambda x: len(x["name"]), reverse=True)

            # 先精确匹配
            for cat in flat_list:
                if cat["name"] == category_name:
                    return cat
            # 再检查 query 中是否包含分类名（从长到短）
            for cat in flat_list:
                if cat["name"] in category_name:
                    return cat
            # 最后：分类名包含在 query 的部分匹配
            for cat in flat_list:
                if category_name in cat["name"]:
                    return cat
    except Exception:
        pass
    return None


def _normalize_spu(item: dict) -> dict:
    """标准化商品条目数据。"""
    return {
        "id": item.get("id"),
        "name": item.get("name", ""),
        "min_price": str(item.get("minPrice", "0")),
        "max_price": str(item.get("maxPrice", "0")),
        "main_image": item.get("mainImage", ""),
        "sale_count": item.get("saleCount", 0),
        "total_stock": item.get("totalStock", 0),
        "brand": item.get("brand", ""),
    }
