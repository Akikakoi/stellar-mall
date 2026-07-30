"""FastAPI 依赖注入：当前用户、权限校验。"""
from __future__ import annotations

from typing import Optional

from fastapi import Depends, Request
from fastapi.security import OAuth2PasswordBearer
from sqlalchemy.orm import Session

from app.config import settings
from app.core.database import get_db
from app.core.exceptions import BizError
from app.core.security import decode_token
from app.models import User, UserRole
from app.services.operation_log_service import log_operation  # 懒用

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/auth/login", auto_error=False)

# 标记 decode_token 返回的来源 tag（见 core.security）
_TAG_SRC = "_src"
_SRC_RAG = "rag"
_SRC_STELLAR_ADMIN = "stellar_admin"
_SRC_STELLAR_USER = "stellar_user"

# Mall ↔ RAG claim 映射（与 stellar-mall JwtClaimsConstant 完全一致）
_CLAIM_EMP_ID = "EMP_ID"
_CLAIM_USER_ID = "USER_ID"
_CLAIM_ROLE = "ROLE"
_CLAIM_NAME = "NAME"


def _parse_token(token: Optional[str]) -> Optional[dict]:
    """扩展原 parse：Mall 签发的 STELLAR token 没有 type=access，不按原来的 type 校验。"""
    if not token:
        return None
    payload = decode_token(token)
    if not payload:
        return None
    src = payload.get(_TAG_SRC)
    # —— RAG 自签发 token：仍然要求 type=access（保持原行为，避免把 refresh 当 access） ——
    if src == _SRC_RAG:
        if payload.get("type") != "access":
            return None
    # —— Mall 签发 token：只要 security 层三段式通过 + claims 校验通过，就认为合法
    elif src in (_SRC_STELLAR_ADMIN, _SRC_STELLAR_USER):
        pass
    # 其他未知来源：拒绝
    else:
        return None
    return payload


def _stellar_role_to_rag_role(stellar_role: str, src: str) -> UserRole:
    """Mall ROLE 字符串 → RAG UserRole 映射。
    Mall: admin/operator/customer-service/finance/user
    RAG: 只有 ADMIN / USER 两种
    策略：管理端 stellar_admin token → ADMIN；
          C 端 stellar_user token 中 ROLE=admin 的管理员也视为 ADMIN；
          其余均为 USER。
    """
    if src == _SRC_STELLAR_ADMIN:
        return UserRole.ADMIN
    if src == _SRC_STELLAR_USER:
        # C 端用户也可能是管理员（admin 账户通过用户登录页登录）
        if stellar_role == "admin":
            return UserRole.ADMIN
        return UserRole.USER
    # fallback
    if stellar_role == "admin":
        return UserRole.ADMIN
    return UserRole.USER


def _build_virtual_user(stellar_id: int, nickname: Optional[str], username_prefix: str, rag_role: UserRole) -> User:
    """构造一个不入库的虚拟 User 实例（给 Mall 跨端 SSO 用）。

    ⚠️ 不会写入 RAG 的 users 表，也不需要 BCrypt 哈希：
       认证已经通过三段式 JWT 校验完成，这里只要给上层 RBAC / 日志 / 上下文使用即可。
    """
    return User(
        id=None,  # id=None 表示虚拟（如果入库会自增；这里故意不入库）
        username=f"{username_prefix}_{stellar_id}",
        email=None,
        password_hash="",  # 不使用密码登录，留空
        nickname=nickname or f"{username_prefix}-{stellar_id}",
        role=rag_role,
        is_active=True,
    )


def _ensure_stellar_shadow_user_in_db(
    db: Session,
    username: str,
    nickname: Optional[str],
    rag_role: UserRole,
) -> Optional[User]:
    """把 Mall 跨端登录的用户同步写入 RAG users 表（返回真实 id 的 User）。

    写入失败（users 表不存在、DB 异常、字段缺值等）时返回 None，调用方回退到虚拟用户。

    为什么要写入 DB？
      Conversation 表 user_id 列是 FK(users.id) 且 nullable=False。如果跨端用户只在内存里
      构造一个 id=None 的虚拟 User，所有对话/消息/操作日志的 ORM 写入都会失败。
      所以对 Mall 跨端登录过来的用户，我们在 RAG 侧维护一行「影子用户」即可，
      username 固定 stellar_emp_<EMP_ID> / stellar_user_<USER_ID>（唯一索引可保证去重）。
    """
    try:
        # 先尝试 INSERT，然后再查一遍——这样哪怕之前有并发插入也能读回真实 id
        new_user = User(
            username=username,
            email=None,
            # 密码永远不使用：跨端认证走 JWT 三段式，不走密码登录
            password_hash="*stellar-sso-shadow-user-no-password*",
            nickname=nickname or username,
            role=rag_role,
            is_active=True,
        )
        try:
            db.add(new_user)
            db.commit()
            db.refresh(new_user)
            return new_user
        except Exception:  # noqa: 唯一键冲突（之前已插入过）/ DB 异常 → 回滚再查
            db.rollback()
            existing = db.query(User).filter(User.username == username).first()
            if existing is not None and existing.is_active:
                return existing
            return None
    except Exception:  # noqa: 任何级别的异常（连表都不存在等）都静默不影响主流程
        return None


