"""Loguru 日志配置。

注：为了避免与 app.config 产生循环导入（config 需要 logger 做弱密钥报警，
而 logger 又需要 settings 拿到日志级别 / 文件路径），本文件采用「延迟初始化」：
  - 模块级只暴露一个预配置的 loguru.logger（默认级别 INFO，只打 stdout）
  - setup_logger(settings) 在 settings 构造完成之后由调用方（main.py / conftest.py）
    显式调用，才装配文件 Handler、真实的 APP_ENV 级别等
"""
from __future__ import annotations

import sys
from pathlib import Path
from loguru import logger


# 模块加载时做一个「最小可用」的 stdout logger，避免 config.model_post_init
# 或其它启动早期代码调用 logger 时丢失日志。真实配置由 setup_logger 覆盖。
logger.remove()
logger.add(
    sys.stdout,
    level="INFO",
    format="<green>{time:YYYY-MM-DD HH:mm:ss}</green> | "
           "<level>{level: <8}</level> | "
           "<cyan>{name}</cyan>:<cyan>{function}</cyan>:<cyan>{line}</cyan> - "
           "<level>{message}</level>",
    enqueue=True,
)


def setup_logger(settings=None) -> None:
    """依据 settings 装配最终的 logger（开发模式 DEBUG，其它 INFO；文件日志按天切分）。

    允许 settings=None 调用，使用默认值（INFO 级别、logs 目录为 BASE_DIR/data/logs）。
    """
    # 清空默认最小配置
    logger.remove()

    # 默认兜底值，避免 settings 为 None 的时候报错
    if settings is None:
        class _Fallback:
            APP_ENV = "development"
            BASE_DIR = Path(__file__).resolve().parent.parent.parent
        settings = _Fallback()  # type: ignore[assignment]

    app_env = getattr(settings, "APP_ENV", "development")
    base_dir: Path = getattr(settings, "BASE_DIR") or Path(__file__).resolve().parent.parent.parent

    stdout_level = "DEBUG" if app_env == "development" else "INFO"
    logger.add(
        sys.stdout,
        level=stdout_level,
        format="<green>{time:YYYY-MM-DD HH:mm:ss}</green> | "
               "<level>{level: <8}</level> | "
               "<cyan>{name}</cyan>:<cyan>{function}</cyan>:<cyan>{line}</cyan> - "
               "<level>{message}</level>",
        enqueue=True,
    )

    log_dir = Path(base_dir) / "data" / "logs"
    log_dir.mkdir(parents=True, exist_ok=True)
    logger.add(
        log_dir / "app_{time:YYYY-MM-DD}.log",
        level="INFO",
        rotation="00:00",
        retention="30 days",
        encoding="utf-8",
        enqueue=True,
        backtrace=True,
        diagnose=False,
    )


__all__ = ["logger", "setup_logger"]

