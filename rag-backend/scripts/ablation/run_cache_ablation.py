"""LLM 三层缓存消融：L1 精确 / L2 语义 / single-flight，用真实 Agent 链路测收益。

通过 graph.run_agent 走完整 Agent（意图分类 → kb_search 工具 → 答案生成），
缓存开关用 settings 运行时覆盖；L2 消融用 monkeypatch（因 get_sync 同步路径
不读取 LLM_CACHE_SEMANTIC_ENABLED，开关在该路径上无效）。

阶段：
  P0  cache_off     关闭全部缓存（基线，全 L3 真调用，不产生写入）
  P0b cold_populate 开缓存首次运行（全 miss + 异步写入，为 P1 铺垫）
  P1  l1_exact      重放相同 query（预期 L1 精确命中）
  P2  l2_semantic   同义改写 query（预期 L2 语义命中）
  P2b l2_disabled   patch 掉 L2 后重放同义改写（量化 L2 贡献 = P2 与 P2b 延迟差）
  P3  novel         全新 query（全 miss，测缓存读开销）
  P4  single_flight 单元级并发测试：6 线程同一 key，计数 producer 调用次数

用法（cwd 必须是 rag-backend 根目录）：
  python scripts/ablation/run_cache_ablation.py
输出：scripts/ablation/results_cache.json
"""
import json
import os
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

os.environ.setdefault("LLM_CACHE_REDIS_URL", "redis://127.0.0.1:6380/11")
# MALL_API_BASE_URL 沿用 .env 的 8082（本机 stellar-server 实例，自签 token 已验证通过）

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT))

from app.config import settings  # noqa: E402
from app.agent.graph import run_agent  # noqa: E402
import app.rag.llm_cache as llm_cache_mod  # noqa: E402

sys.path.insert(0, str(Path(__file__).resolve().parent))
from mall_token import make_mall_user_token  # noqa: E402

MALL_TOKEN = make_mall_user_token(user_id=4)  # 真实存在的测试用户 ID


def agent(q):
    return run_agent(q, mall_token=MALL_TOKEN)

HERE = Path(__file__).resolve().parent
OUT = HERE / "results_cache.json"

QUERIES = [
    "星耀 X100 Pro Max 支持卫星通信吗",
    "清逸 AirBook 14 的屏幕参数怎么样",
    "AX3000 路由器支持哪些无线协议",
    "20000mAh 移动电源可以带上飞机吗",
    "星耀 Band 9 支持多少种运动模式",
    "天玑 9300 芯片的平板有哪些",
    "能测心电图和血压趋势的智能手表",
    "戴着睡觉也没感觉的健康监测设备",
    "5500mAh 电池配 120W 快充的手机",
    "支持无线充电的折叠屏手机",
    "可以插存储卡扩容的平板电脑",
    "带 eSIM 独立打电话的智能手表",
]
PARAPHRASES = [
    "请问一下，星耀 X100 Pro Max 支不支持卫星通信呢？",
    "想了解下清逸 AirBook 14 这款笔记本的屏幕参数",
    "路由器 AX3000 都兼容什么无线协议呀",
    "两万毫安的移动电源登机的时候能带吗",
    "星耀 Band 9 这款手环一共有多少种运动模式？",
    "用的天玑 9300 芯片的平板电脑有哪几款",
    "有没有能测心电图、看血压趋势的智能手表推荐",
    "睡觉戴着基本没感觉的健康监测设备有哪些",
    "手机电池 5500 毫安还配 120W 快充的有哪些",
    "折叠屏手机里面支持无线充电的是哪款",
    "哪些平板电脑可以插存储卡来扩容？",
    "智能手表里带 eSIM 能独立打电话的有哪些",
]
NOVEL = [
    "星耀 Watch 4 Ultra 的防水等级是多少",
    "星耀 Creator 14 用什么处理器",
    "星耀 Tab Pro 12.4 电池多大",
    "星耀 Buds Pro 3 降噪深度多少分贝",
    "清逸 ProBook 16 的显卡是什么型号",
    "星耀 SmartRing Nova 续航几天",
]


def run_phase(name, queries, before, sleep_after=4.0):
    lats, detail = [], []
    for q in queries:
        t0 = time.perf_counter()
        try:
            r = agent(q)
            ans = (r or {}).get("answer", "")
        except Exception as e:
            ans = f"<error: {e}>"
        lats.append((time.perf_counter() - t0) * 1000)
        detail.append({"query": q, "latency_ms": lats[-1],
                       "answer_head": ans[:80].replace("\n", " ")})
    if sleep_after:
        time.sleep(sleep_after)  # 等后台异步缓存写入落盘
    snap = llm_cache_mod.get_cache_metrics()
    delta = {"l1_hits": snap["l1_hits"] - before["l1_hits"],
             "l2_hits": snap["l2_hits"] - before["l2_hits"],
             "misses": snap["misses"] - before["misses"],
             "write_oks": snap["write_oks"] - before["write_oks"]}
    total = delta["l1_hits"] + delta["l2_hits"] + delta["misses"]
    result = {
        "phase": name, "n": len(queries),
        "latency_avg_ms": sum(lats) / len(lats),
        "latency_min_ms": min(lats), "latency_max_ms": max(lats),
        "delta": delta,
        "hit_ratio": round((delta["l1_hits"] + delta["l2_hits"]) / total, 3) if total else 0.0,
        "detail": detail,
    }
    print(f"  [{name}] avg={result['latency_avg_ms']:.0f}ms  "
          f"L1={delta['l1_hits']} L2={delta['l2_hits']} miss={delta['misses']} "
          f"writes={delta['write_oks']}")
    return result, snap


def patch_l2_off():
    """临时禁用 L2 语义检索（get_sync 同步路径不读 SEMANTIC_ENABLED 开关）。"""
    llm_cache_mod._semantic_search_sync = lambda *a, **k: None


