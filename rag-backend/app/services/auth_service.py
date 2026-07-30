"""认证服务层。"""
from __future__ import annotations
from typing import Optional
from datetime import datetime
from sqlalchemy.orm import Session
from fastapi import Request

from app.core.exceptions import BizError
from app.core.security import verify_password, hash_password, create_token_pair, decode_token
from app.models import User, UserRole
from app.schemas import UserRegisterReq, UserLoginReq, ChangePasswordReq, RefreshTokenReq
from app.services.operation_log_service import log_operation


class AuthService:
    def __init__(self, db: Session):
        self.db = db

    # ---------- 注册 ----------
    def register(self, req: UserRegisterReq, request: Optional[Request] = None) -> User:
        # 用户名唯一
        if self.db.query(User).filter(User.username == req.username).first():
            raise BizError("用户名已存在", code=40001)
        if req.email and self.db.query(User).filter(User.email == req.email).first():
            raise BizError("邮箱已被注册", code=40002)
        user = User(
            username=req.username,
            nickname=req.nickname or req.username,
            email=req.email,
            password_hash=hash_password(req.password),
            role=UserRole.USER,  # 禁止注册管理员
            is_active=True,
        )
        self.db.add(user)
        self.db.commit()
        self.db.refresh(user)
        log_operation(self.db, user=user, action="register", detail=f"user_id={user.id}", request=request)
        return user

    # ---------- 登录 ----------
    def login(self, req: UserLoginReq, request: Optional[Request] = None):
        # 支持用户名或邮箱登录
        q = self.db.query(User)
        if "@" in req.username:
            q = q.filter(User.email == req.username)
        else:
            q = q.filter(User.username == req.username)
        user = q.first()
        status = "fail"
        try:
            if not user or not verify_password(req.password, user.password_hash):
                raise BizError("用户名或密码错误", code=40100, http_status=401)
            if not user.is_active:
                raise BizError("账号已被禁用，请联系管理员", code=40103, http_status=401)
            status = "success"
            user.last_login_at = datetime.now()
            self.db.commit()
            access_token, refresh_token = create_token_pair(user.id, user.role.value)
            return {
                "access_token": access_token,
                "refresh_token": refresh_token,
                "token_type": "bearer",
                "user_info": user,
            }
        finally:
            log_operation(self.db, user=user if status == "success" else None,
                          action="login", detail=f"username={req.username} status={status}",
                          request=request, status=status)

    # ---------- 刷新 Token ----------
    def refresh(self, req: RefreshTokenReq):
        payload = decode_token(req.refresh_token)
        if not payload or payload.get("type") != "refresh":
            raise BizError("刷新令牌无效或已过期", code=40110, http_status=401)
        user_id = int(payload["sub"])
        user = self.db.query(User).filter(User.id == user_id).first()
        if not user or not user.is_active:
            raise BizError("用户不存在或已禁用", code=40111, http_status=401)
        access_token, refresh_token = create_token_pair(user.id, user.role.value)
        return {"access_token": access_token, "refresh_token": refresh_token,
                "token_type": "bearer", "user_info": user}

    # ---------- 修改密码 ----------
    def change_password(self, user: User, req: ChangePasswordReq, request: Optional[Request] = None):
        if not verify_password(req.old_password, user.password_hash):
            raise BizError("旧密码错误", code=40010)
        if req.old_password == req.new_password:
            raise BizError("新密码不能与旧密码相同", code=40011)
        user.password_hash = hash_password(req.new_password)
        self.db.commit()
        log_operation(self.db, user=user, action="change_password", request=request)
        return True
