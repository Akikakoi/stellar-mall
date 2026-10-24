"""AI 欢迎页动态提问示例路由。"""
from __future__ import annotations

from fastapi import APIRouter, Depends, Query, Request

from app.core.exceptions import ok
from app.core.rate_limiter import rate_limit
from app.dependencies import get_current_user
from app.models import User

router = APIRouter(prefix="/suggestions")


@router.get("")
@rate_limit(max_calls=30)
async def suggestions(
    request: Request,
    limit: int = Query(8, ge=4, le=10, description="返回的示例数量（含 2 条服务类问题）"),
    current_user: User = Depends(get_current_user),
):
    """返回基于真实商品库动态生成的提问示例。

    商品池与 LLM 问题池由 suggestion_service 统一维护，
    本接口只负责鉴权、取 Mall token 和随机抽样。
    """
    from app.services.suggestion_service import get_suggestions

    mall_token = _extract_mall_token(request)
    data = await get_suggestions(limit=limit, mall_token=mall_token)
    return ok(data)


def _extract_mall_token(request: Request) -> str | None:
    """从请求头提取 Mall 用户 token，供调用 C 端商品接口使用。"""
    auth = request.headers.get("authorization", "")
    if auth.startswith("Bearer "):
        return auth[7:].strip() or None
    return request.headers.get("stellar-token") or request.headers.get("authentication")
