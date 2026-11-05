"""修复验证脚本：针对消融报告 D 组失败案例（D02/D09/D03 等）重跑 Agent。

用法：cd rag-backend && python scripts/ablation/verify_fix.py
"""
import os
import sys
from pathlib import Path

os.environ.setdefault("LLM_CACHE_REDIS_URL", "redis://127.0.0.1:6380/11")

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from app.config import settings  # noqa: E402
from app.agent.graph import run_agent  # noqa: E402
from mall_token import make_mall_user_token  # noqa: E402

CASES = [
    {"id": "D02", "context": "介绍一下玉米手机 10 Pro", "query": "这款手机的续航和快充表现怎么样",
     "expect": "5500mAh / 120W，主体应为玉米手机 10 Pro"},
    {"id": "D09", "context": "看看星耀 Note 14 Pro 吧", "query": "12 加 512 那个版本现在卖多少",
     "expect": "12GB+512GB 售价 ¥2,599 起（子夜黑/冰川蓝），主体应为 Note 14 Pro"},
    {"id": "D03", "context": "我想买台平板用来画画", "query": "屏幕最大那款对触控笔的支持怎么样",
     "expect": "Tab Max 14.6，笔模式 480Hz"},
    {"id": "D05", "context": "店里有哪些智能手表", "query": "能独立插卡打电话的那款续航多久",
     "expect": "Watch 3 Pro / 4 Ultra，14 天"},
]


def main():
    settings.LLM_CACHE_ENABLED = False  # 隔离缓存，确保答案真实生成
    token = make_mall_user_token(user_id=4)
    for c in CASES:
        r = run_agent(c["query"], mall_token=token,
                      conversation_history=[("user", c["context"])])
        ans = (r or {}).get("answer", "").replace("\n", " ")
        print(f"[{c['id']}] 期望: {c['expect']}")
        print(f"      实答: {ans[:180]}")
        print()
    settings.LLM_CACHE_ENABLED = True


if __name__ == "__main__":
    main()