def restore_l2():
    import importlib
    importlib.reload(llm_cache_mod)
    # reload 后需重新绑定 run_agent 引用的符号
    import app.agent.graph  # noqa
    import app.agent.nodes as nodes
    importlib.reload(nodes)


def main():
    # L1 Redis：独立脚本没有 FastAPI lifespan，需手动初始化（否则 _redis_available=False 恒 miss）
    import asyncio
    ok = asyncio.run(llm_cache_mod.init_redis())
    print(f"L1 Redis 手动初始化: {'成功' if ok else '失败'}")

    # L2 Chroma：清空旧语义缓存，保证 P0b 真冷启动、P3 真全 miss
    import asyncio as _aio
    async def _clear_l2():
        cache = llm_cache_mod.get_llm_cache()
        await cache.invalidate_all()
    _aio.run(_clear_l2())
    print("L2 语义缓存已清空\n")

    print(f"query 数：主组 {len(QUERIES)}，改写 {len(PARAPHRASES)}，新颖 {len(NOVEL)}\n")

    # L1 Redis 连通性自检
    try:
        import redis
        client = redis.Redis.from_url(settings.LLM_CACHE_REDIS_URL, socket_timeout=2)
        client.ping()
        print(f"L1 Redis 连通: {settings.LLM_CACHE_REDIS_URL}")
    except Exception as e:
        print(f"L1 Redis 不可用（L1 命中将为 0）: {e}")
    print()

    results = []

    # ---- P0 基线（关缓存） ----
    settings.LLM_CACHE_ENABLED = False
    print("== P0 cache_off（基线：全 L3 真调用） ==")
    r, _ = run_phase("P0_cache_off", QUERIES, llm_cache_mod.get_cache_metrics())
    results.append(r)

    # ---- P0b 冷启动写入 ----
    settings.LLM_CACHE_ENABLED = True
    print("\n== P0b cold_populate（缓存开，首次运行，全 miss + 写入） ==")
    r, _ = run_phase("P0b_cold_populate", QUERIES, llm_cache_mod.get_cache_metrics())
    results.append(r)

    # ---- P1 L1 精确命中 ----
    print("\n== P1 l1_exact（重放相同 query，预期 L1 命中） ==")
    r, _ = run_phase("P1_l1_exact", QUERIES, llm_cache_mod.get_cache_metrics())
    results.append(r)

    # ---- P2 L2 语义命中 ----
    print("\n== P2 l2_semantic（同义改写 query，预期 L2 命中） ==")
    r, _ = run_phase("P2_l2_semantic", PARAPHRASES, llm_cache_mod.get_cache_metrics())
    results.append(r)

    # ---- P2b 关 L2（monkeypatch）重放同义改写 ----
    print("\n== P2b l2_disabled（patch 关 L2，重放同义改写） ==")
    patch_l2_off()
    try:
        r, _ = run_phase("P2b_l2_disabled", PARAPHRASES, llm_cache_mod.get_cache_metrics())
        results.append(r)
    finally:
        restore_l2()

    # ---- P3 全新 query ----
    print("\n== P3 novel（全新 query，全 miss） ==")
    r, _ = run_phase("P3_novel_miss", NOVEL, llm_cache_mod.get_cache_metrics())
    results.append(r)

    # ---- P4 single-flight 单元测试（不跑 Agent，隔离机制本身） ----
    print("\n== P4 single_flight 单元测试（6 线程同一 key，producer 睡 2s） ==")
    producer_calls = {"n": 0}
    def producer():
        producer_calls["n"] += 1
        time.sleep(2.0)
        return f"answer-{producer_calls['n']}"
    def worker(i):
        t0 = time.perf_counter()
        val = llm_cache_mod.single_flight_sync("ablation:sf-test", producer)
        return (time.perf_counter() - t0) * 1000, val
    with ThreadPoolExecutor(max_workers=6) as ex:
        w = list(ex.map(worker, range(6)))
    vals = {v for _, v in w}
    results.append({
        "phase": "P4_single_flight_unit", "n": 6,
        "producer_calls": producer_calls["n"],
        "distinct_values": len(vals),
        "latency_max_ms": max(l for l, _ in w),
        "latency_min_ms": min(l for l, _ in w),
        "note": f"预期 producer_calls=1（实际 {producer_calls['n']}）；"
                f"等待方延迟应 < 2s（实际 min={min(l for l, _ in w):.0f}ms max={max(l for l, _ in w):.0f}ms）",
    })
    print(f"  producer 调用 {producer_calls['n']} 次（预期 1），"
          f"延迟 min={min(l for l, _ in w):.0f}ms / max={max(l for l, _ in w):.0f}ms")

    OUT.write_text(json.dumps({
        "final_metrics_snapshot": llm_cache_mod.get_cache_metrics(),
        "phases": results,
    }, ensure_ascii=False, indent=1), encoding="utf-8")

    print("\n===== LLM 缓存消融对照（12 条产品咨询 query，完整 Agent 链路） =====")
    print(f"{'阶段':<24}{'延迟avg':>10}{'延迟min':>10}{'L1命中':>8}{'L2命中':>8}{'miss':>6}{'命中率':>8}")
    for r in results:
        d = r.get("delta", {})
        print(f"{r['phase']:<24}{r.get('latency_avg_ms', 0):>9.0f}ms{r.get('latency_min_ms', 0):>9.0f}ms"
              f"{d.get('l1_hits', 0):>8}{d.get('l2_hits', 0):>8}{d.get('misses', 0):>6}"
              f"{r.get('hit_ratio', '-'):>8}")
    print(f"\n结果已写入 {OUT}")


if __name__ == "__main__":
    main()
