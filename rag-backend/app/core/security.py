"""JWT 认证 + 密码哈希。"""
from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone
from typing import Optional, Tuple, Any
from passlib.context import CryptContext
import jwt

from app.config import settings

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

_logger = logging.getLogger("app.core.security")

# 与 Mall Java 端约定的 claim 字段名（必须和 stellar-mall constant.JwtClaimsConstant 完全一致，大小写敏感）
_STELLAR_CLAIM_EMP_ID = "EMP_ID"
_STELLAR_CLAIM_USER_ID = "USER_ID"
_STELLAR_CLAIM_ROLE = "ROLE"
_STELLAR_CLAIM_NAME = "NAME"

# 内部 tag，标记解析来源，方便上层区分
_TAG_SRC_RAG = "_src"
_SRC_RAG = "rag"
_SRC_STELLAR_ADMIN = "stellar_admin"
_SRC_STELLAR_USER = "stellar_user"


# ---------- 密码 ----------
def hash_password(plain_password: str) -> str:
    return pwd_context.hash(plain_password)


def verify_password(plain_password: str, hashed_password: str) -> bool:
    try:
        return pwd_context.verify(plain_password, hashed_password)
    except Exception:
        return False


# ---------- JWT ----------
def create_access_token(subject: str | int, extra: Optional[dict] = None, expires_delta: Optional[timedelta] = None) -> str:
    expire = datetime.now(timezone.utc) + (expires_delta or timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES))
    payload = {"sub": str(subject), "exp": expire, "type": "access"}
    if extra:
        payload.update(extra)
    return jwt.encode(payload, settings.SECRET_KEY, algorithm=settings.ALGORITHM)


def create_refresh_token(subject: str | int, expires_delta: Optional[timedelta] = None) -> str:
    expire = datetime.now(timezone.utc) + (expires_delta or timedelta(days=settings.REFRESH_TOKEN_EXPIRE_DAYS))
    payload = {"sub": str(subject), "exp": expire, "type": "refresh"}
    return jwt.encode(payload, settings.SECRET_KEY, algorithm=settings.ALGORITHM)


def _decode_with_secret(token: str, secret: str, algorithm: str = settings.ALGORITHM) -> Optional[dict]:
    """用指定密钥解析一次 JWT，失败返回 None（不抛异常）。"""
    try:
        return jwt.decode(
            token,
            secret,
            algorithms=[algorithm],
            options={"require": []},  # exp 可选，缺失也不报错；如果有 exp 仍会自动校验过期
        )
    except jwt.ExpiredSignatureError:
        # 明确过期也视为失败——统一返回 None，由上层决定如何提示
        _logger.debug("JWT 解码失败：已过期")
        return None
    except jwt.InvalidSignatureError:
        _logger.debug("JWT 解码失败：签名不匹配")
        return None
    except Exception as e:  # noqa
        _logger.debug("JWT 解码失败：%s", e)
        return None


def _looks_like_stellar_admin_claims(claims: dict) -> bool:
    """判断解析后的 claims 是否符合 STELLAR ADMIN JWT 规范（必须有 EMP_ID、ROLE 不能是 user）。"""
    return (
        isinstance(claims, dict)
        and _STELLAR_CLAIM_EMP_ID in claims
        and claims.get(_STELLAR_CLAIM_EMP_ID) is not None
        and _STELLAR_CLAIM_ROLE in claims
        and claims.get(_STELLAR_CLAIM_ROLE) != "user"  # 管理端 ROLE ∈ {admin/operator/customer-service/finance}
    )


def _looks_like_stellar_user_claims(claims: dict) -> bool:
    """判断解析后的 claims 是否符合 STELLAR USER JWT 规范（必须有 USER_ID、ROLE=user 或带 USER_ID）。"""
    return (
        isinstance(claims, dict)
        and _STELLAR_CLAIM_USER_ID in claims
        and claims.get(_STELLAR_CLAIM_USER_ID) is not None
    )


def decode_token(token: str) -> Optional[dict]:
    """三段式 JWT 解码——与 Mall Java 端 JJWT (HS256, UTF-8 raw bytes) 100% 互通。

    解码顺序（任一成功即返回）：
      Stage 1. RAG 自有 SECRET_KEY       → 成功则 claims 中加入 _src="rag"
      Stage 2. STELLAR_ADMIN_SECRET_KEY   → 成功 && 含 EMP_ID 则 claims 中加入 _src="stellar_admin"
      Stage 3. STELLAR_USER_SECRET_KEY    → 成功 && 含 USER_ID 则 claims 中加入 _src="stellar_user"

    所有 stage 失败则返回 None（不抛错、不打印堆栈，避免被大量无效 token 刷爆日志）。
    设计原因：
      - 前端同时支持 RAG 登录 token 和 Mall 端 SSO 过来的 token，任一合法都放行
      - 三种 token 用的密钥不同，JWT header 里没有指定密钥 ID（kid），只能依次试
      - 三者算法都是 HS256（对称），顺序上 RAG 自己的 token 放最前面（最常见）提升解析效率
    """
    if not token:
        return None

    # Stage 1：RAG 自有 token（最常见路径）
    claims = _decode_with_secret(token, settings.SECRET_KEY, settings.ALGORITHM)
    if claims is not None:
        # RAG 自签发的 token 有 type=access/refresh + sub，按原逻辑
        claims[_TAG_SRC_RAG] = _SRC_RAG
        return claims

    # Stage 2：Mall 管理端 STELLAR_ADMIN_SECRET_KEY
    try:
        admin_key = settings.STELLAR_ADMIN_SECRET_KEY
    except AttributeError:
        admin_key = None
    if admin_key:
        claims = _decode_with_secret(token, admin_key, settings.ALGORITHM)
        if claims is not None and _looks_like_stellar_admin_claims(claims):
            claims[_TAG_SRC_RAG] = _SRC_STELLAR_ADMIN
            return claims

    # Stage 3：Mall C 端 STELLAR_USER_SECRET_KEY
    try:
        user_key = settings.STELLAR_USER_SECRET_KEY
    except AttributeError:
        user_key = None
    if user_key:
        claims = _decode_with_secret(token, user_key, settings.ALGORITHM)
        if claims is not None and _looks_like_stellar_user_claims(claims):
            claims[_TAG_SRC_RAG] = _SRC_STELLAR_USER
            return claims

    # 三个 stage 都失败：debug 级日志，不影响用户
    _logger.debug("decode_token 三段式校验全部失败，token 已拒绝")
    return None


def create_token_pair(user_id: int, role: str) -> Tuple[str, str]:
    extra = {"role": role}
    return create_access_token(user_id, extra=extra), create_refresh_token(user_id)

