"""FastAPI 应用入口。"""
from __future__ import annotations

import logging
import os
import secrets

from contextlib import asynccontextmanager
from datetime import datetime
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.config import settings
from app.core.database import init_db, SessionLocal
from app.core.exceptions import ok, register_exception_handlers
from app.core.logger import logger
from app.core.security import hash_password
from app.models import User, UserRole

# 同时用标准库 logging 记录关键启动日志，确保 pytest caplog 可以捕获
_seed_logger = logging.getLogger("app.main.seed")


def _seed_admin_once() -> None:
    """首次启动时创建 admin / demo 账号。

    密码策略（避免在源码里硬编码 "123456"）：
      1) 优先读环境变量 DEFAULT_ADMIN_PASSWORD / DEFAULT_DEMO_PASSWORD
      2) 若为空：用 secrets.token_urlsafe(8) 生成随机密码，打印到日志
         （运维首次启动需要从日志里拿到初始密码）
      3) 注意：日志里不会再输出 "123456" 这种弱默认密码
    """
    db = SessionLocal()
    try:
        exist = db.query(User).filter(User.username == "admin").first()
        if exist:
            logger.info("管理员账号 admin 已存在，跳过创建")
            return
        # 读取或生成 admin 密码
        admin_pwd = (os.getenv("DEFAULT_ADMIN_PASSWORD") or "").strip()
        admin_pwd_from_env = bool(admin_pwd)
        if not admin_pwd:
            admin_pwd = secrets.token_urlsafe(8)
        # 演示用户 demo：同策略
        demo_pwd = (os.getenv("DEFAULT_DEMO_PASSWORD") or "").strip()
        demo_pwd_from_env = bool(demo_pwd)
        if not demo_pwd:
            demo_pwd = secrets.token_urlsafe(8)

        admin = User(
            username="admin",
            nickname="超级管理员",
            email="admin@rag.local",
            password_hash=hash_password(admin_pwd),
            role=UserRole.ADMIN,
            is_active=True,
        )
        db.add(admin)
        demo = User(
            username="demo",
            nickname="演示用户",
            email="demo@rag.local",
            password_hash=hash_password(demo_pwd),
            role=UserRole.USER,
            is_active=True,
        )
        db.add(demo)
        db.commit()
        # 同时打 loguru.logger 和 stdlib logging：
        #   - loguru：给控制台 / 日志文件看
        #   - logging：让 pytest caplog 能可靠捕获（避免 loguru→logging 桥接配置）
        if admin_pwd_from_env:
            msg_admin = "初始化账号成功：admin 密码已通过 DEFAULT_ADMIN_PASSWORD 环境变量设置"
        else:
            msg_admin = f"初始化账号成功：admin / {admin_pwd}  （仅首次启动时输出，请妥善保存）"
        if demo_pwd_from_env:
            msg_demo = "演示账号 demo 密码已通过 DEFAULT_DEMO_PASSWORD 环境变量设置"
        else:
            msg_demo = f"演示账号 demo / {demo_pwd}  （仅首次启动时输出）"
        logger.warning(msg_admin)
        logger.warning(msg_demo)
        _seed_logger.warning(msg_admin)
        _seed_logger.warning(msg_demo)
    except Exception as e:  # noqa
        logger.warning(f"初始化种子数据失败: {e}")
        db.rollback()
    finally:
        db.close()


def _ensure_dirs() -> None:
    from pathlib import Path
    for d in [settings.UPLOAD_DIR, settings.CHROMA_PERSIST_DIR, settings.SAMPLE_DATA_DIR]:
        Path(d).mkdir(parents=True, exist_ok=True)


def _warmup_and_seed() -> None:
    """后台线程：预热 RAG 组件 + 加载样例数据，避免阻塞 HTTP 服务监听。"""
    try:
        from app.rag import vector_store, embeddings, llm, chains  # noqa
        logger.info("✅ RAG 组件预热完成 (background)")
    except Exception as e:  # noqa
        logger.warning(f"RAG 组件预热异常(首次问答会重试): {e}")
    try:
        from app.services.sample_data_loader import load_sample_data_if_empty
        load_sample_data_if_empty()
    except Exception as e:  # noqa
        logger.warning(f"样例数据加载跳过: {e}")


@asynccontextmanager
async def lifespan(app: FastAPI):
    import threading
    logger.info(f"⏳ 启动 [{settings.APP_NAME}] env={settings.APP_ENV}")
    _ensure_dirs()
    init_db()
    _seed_admin_once()
    # 把较重的向量化/样例加载放到后台线程，立即开放 HTTP 端口
    threading.Thread(target=_warmup_and_seed, daemon=True).start()
    logger.info(f"🚀 {settings.APP_NAME} HTTP 服务已就绪: http://{settings.APP_HOST}:{settings.APP_PORT}")
    yield
    logger.info("👋 服务关闭")


