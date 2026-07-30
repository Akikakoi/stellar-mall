"""操作日志服务：统一记录管理员和关键用户行为。"""
from __future__ import annotations
from typing import Optional
from sqlalchemy.orm import Session
from fastapi import Request

from app.models import OperationLog, User
from app.core.logger import logger


def _ip_of(request: Optional[Request]) -> Optional[str]:
    if not request:
        return None
    # 优先取 X-Forwarded-For
    forwarded = request.headers.get("X-Forwarded-For")
    if forwarded:
        return forwarded.split(",")[0].strip()
    if request.client:
        return request.client.host
    return None


def log_operation(
    db: Session,
    user: Optional[User],
    action: str,
    resource: Optional[str] = None,
    detail: Optional[str] = None,
    request: Optional[Request] = None,
    status: str = "success",
) -> None:
    """写一条操作日志。非关键路径，吞掉异常避免影响主流程。"""
    try:
        log = OperationLog(
            user_id=user.id if user else None,
            username=user.username if user else None,
            action=action,
            resource=resource,
            detail=detail,
            ip=_ip_of(request),
            status=status,
        )
        db.add(log)
        db.commit()
    except Exception as e:  # noqa
        logger.warning(f"写操作日志失败: {e}")
