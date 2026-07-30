"""管理员系统路由：仪表盘、系统设置、操作日志等（先占位）。"""
from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.exceptions import ok
from app.dependencies import require_admin
from app.models import User

router = APIRouter(prefix="/admin")


@router.get("/dashboard")
async def dashboard(current_user: User = Depends(require_admin), db: Session = Depends(get_db)):
    from app.services.admin_service import AdminService
    return ok(AdminService(db).dashboard())


@router.get("/logs")
async def list_logs(
    page: int = 1, page_size: int = 50, action: str = "",
    current_user: User = Depends(require_admin), db: Session = Depends(get_db),
):
    from app.services.admin_service import AdminService
    return ok(AdminService(db).list_logs(page, page_size, action=action))


@router.get("/settings")
async def get_settings(current_user: User = Depends(require_admin)):
    from app.services.admin_service import AdminService
    return ok(AdminService.get_settings_from_env())


@router.post("/settings")
async def update_settings(
    body: dict, current_user: User = Depends(require_admin),
    db: Session = Depends(get_db),
):
    from app.services.admin_service import AdminService
    from app.schemas import SystemSettingsReq
    req = SystemSettingsReq(**(body or {}))
    AdminService.apply_runtime_settings(req)
    return ok(message="已更新当前运行时配置")


@router.get("/users")
async def list_users(
    page: int = 1, page_size: int = 20, keyword: str = "",
    current_user: User = Depends(require_admin), db: Session = Depends(get_db),
):
    from app.services.admin_service import AdminService
    return ok(AdminService(db).list_users(page, page_size, keyword))
