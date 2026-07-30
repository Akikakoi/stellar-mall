"""认证授权路由。"""
from __future__ import annotations

from fastapi import APIRouter, Depends, Request
from sqlalchemy.orm import Session

from app.config import settings
from app.core.database import get_db
from app.core.exceptions import ok
from app.core.rate_limiter import rate_limit
from app.dependencies import get_current_user
from app.models import User
from app.schemas import (
    UserRegisterReq,
    UserLoginReq,
    UserLoginResp,
    ChangePasswordReq,
    RefreshTokenReq,
    UserInfo,
)
from app.services.auth_service import AuthService

router = APIRouter(prefix="/auth")


@router.post("/register", response_model_exclude_none=True)
@rate_limit(max_calls=settings.RATE_LIMIT_PER_MINUTE_AUTH)
async def register(req: UserRegisterReq, request: Request, db: Session = Depends(get_db)):
    svc = AuthService(db)
    user = svc.register(req, request=request)
    return ok(UserInfo.model_validate(user))


@router.post("/login")
@rate_limit(max_calls=settings.RATE_LIMIT_PER_MINUTE_AUTH)
async def login(req: UserLoginReq, request: Request, db: Session = Depends(get_db)):
    svc = AuthService(db)
    result = svc.login(req, request=request)
    # 统一封装：user_info 用 UserInfo 序列化
    result["user_info"] = UserInfo.model_validate(result["user_info"]).model_dump()
    return ok(result)


@router.post("/refresh")
async def refresh(req: RefreshTokenReq, db: Session = Depends(get_db)):
    svc = AuthService(db)
    r = svc.refresh(req)
    r["user_info"] = UserInfo.model_validate(r["user_info"]).model_dump()
    return ok(r)


@router.get("/me")
async def me(current_user: User = Depends(get_current_user)):
    return ok(UserInfo.model_validate(current_user))


@router.post("/change-password")
@rate_limit(max_calls=20)
async def change_password(
    req: ChangePasswordReq,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    svc = AuthService(db)
    svc.change_password(current_user, req, request=request)
    return ok(message="密码修改成功")
