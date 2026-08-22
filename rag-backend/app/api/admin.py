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


@router.get("/cache/stats", summary="LLM 缓存统计")
async def cache_stats(current_user: User = Depends(require_admin)):
    """获取 LLM 三层缓存的命中率、延迟等统计指标。"""
    from app.rag.llm_cache import get_cache_metrics
    return ok(get_cache_metrics())


@router.post("/cache/clear", summary="清空 LLM 缓存")
async def cache_clear(
    body: dict = None, current_user: User = Depends(require_admin),
):
    """清空 LLM 语义缓存（L2 Chroma）。
    
    可选参数: {"layer": "l2"|"all"}  默认 "l2"
    L1 Redis 缓存自然过期（TTL），不清除。
    """
    from app.rag.llm_cache import get_llm_cache
    import asyncio
    cache = get_llm_cache()
    count = await cache.invalidate_all()
    return ok({"cleared": count, "message": f"已清空 {count} 条语义缓存"})


@router.post("/cache/invalidate", summary="按商品 ID 精准失效缓存")
async def cache_invalidate(
    body: dict, current_user: User = Depends(require_admin),
):
    """按商品 ID 失效关联的 LLM 缓存。
    
    请求体: {"product_ids": [123, 456]}
    """
    product_ids = body.get("product_ids", []) if body else []
    if not product_ids:
        from app.core.exceptions import bad_request
        return bad_request("product_ids 不能为空")
    from app.rag.llm_cache import get_llm_cache
    cache = get_llm_cache()
    count = await cache.invalidate_by_product_ids([int(p) for p in product_ids])
    return ok({"cleared": count, "product_ids": product_ids})