def create_app() -> FastAPI:
    app = FastAPI(
        title=settings.APP_NAME,
        description="基于 LangChain + ChromaDB + 通义千问 的电商商品RAG智能问答系统",
        version="1.0.0",
        lifespan=lifespan,
        docs_url="/docs",
        redoc_url="/redoc",
    )

    # CORS：允许所有来源（开发阶段），生产应配置白名单
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
        expose_headers=["*"],
    )

    # 全局异常处理
    register_exception_handlers(app)

    # ---------- 根路由 / 健康检查 ----------
    @app.get("/", tags=["Root"])
    async def root():
        return ok({
            "name": settings.APP_NAME,
            "version": "1.0.0",
            "time": datetime.now().isoformat(),
            "docs": "/docs",
            "health": "/health",
        })

    def _probe_mall_mysql() -> str:
        """Mall MySQL 只读桥接探活：连上 + SELECT 1 = UP，否则 DOWN。"""
        url = getattr(settings, "MALL_BRIDGE_MYSQL_URL", "") or ""
        if not url:
            return "DOWN"
        try:
            # 优先用 pymysql driver（和 DATABASE_URL 示例 mysql+pymysql://... 一致）
            from sqlalchemy import create_engine, text
            engine = create_engine(url, pool_pre_ping=True, pool_recycle=60)
            with engine.connect() as conn:
                conn.execute(text("SELECT 1"))
            return "UP"
        except Exception:
            return "DOWN"

    def _probe_mall_redis() -> str:
        """Mall Redis 探活：PING = UP。"""
        url = getattr(settings, "MALL_BRIDGE_REDIS_URL", "") or ""
        if not url:
            return "DOWN"
        try:
            import redis as redis_lib
            client = redis_lib.Redis.from_url(url, socket_connect_timeout=2, socket_timeout=2)
            pong = client.ping()
            return "UP" if pong else "DOWN"
        except Exception:
            return "DOWN"

    def _probe_mall_api() -> str:
        """Mall API 探活：GET /health 返回 200 = UP。不依赖 httpx，优先 urllib 兜底。"""
        base = getattr(settings, "MALL_API_BASE_URL", "") or ""
        if not base:
            return "DOWN"
        health_url = base.rstrip("/") + "/health"
        try:
            # —— 优先 httpx（FastAPI 生态常见）——
            try:
                import httpx  # type: ignore
                with httpx.Client(timeout=3.0) as c:
                    r = c.get(health_url)
                    return "UP" if r.status_code == 200 else "DOWN"
            except ImportError:
                pass
            # —— 否则 urllib.request（标准库，一定存在）——
            import urllib.request
            import urllib.error
            req = urllib.request.Request(health_url, method="GET")
            with urllib.request.urlopen(req, timeout=3) as resp:
                status = getattr(resp, "status", getattr(resp, "getcode", lambda: 200)())
                return "UP" if status == 200 else "DOWN"
        except Exception:
            return "DOWN"

    @app.get("/health", tags=["Root"])
    async def health(request: Request):
        """RAG + Mall 联合健康检查（扁平 JSON 结构，方便 Prometheus 抓取/测试断言）。

        必返回字段（顶层）：
          status      : "UP" 或 "DOWN"（RAG 自身 DB 决定）
          time        : ISO 时间戳
          database    : "UP" / "DOWN"（RAG 自身主库）
          mall_mysql  : "UP" / "DOWN"（Mall 只读桥接 MySQL，未配置也返回 DOWN）
          mall_redis  : "UP" / "DOWN"（Mall Redis db=11，未配置也返回 DOWN）
          mall_api    : "UP" / "DOWN"（Mall API /health 探活，未配置也返回 DOWN）
        """
        # —— 1. RAG 自身主库探活 ——
        database_status = "DOWN"
        try:
            from sqlalchemy import text
            from app.core.database import engine
            with engine.connect() as conn:
                conn.execute(text("SELECT 1"))
            database_status = "UP"
        except Exception as e:  # noqa
            database_status = "DOWN"

        # —— 2. 三项 Mall 探活 ——
        mall_mysql = _probe_mall_mysql()
        mall_redis = _probe_mall_redis()
        mall_api = _probe_mall_api()

        now = datetime.now().isoformat(timespec="seconds")
        body = {
            "status": "UP" if database_status == "UP" else "DOWN",
            "time": now,
            "database": database_status,
            "mall_mysql": mall_mysql,
            "mall_redis": mall_redis,
            "mall_api": mall_api,
        }

        # —— 3. 主库 DOWN 时整体状态码 503 ——
        if body["status"] == "DOWN":
            return JSONResponse(status_code=503, content=body)
        return body

    # ---------- API 路由挂载 ----------
    from app.api import auth, conversation, chat, knowledge_base, admin as admin_api, internal as internal_sync, embed
    API_PREFIX = "/api"
    app.include_router(auth.router, prefix=API_PREFIX, tags=["认证授权"])
    app.include_router(conversation.router, prefix=API_PREFIX, tags=["会话管理"])
    app.include_router(chat.router, prefix=API_PREFIX, tags=["RAG 问答"])
    app.include_router(knowledge_base.router, prefix=API_PREFIX, tags=["知识库管理(管理员)"])
    app.include_router(admin_api.router, prefix=API_PREFIX, tags=["系统管理(管理员)"])
    app.include_router(internal_sync.router, prefix=f"{API_PREFIX}/internal", tags=["Mall 内部同步桥"])
    app.include_router(embed.router, prefix=API_PREFIX, tags=["Embedding"])

    return app


app = create_app()


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host=settings.APP_HOST,
        port=settings.APP_PORT,
        reload=(settings.APP_ENV == "development"),
    )
