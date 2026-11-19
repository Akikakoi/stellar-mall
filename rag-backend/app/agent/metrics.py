"""Agent 轻量指标：结构化日志 + Redis 计数（不落原文）。"""
from __future__ import annotations

from datetime import datetime, timedelta
from typing import Dict

from app.core.logger import logger
from app.rag.llm_cache import get_redis_sync

_KEY_PREFIX = "agent:metrics:"
_TTL_SECONDS = 7 * 24 * 3600


def _today() -> str:
    return datetime.now().strftime("%Y-%m-%d")


def record_intent(
    intent: str,
    source: str,
    query_hash: str,
    latency_ms: int,
    cache_hit: bool = False,
    confidence: float = 0.0,
    reclassified: bool = False,
) -> None:
    """记录一次意图识别结果。只写 hash，不写用户原文。"""
    logger.info(
        f"[IntentMetric] intent={intent} source={source} cache_hit={int(cache_hit)} "
        f"latency_ms={latency_ms} conf={confidence:.2f} "
        f"reclassified={int(reclassified)} qhash={query_hash}"
    )
    client = get_redis_sync()
    if client is None:
        return
    try:
        key = f"{_KEY_PREFIX}{_today()}"
        pipe = client.pipeline()
        pipe.hincrby(key, "total", 1)
        pipe.hincrby(key, f"intent:{intent}", 1)
        pipe.hincrby(key, f"source:{source}", 1)
        if cache_hit:
            pipe.hincrby(key, "intent_cache_hit", 1)
        if reclassified:
            pipe.hincrby(key, "reclassified", 1)
        pipe.expire(key, _TTL_SECONDS)
        pipe.execute()
    except Exception as e:  # noqa: BLE001
        logger.warning(f"[IntentMetric] Redis 计数失败: {e}")


def record_agent_run(
    intent: str,
    uses_react: bool,
    answer_cache_hit: bool,
    latency_ms: int,
    react_steps: int = 0,
    cache_source: str = "",
) -> None:
    """记录一次 Agent 执行结果。

    cache_source: faq / llm，区分缓存命中来源，避免 FAQ 旁路命中被漏记。
    """
    logger.info(
        f"[AgentMetric] intent={intent} uses_react={int(uses_react)} "
        f"answer_cache_hit={int(answer_cache_hit)} cache_source={cache_source or '-'} "
        f"latency_ms={latency_ms} react_steps={react_steps}"
    )
    client = get_redis_sync()
    if client is None:
        return
    try:
        key = f"{_KEY_PREFIX}{_today()}"
        pipe = client.pipeline()
        pipe.hincrby(key, "runs", 1)
        pipe.hincrby(key, f"run_intent:{intent}", 1)
        if uses_react:
            pipe.hincrby(key, "runs_react", 1)
        if answer_cache_hit:
            pipe.hincrby(key, "answer_cache_hit", 1)
            if cache_source:
                pipe.hincrby(key, f"cache_source:{cache_source}", 1)
        pipe.expire(key, _TTL_SECONDS)
        pipe.execute()
    except Exception as e:  # noqa: BLE001
        logger.warning(f"[AgentMetric] Redis 计数失败: {e}")


def read_metrics(date: str | None = None) -> Dict[str, int]:
    """读取某日计数，供脚本/管理接口导出。"""
    client = get_redis_sync()
    if client is None:
        return {}
    day = date or _today()
    try:
        raw = client.hgetall(f"{_KEY_PREFIX}{day}")
        return {str(k): int(v) for k, v in raw.items()}
    except Exception as e:  # noqa: BLE001
        logger.warning(f"[AgentMetric] 读取计数失败: {e}")
        return {}


def recent_days(days: int = 7) -> list[str]:
    return [(datetime.now() - timedelta(days=i)).strftime("%Y-%m-%d") for i in range(days)]
