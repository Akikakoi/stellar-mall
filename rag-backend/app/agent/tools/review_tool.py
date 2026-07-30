from langchain_core.tools import tool
from typing import Optional

from app.agent.tools.mall_client import call_mall


@tool
def query_reviews_tool(
    spu_id: str,
    page: int = 1,
    page_size: int = 10,
    mall_token: Optional[str] = None,
    user_id: Optional[int] = None,
) -> dict:
    """商品评价查询工具，用于查询指定商品的用户评价，包括评价内容、评分、回复等。

    Args:
        spu_id: SPU ID，商品的唯一标识
        page: 页码，默认第1页
        page_size: 每页数量，默认10条
        mall_token: 商城用户token（可选）
        user_id: 用户ID（可选）
    """
    params = {"page": page, "pageSize": page_size}
    result = call_mall("GET", f"/user/review/spu/{spu_id}", mall_token=mall_token, params=params)
    if not result["ok"]:
        return {"success": False, "message": result["message"]}

    data = result["data"] or {}
    if data.get("code") in (1, 200):
        inner = data.get("data", {})
        records = inner.get("records", []) if isinstance(inner, dict) else []
        total = inner.get("total", 0) if isinstance(inner, dict) else 0
        reviews = []
        for r in records:
            reviews.append({
                "id": r.get("id"),
                "rating": r.get("rating", 0),
                "content": r.get("content", ""),
                "nickname": r.get("nickname", "") or r.get("username", ""),
                "create_time": r.get("createTime", ""),
                "reply": r.get("reply", ""),
                "reply_time": r.get("replyTime", ""),
            })

        # 同时获取平均评分
        avg_rating = None
        try:
            r2 = call_mall("GET", f"/user/review/spu/{spu_id}/avg-rating", mall_token=mall_token)
            if r2["ok"]:
                d2 = r2["data"] or {}
                if d2.get("code") in (1, 200):
                    avg_rating = d2.get("data")
        except Exception:
            pass

        return {
            "success": True,
            "spu_id": spu_id,
            "data": reviews,
            "count": len(reviews),
            "total": total,
            "avg_rating": str(avg_rating) if avg_rating is not None else None,
            "message": f"共 {total} 条评价" + (f"，平均评分 {avg_rating}" if avg_rating is not None else ""),
        }
    else:
        return {
            "success": False,
            "message": data.get("msg", "评价查询失败"),
        }
