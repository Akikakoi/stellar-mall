"""导出 Agent 意图/执行计数（默认最近 7 天）。

用法：
    python scripts/agent_metrics.py --days 7
    python scripts/agent_metrics.py --date 2026-11-19
"""
from __future__ import annotations

import argparse
import asyncio
import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

# 指标脚本不参与鉴权，补占位密钥避免 Settings 在导入期报错。
for _key in (
    "SECRET_KEY",
    "STELLAR_ADMIN_SECRET_KEY",
    "STELLAR_USER_SECRET_KEY",
    "STELLAR_RAG_INTERNAL_SYNC_SECRET",
):
    os.environ.setdefault(_key, "agent-metrics-placeholder-key-0123456789")

from app.agent.metrics import read_metrics, recent_days  # noqa: E402
from app.rag.llm_cache import init_redis  # noqa: E402


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--date", help="指定日期 YYYY-MM-DD")
    parser.add_argument("--days", type=int, default=7)
    args = parser.parse_args()

    asyncio.run(init_redis())
    days = [args.date] if args.date else recent_days(args.days)
    result = {day: read_metrics(day) for day in days}
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
