"""应用配置，使用 pydantic-settings 从 .env 加载。"""
from pydantic_settings import BaseSettings, SettingsConfigDict
from typing import List
from pathlib import Path
import re


# 常见弱密钥关键词（不区分大小写）——命中任一值就算弱密钥，必须打 WARNING 日志
_WEAK_KEY_PATTERNS = [
    r"change[-_ ]?me",
    r"please[-_ ]?change",
    r"secret[-_ ]?key",
    r"default[-_ ]?(secret|key)",
    r"^\*{1,8}$",
    r"^x+$",
    r"^test[-_ ]?only",
    r"^123456$",
    r"^password$",
    r"admin123",
    r"qwerty",
]
_WEAK_KEY_MIN_LEN = 24  # 低于这个长度也被认为弱


def _validate_secret_key_strength(secret_key: str, source: str = "Settings") -> None:
    """校验 SECRET_KEY 的强度，弱值打印 WARNING 日志但不抛错（演示项目仍可启动）。

    判定条件（任一命中即弱）：
      1. 长度 < 24
      2. 命中 _WEAK_KEY_PATTERNS 中任一正则（典型占位符 / 默认值 / 常见弱口令）

    :param secret_key: 从配置或环境加载到的 SECRET_KEY 原值
    :param source: 标记来源，方便日志定位（Settings / 手动调用的 TmpSettings 等）

    注：这里使用标准库 logging，而不是 loguru，以确保 pytest 的 caplog
        能可靠捕获日志记录（loguru 需要额外的 handler 才能桥接到 logging）。
    """
    import logging

    _logger = logging.getLogger("app.config")

    if not isinstance(secret_key, str):
        _logger.warning("[%s] SECRET_KEY 不是字符串类型，实际类型 %s",
                        source, type(secret_key).__name__)
        return

    reason: str | None = None
    if len(secret_key) < _WEAK_KEY_MIN_LEN:
        reason = f"长度仅 {len(secret_key)}，建议 >= {_WEAK_KEY_MIN_LEN}"
    else:
        for pat in _WEAK_KEY_PATTERNS:
            if re.search(pat, secret_key, flags=re.IGNORECASE):
                reason = f"命中弱密钥模式 /{pat}/"
                break
    if reason:
        _logger.warning(
            "[%s] SECRET_KEY 为弱密钥（%s），"
            "请立即修改 .env / 环境变量 SECRET_KEY 为一个随机强字符串，"
            "例如: python -c 'import secrets; print(secrets.token_urlsafe(48))'",
            source, reason,
        )



