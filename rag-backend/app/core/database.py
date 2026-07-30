"""SQLAlchemy 引擎与会话管理。"""
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base, Session
from sqlalchemy.pool import StaticPool

from app.config import settings

# SQLite 需要 check_same_thread=False + StaticPool，保证多线程/异步安全
connect_args = {}
kwargs = {}
if settings.DATABASE_URL.startswith("sqlite"):
    connect_args["check_same_thread"] = False
    kwargs["poolclass"] = StaticPool

engine = create_engine(
    settings.DATABASE_URL,
    connect_args=connect_args,
    echo=(settings.APP_ENV == "development"),
    future=True,
    **kwargs,
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine, future=True)

Base = declarative_base()


def get_db() -> Session:
    """FastAPI 依赖注入用，返回 DB Session，确保使用后关闭。"""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()


def init_db() -> None:
    """创建所有表（仅开发环境，首次启动调用）。"""
    # 先导入所有 model，确保被 Base.metadata 识别
    from app.models import user, conversation, message, kb_document, operation_log  # noqa
    Base.metadata.create_all(bind=engine)
