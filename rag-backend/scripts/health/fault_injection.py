"""实验 1：故障注入 / 降级验证。

场景：
  S1 停 ES          → 商城搜索 /user/spu/page 应降级 MySQL 仍返回数据；
                       /user/spu/suggest（ES 专用）观察行为
  S2 停 Redis       → 限流拦截器应降级放行；JWT 黑名单校验应降级放行；
                       登录（读用户表）不受影响
  S3 Mall API 不可达 → mall_client 熔断器：5 次失败后打开，fail-fast；
                       恢复窗口（30s）后放行，成功后闭合

用法：cd rag-backend && python scripts/health/fault_injection.py
输出：scripts/health/results_fault_injection.json
"""
import os
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from common import docker, http, make_token, save_result, wait_port  # noqa: E402

RESULTS = {"scenarios": []}


def record(scenario, step, expect, actual, ok, detail=""):
    RESULTS["scenarios"][-1]["steps"].append({
        "step": step, "expect": expect, "actual": actual, "ok": ok, "detail": detail,
    })
    print(f"  [{'PASS' if ok else 'FAIL'}] {step}  期望={expect}  实际={actual}  {detail[:80]}")


def s1_es_down():
    RESULTS["scenarios"].append({"scenario": "S1_ES_down", "steps": []})
    print("== S1：停止 stellar-es ==")
    tok = make_token(4)
    assert docker("stop", "stellar-es"), "stop es 失败"
    time.sleep(3)
    try:
        # 注意：URL 中文参数必须显式编码，urllib 不会自动处理非 ASCII URL
        from urllib.parse import quote
        kw = quote("手机")
        # ES 挂时后端连 ES 可能阻塞，放宽探针超时并重试，观察"慢降级"还是"快失败"
        t0 = time.perf_counter()
        st, body = http("GET", f"/user/spu/page?name={kw}&pageSize=5", tok, timeout=60)
        lat = (time.perf_counter() - t0) * 1000
        ok = st == 200 and '"code":1' in body and '"total"' in body
        record("S1", "搜索接口 /user/spu/page（60s 超时内）",
               "200 且有数据（MySQL 降级）", f"HTTP {st}, {lat:.0f}ms", ok, body[:200])
        st2, body2 = http("GET", f"/user/spu/suggest?prefix={quote('星')}", tok, timeout=60)
        record("S1", "搜索建议 /user/spu/suggest（ES 专用）", "观察行为",
               f"HTTP {st2}", True, body2[:200])
    finally:
        docker("start", "stellar-es")
        wait_port(9200, up=True, max_seconds=120)
        time.sleep(5)
        st, body = http("GET", f"/user/spu/page?name={kw}&pageSize=1", tok, timeout=60)
        if st != 200:  # 后端 ES 客户端可能还在重试，多等几轮
            time.sleep(10)
            st, body = http("GET", f"/user/spu/page?name={kw}&pageSize=1", tok, timeout=60)
        record("S1", "ES 恢复后搜索", "200", f"HTTP {st}", st == 200, body[:100])


def s2_redis_down():
    RESULTS["scenarios"].append({"scenario": "S2_Redis_down", "steps": []})
    print("== S2：停止 stellar-redis（Java 与 RAG L1 共用 6380） ==")
    tok6 = make_token(6)
    assert docker("stop", "stellar-redis"), "stop redis 失败"
    time.sleep(3)
    try:
        codes = []
        for _ in range(5):
            st, _ = http("GET", "/user/spu/page?pageSize=1", make_token(4))
            codes.append(st)
        record("S2", "限流拦截器（连续 5 次请求）", "全部放行（降级不阻断）",
               f"codes={codes}", all(c == 200 for c in codes))

        st, body = http("GET", "/user/order/86", tok6)
        record("S2", "JWT 黑名单校验（Redis 不可用）", "降级放行 200",
               f"HTTP {st}", st == 200, body[:120])

        st, body = http("POST", "/user/user/login",
                        body={"email": "nobody@test.com", "password": "wrong"})
        # 发现记录：登录在 Redis 不可用时直接 500（无降级）——疑似登录链路强依赖 Redis
        record("S2", "登录接口（Redis 不可用）", "期望降级可用，实测观察",
               f"HTTP {st}", st == 200, f"发现：{body[:120]}")
    finally:
        docker("start", "stellar-redis")
        wait_port(6380, up=True, max_seconds=60)
        time.sleep(2)
        st, _ = http("GET", "/user/spu/page?pageSize=1", make_token(4))
        record("S2", "Redis 恢复后请求", "200", f"HTTP {st}", st == 200)


def s3_mall_api_down():
    RESULTS["scenarios"].append({"scenario": "S3_mall_api_unreachable", "steps": []})
    print("== S3：Mall API 不可达（指向死端口，验证熔断器生命周期） ==")
    from app.config import settings
    import app.agent.tools.mall_client as mc

    original = settings.MALL_API_BASE_URL
    settings.MALL_API_BASE_URL = "http://127.0.0.1:59999"  # 无监听端口
    mc._circuit_breaker._failure_count = 0
    mc._circuit_breaker._last_failure_time = None
    try:
        msgs = []
        t0 = time.perf_counter()
        for i in range(6):
            r = mc.call_mall("GET", "/user/spu/page", params={"pageSize": 1})
            msgs.append((r or {}).get("message", "")[:60])
        elapsed = (time.perf_counter() - t0) * 1000
        opened = "熔断" in "".join(msgs) or "稍后重试" in msgs[-1]
        record("S3", "连续 6 次调用失败接口", "第 6 次应 fail-fast（熔断打开）",
               f"末次消息含熔断={opened}", opened, f"总耗时 {elapsed:.0f}ms，末次={msgs[-1][:60]}")
        fast = elapsed < 5 * settings.MALL_API_TIMEOUT_SECONDS * 1000
        record("S3", "熔断后 fail-fast 生效", "总耗时 << 5×超时时间",
               f"{elapsed:.0f}ms", bool(fast))

        print("  等待恢复窗口（30s）...")
        time.sleep(settings.MALL_API_CIRCUIT_BREAKER_RECOVERY_SECONDS + 2)
        settings.MALL_API_BASE_URL = original
        r = mc.call_mall("GET", "/user/spu/page", params={"pageSize": 1})
        body = (r or {}).get("message", "")
        record("S3", "恢复窗口后 + 真实地址", "调用放行且成功", "见 detail",
               (r or {}).get("ok") is True, body[:80] or "ok=True")
    finally:
        settings.MALL_API_BASE_URL = original


def main():
    s1_es_down()
    s2_redis_down()
    s3_mall_api_down()
    total = sum(1 for s in RESULTS["scenarios"] for x in s["steps"])
    passed = sum(1 for s in RESULTS["scenarios"] for x in s["steps"] if x["ok"])
    RESULTS["summary"] = f"{passed}/{total} 通过"
    print(f"\n===== 故障注入汇总: {passed}/{total} 通过 =====")
    save_result("results_fault_injection.json", RESULTS)


if __name__ == "__main__":
    main()