def _user_from_stellar(payload: dict, db: Session) -> Optional[User]:
    """按 Mall JWT claims 返回一个 User（优先查 RAG 表 → 没查到则写入一行影子用户拿真实 id → 实在不行虚拟用户兜底）。"""
    src = payload.get(_TAG_SRC)
    if src == _SRC_STELLAR_ADMIN:
        try:
            emp_id = int(payload[_CLAIM_EMP_ID])
        except (TypeError, ValueError, KeyError):
            return None
        rag_role = _stellar_role_to_rag_role(payload.get(_CLAIM_ROLE, "admin"), src)
        nickname = payload.get(_CLAIM_NAME)
        username = f"stellar_emp_{emp_id}"
        existing: Optional[User] = None
        try:
            existing = db.query(User).filter(User.username == username).first()
        except Exception:  # noqa: users 表不存在 / DB 挂了 → 忽略
            existing = None
        if existing is not None and existing.is_active:
            return existing
        # 没查到：尝试写入影子用户（拿真实 id → FK 关联可用）
        persisted = _ensure_stellar_shadow_user_in_db(db, username, nickname, rag_role)
        if persisted is not None:
            return persisted
        # 最后兜底：虚拟用户（id=None；后续任何需要 FK 的 ORM 写入会失败，但 JWT 层至少能通过鉴权）
        return _build_virtual_user(emp_id, nickname, "stellar_emp", rag_role)

    if src == _SRC_STELLAR_USER:
        try:
            user_id = int(payload[_CLAIM_USER_ID])
        except (TypeError, ValueError, KeyError):
            return None
        rag_role = _stellar_role_to_rag_role(payload.get(_CLAIM_ROLE, "user"), src)
        nickname = payload.get(_CLAIM_NAME)
        username = f"stellar_user_{user_id}"
        existing: Optional[User] = None
        try:
            existing = db.query(User).filter(User.username == username).first()
        except Exception:  # noqa
            existing = None
        if existing is not None and existing.is_active:
            return existing
        persisted = _ensure_stellar_shadow_user_in_db(db, username, nickname, rag_role)
        if persisted is not None:
            return persisted
        return _build_virtual_user(user_id, nickname, "stellar_user", rag_role)

    return None


async def get_current_user(
    request: Request,
    token: Optional[str] = Depends(oauth2_scheme),
    db: Session = Depends(get_db),
) -> User:
    """若未携带合法 token，抛 401；否则返回当前用户。

    兼容：
      1) RAG 自签发 JWT → 查 RAG users 表（原行为 100% 兼容）
      2) Mall 管理端 STELLAR_ADMIN_SECRET_KEY 签发 JWT（含 EMP_ID）→ 视作 ADMIN 用户
      3) Mall C 端 STELLAR_USER_SECRET_KEY 签发 JWT（含 USER_ID）→ 视作 USER 用户
    """
    # 若 OAuth2 没拿到（自定义 Header），试试 X-Token / Authorization Bearer
    if not token:
        token = request.headers.get("Authorization", "").replace("Bearer ", "").strip() or None
    payload = _parse_token(token)
    if not payload:
        raise BizError("未登录或登录已过期", code=40101, http_status=401)

    # Path A：Mall 跨端 SSO token
    src = payload.get(_TAG_SRC)
    if src in (_SRC_STELLAR_ADMIN, _SRC_STELLAR_USER):
        user = _user_from_stellar(payload, db)
        if user is None:
            raise BizError("跨端登录 claims 异常", code=40103, http_status=401)
        request.state.current_user = user
        return user

    # Path B：RAG 自签发 token（原逻辑）
    try:
        user_id = int(payload["sub"])
    except (TypeError, ValueError, KeyError):
        raise BizError("JWT 缺少 sub 字段", code=40102, http_status=401)
    user = db.query(User).filter(User.id == user_id).first()
    if not user or not user.is_active:
        raise BizError("用户不存在或已被禁用", code=40102, http_status=401)
    request.state.current_user = user
    return user


async def get_current_user_opt(
    request: Request,
    token: Optional[str] = Depends(oauth2_scheme),
    db: Session = Depends(get_db),
) -> Optional[User]:
    """可选的当前用户，不强制登录。"""
    try:
        return await get_current_user(request, token, db)
    except BizError:
        return None


class RequireRole:
    """RBAC 依赖：必须属于指定角色之一。"""

    def __init__(self, *roles: UserRole):
        self.allowed = set(roles)

    async def __call__(self, current_user: User = Depends(get_current_user)) -> User:
        if current_user.role not in self.allowed:
            raise BizError("权限不足，需要管理员角色", code=40301, http_status=403)
        return current_user


# 快捷依赖
require_admin = RequireRole(UserRole.ADMIN)
require_any_user = get_current_user