class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # 系统
    APP_NAME: str = "电商RAG问答系统"
    APP_ENV: str = "development"
    APP_HOST: str = "0.0.0.0"
    APP_PORT: int = 8000
    SECRET_KEY: str = "change-me"
    ALGORITHM: str = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 60 * 24
    REFRESH_TOKEN_EXPIRE_DAYS: int = 7

    # 数据库
    DATABASE_URL: str = "sqlite:///./data/sqlite/app.db"

    # 通义千问
    DASHSCOPE_API_KEY: str = ""
    # 业务空间专属域名（阿里云百炼「专属业务空间」sk-ws- 开头的 Key 必须走下面自定义域名）
    #   OPENAI_COMPATIBLE_BASE_URL：LangChain ChatOpenAI / ChatTongyi 兼容模式 endpoint
    #   DASHSCOPE_BASE_URL：百炼原生 SDK / DashScopeEmbeddings endpoint（包含 /api/v1）
    #   DASHSCOPE_WORKSPACE_ID / DASHSCOPE_API_HOST：可选，排查问题时留痕
    OPENAI_COMPATIBLE_BASE_URL: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    DASHSCOPE_BASE_URL: str = "https://dashscope.aliyuncs.com/api/v1"
    DASHSCOPE_WORKSPACE_ID: str = ""
    DASHSCOPE_API_HOST: str = "dashscope.aliyuncs.com"
    LLM_MODEL_NAME: str = "qwen-plus"
    LLM_TEMPERATURE: float = 0.3
    LLM_MAX_TOKENS: int = 2048

    # Embedding
    EMBEDDING_MODEL_NAME: str = "BAAI/bge-large-zh-v1.5"
    EMBEDDING_DIMENSION: int = 1024
    DASHSCOPE_EMBEDDING_MODEL: str = "text-embedding-v2"

    # Chroma
    CHROMA_PERSIST_DIR: str = "./data/chroma"
    CHROMA_COLLECTION_NAME: str = "ecommerce_knowledge_base"

    # 上传
    UPLOAD_DIR: str = "./data/uploads"
    MAX_UPLOAD_SIZE: int = 20 * 1024 * 1024
    ALLOWED_EXTENSIONS: str = ".pdf,.docx,.txt,.md,.csv"

    @property
    def ALLOWED_EXT_LIST(self) -> List[str]:
        return [e.strip().lower() for e in self.ALLOWED_EXTENSIONS.split(",") if e.strip()]

    # RAG
    RETRIEVER_TOP_K: int = 20
    RERANK_TOP_K: int = 5
    SIMILARITY_THRESHOLD: float = 0.25   # 降到 0.25：自然语言问句 vs 结构化 spec 的语义相似度本身偏低
    CHUNK_SIZE: int = 1024
    CHUNK_OVERLAP: int = 200
    QUERY_REWRITE_ENABLED: bool = True

    # 缓存
    QUERY_CACHE_MAXSIZE: int = 128
    QUERY_CACHE_SIM_THRESHOLD: float = 0.97
    QUERY_CACHE_TTL_SECONDS: int = 300  # FAQ 查询缓存默认 5 分钟过期

    # 限流
    RATE_LIMIT_PER_MINUTE_AUTH: int = 60
    RATE_LIMIT_PER_MINUTE_CHAT: int = 15

    SAMPLE_DATA_DIR: str = "./data/sample_data"

    # Agent 智能体
    AGENT_ENABLED: bool = True

    # ========================================================================
    # 星耀商城 stellar-mall 桥接配置（RAG ↔ Mall 跨端互通关键！）
    # ========================================================================
    # 1. 共享 JWT 密钥（⚠️ 两端必须 100% 一致！）
    #    生产请替换成：python -c "import secrets; print(secrets.token_urlsafe(48))"
    #    STELLAR_ADMIN_SECRET_KEY：Mall 管理端 /admin/** 签发 token 用
    #    STELLAR_USER_SECRET_KEY：Mall C 端  /user/** 签发 token 用
    STELLAR_ADMIN_SECRET_KEY: str = "StellarMall_Admin_SecretKey_2024_Strong_32bytes_!@#"
    STELLAR_USER_SECRET_KEY: str  = "StellarMall_User_SecretKey_2024_Strong_32bytes_$%^"

    # 2. Mall → RAG 知识库同步内部接口共享密钥
    #    （Mall 端 stellar.rag.internal-sync-secret 配置必须和这个相同）
    STELLAR_RAG_INTERNAL_SYNC_SECRET: str = "uEvV_FYrloC6T_R8vYNZcHlt07xL-K14Vh-2-VBpMqrzylSX4BbouNZQwv90QpJL"

    # 3. RAG 只读桥接（给健康检查 + 后续只读业务查询用）
    #    - MALL_BRIDGE_MYSQL_URL：stellar_ro 只读账号 Pymysql URL（没配则 health mall_mysql 显示 not configured）
    #      例：mysql+pymysql://stellar_ro:123456@127.0.0.1:3306/stellar_mall?charset=utf8mb4
    #    - MALL_BRIDGE_REDIS_URL：商城 Redis（db=11）URL，没配则 health mall_redis 显示 not configured
    #      例：redis://127.0.0.1:6379/11
    #    - MALL_API_BASE_URL：商城 API 根地址（health mall_api 探活用 / Agent 工具调用）
    MALL_BRIDGE_MYSQL_URL: str = ""
    MALL_BRIDGE_REDIS_URL: str = ""
    MALL_API_BASE_URL: str = "http://127.0.0.1:8082"

    # Agent 工具调用 Mall Java 后端的超时/重试/熔断保护
    MALL_API_TIMEOUT_SECONDS: float = 10.0
    MALL_API_MAX_RETRIES: int = 2
    MALL_API_CIRCUIT_BREAKER_FAILURE_THRESHOLD: int = 5
    MALL_API_CIRCUIT_BREAKER_RECOVERY_SECONDS: int = 30

    @property
    def BASE_DIR(self) -> Path:
        return Path(__file__).resolve().parent.parent

    def model_post_init(self, __context) -> None:
        """模型构建完成后的钩子：装配正式 logger + 检测弱密钥 + DashScope Key 为空报警。"""
        # 先装配正式 logger（之前是 logger.py 里的最小 stdout 配置），再打日志
        from app.core.logger import setup_logger
        setup_logger(self)
        # —— 4 个关键密钥统一做强度检测（RAG 主 + 2 个共享 JWT + 1 个内部同步） ——
        _validate_secret_key_strength(self.SECRET_KEY, source="Settings.SECRET_KEY")
        _validate_secret_key_strength(self.STELLAR_ADMIN_SECRET_KEY, source="Settings.STELLAR_ADMIN_SECRET_KEY")
        _validate_secret_key_strength(self.STELLAR_USER_SECRET_KEY, source="Settings.STELLAR_USER_SECRET_KEY")
        _validate_secret_key_strength(self.STELLAR_RAG_INTERNAL_SYNC_SECRET, source="Settings.STELLAR_RAG_INTERNAL_SYNC_SECRET")
        if not self.DASHSCOPE_API_KEY:
            from app.core.logger import logger as _logger
            _logger.warning(
                "[Settings] DASHSCOPE_API_KEY 为空，RAG 大模型问答会失败。"
                "请在 .env 或环境变量中配置 DASHSCOPE_API_KEY（阿里云百炼 API Key）。"
            )


settings = Settings()
