"""Agent 端到端消融 + LLM-as-judge 评测。

对照（均关闭 LLM 缓存，保证每个变体独立生成答案）：
  组 R（8 条单轮产品咨询）：
    full        完整链路（Rerank 开）
    no_rerank   RERANKER_ENABLED=False（检索少一层精排）
  组 D（10 条多轮指代，带对话历史）：
    full        完整链路（Query Rewrite 开）
    no_rewrite  QUERY_REWRITE_ENABLED=False（追问原样检索）

评测：DeepSeek 做判分（0-5 分，与知识库参考事实对照），统计平均分/延迟。

用法（cwd 必须是 rag-backend 根目录）：
  python scripts/ablation/run_agent_e2e_ablation.py
输出：scripts/ablation/results_agent_e2e.json
"""
import json
import os
import re
import sys
import time
from pathlib import Path

os.environ.setdefault("LLM_CACHE_REDIS_URL", "redis://127.0.0.1:6380/11")
# MALL_API_BASE_URL 沿用 .env 的 8082（本机 stellar-server 实例，自签 token 已验证通过）

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT))

from app.config import settings  # noqa: E402
from app.agent.graph import run_agent  # noqa: E402
from app.rag.llm import get_langchain_chat  # noqa: E402
from langchain_core.messages import SystemMessage, HumanMessage  # noqa: E402

HERE = Path(__file__).resolve().parent
OUT = HERE / "results_agent_e2e.json"

sys.path.insert(0, str(HERE))
from mall_token import make_mall_user_token  # noqa: E402

MALL_TOKEN = make_mall_user_token(user_id=4)  # 真实存在的测试用户 ID

# 组 R：单轮产品咨询（rerank 影响）
GROUP_R = [
    {"id": "A02", "query": "星耀 X100 Pro Max 支持卫星通信吗",
     "reference": "星耀 X100 Pro Max 支持双向卫星通信。"},
    {"id": "A03", "query": "清逸 AirBook 14 的屏幕参数怎么样",
     "reference": "14 英寸 OLED，2.8K（2880×1800），90Hz 刷新率，峰值亮度 600nit（HDR 1000nit），100% DCI-P3，Delta E<1，16:10 比例。"},
    {"id": "A04", "query": "AX3000 路由器支持哪些无线协议",
     "reference": "IEEE 802.11a/b/g/n/ac/ax（Wi-Fi 6），双频并发 3000Mbps（2.4GHz 574Mbps + 5GHz 2402Mbps）。"},
    {"id": "A06", "query": "20000mAh 移动电源可以带上飞机吗",
     "reference": "20000mAh 约 74Wh，符合民航登机标准（<100Wh），可以带上飞机。"},
    {"id": "A09", "query": "星耀 Band 9 支持多少种运动模式",
     "reference": "星耀 Band 9 支持 120 种运动模式。"},
    {"id": "B02", "query": "预算一千多，给孩子买的第一部 5G 手机有什么推荐",
     "reference": "星耀 A60 5G 学生机，¥1,299 起，内置家长管控（护眼模式、应用时长管理、定位追踪），适合作为第一部 5G 手机。"},
    {"id": "B06", "query": "能测心电图和血压趋势的智能手表有哪些",
     "reference": "星耀 Watch 3 Pro（ECG 心电图医疗器械认证 + 血压趋势）和星耀 Watch 4 Ultra（ECG + 血压趋势）。"},
    {"id": "C03", "query": "支持无线充电的折叠屏手机是哪款",
     "reference": "星耀 Flip 折叠屏，67W 有线 + 15W 无线充电。"},
]

# 组 D：多轮指代（rewrite 影响），带 context 历史
GROUP_D = [
    {"id": "D01", "context": "星耀 X100 Pro Max 卖多少钱", "query": "它和 X200 哪个拍照更好",
     "reference": "星耀 X100 Pro Max：骁龙8Gen4，1 英寸超大底主摄 + 200MP 潜望长焦；星耀 X200：徕卡联合研发光学系统、1 英寸大底主摄，全焦段 4K 120fps。两者均为影像旗舰。"},
    {"id": "D02", "context": "介绍一下玉米手机 10 Pro", "query": "这款手机的续航和快充表现怎么样",
     "reference": "玉米手机 10 Pro：5500mAh 谷能高密度电池，120W 谷闪极速快充（19 分钟充满）。"},
    {"id": "D03", "context": "我想买台平板用来画画", "query": "屏幕最大那款对触控笔的支持怎么样",
     "reference": "屏幕最大的是星耀 Tab Max 14.6（14.6 英寸），触控采样率 480Hz（笔模式），支持专业触控笔，OLED 3K 屏。"},
    {"id": "D04", "context": "清逸 AirBook 13 什么价格", "query": "它比 AirBook 14 轻多少",
     "reference": "清逸 AirBook 13 重 980g，清逸 AirBook 14 重 1.1kg（1100g），AirBook 13 轻 120g。"},
    {"id": "D05", "context": "店里有哪些智能手表", "query": "能独立插卡打电话的那款续航多久",
     "reference": "支持 eSIM 独立通话的是星耀 Watch 3 Pro / Watch 4 Ultra，典型续航 14 天（Watch 4 Ultra GPS 模式 100 小时、探险模式 40 天）。"},
    {"id": "D06", "context": "星耀 Buds Pro 3 降噪深度是多少", "query": "开着降噪能连续用几个小时",
     "reference": "星耀 Buds Pro 3 开 ANC 续航 5.5 小时（关 ANC 8 小时），充电盒额外 28 小时，综合 36 小时。"},
    {"id": "D07", "context": "帮我看看星耀 GamePro 16", "query": "这台机器的散热规格怎么样",
     "reference": "星耀 GamePro 16：双风扇 6 热管 + 液态金属导热，四出风口，一键强冷模式。"},
    {"id": "D08", "context": "苍穹 Aether N8 Max 屏幕多大", "query": "它的峰值亮度能到多少",
     "reference": "苍穹 Aether N8 Max 峰值亮度 4500nit，全局 1600nit。"},
    {"id": "D09", "context": "看看星耀 Note 14 Pro 吧", "query": "12 加 512 那个版本现在卖多少",
     "reference": "星耀 Note 14 Pro 12GB+512GB 版本：子夜黑 ¥2,599、冰川蓝 ¥2,599、香槟金 ¥2,699。"},
    {"id": "D10", "context": "我想要一个能监测睡眠的智能戒指", "query": "防水等级能戴着游泳吗",
     "reference": "星耀 SmartRing Nova 为 IP68 防水，可游泳佩戴。"},
]

JUDGE_SYSTEM = "你是严格的电商客服问答质检员。根据【参考事实】给回答打分，只输出 JSON。"
JUDGE_PROMPT = """【用户问题】
{query}

【参考事实】
{reference}

【待评回答】
{answer}

评分标准（0-5 整数）：
5 = 完全正确且覆盖参考事实要点，表述清晰
4 = 正确，遗漏少量次要细节
3 = 部分正确，有明显遗漏或表述含糊
2 = 含事实性错误但方向大致对
1 = 大部分错误或答非所问
0 = 完全错误 / 拒答 / 编造

只输出 JSON：{{"score": 整数, "reason": "一句话理由"}}"""


def judge(query, reference, answer):
    llm = get_langchain_chat()
    resp = llm.invoke([SystemMessage(content=JUDGE_SYSTEM),
                       HumanMessage(content=JUDGE_PROMPT.format(
                           query=query, reference=reference,
                           answer=answer[:1500]))])
    text = resp.content if hasattr(resp, "content") else str(resp)
    m = re.search(r'"score"\s*:\s*(\d)', text)
    reason_m = re.search(r'"reason"\s*:\s*"([^"]*)"', text)
    return (int(m.group(1)) if m else -1), (reason_m.group(1) if reason_m else text[:100])


def run_group(name, items, use_history, label):
    rows = []
    for it in items:
        t0 = time.perf_counter()
        try:
            r = run_agent(it["query"], mall_token=MALL_TOKEN,
                          conversation_history=[("user", it["context"])] if use_history else None)
            answer = (r or {}).get("answer", "")
        except Exception as e:
            answer, r = f"<error: {e}>", {}
        latency = (time.perf_counter() - t0) * 1000
        score, reason = judge(it["query"], it["reference"], answer)
        rows.append({"id": it["id"], "variant": label, "score": score,
                     "latency_ms": latency, "reason": reason,
                     "answer_head": answer[:120].replace("\n", " ")})
        print(f"  [{it['id']}] score={score}  lat={latency/1000:.1f}s  {reason[:40]}")
    avg = sum(r["score"] for r in rows) / len(rows)
    lat = sum(r["latency_ms"] for r in rows) / len(rows)
    print(f"  --> {label} 平均分={avg:.2f}  平均延迟={lat/1000:.1f}s\n")
    return rows, {"variant": label, "n": len(rows), "avg_score": avg, "latency_avg_ms": lat}


def main():
    print("全程关闭 LLM 缓存，保证各变体独立生成。\n")
    settings.LLM_CACHE_ENABLED = False
    out = {"rows": [], "summary": []}

    print("== 组 R 变体 1/2: full（Rerank 开） ==")
    rows, s = run_group("R", GROUP_R, False, "R_full")
    out["rows"] += rows; out["summary"].append(s)

    settings.RERANKER_ENABLED = False
    print("== 组 R 变体 2/2: no_rerank（Rerank 关） ==")
    rows, s = run_group("R", GROUP_R, False, "R_no_rerank")
    out["rows"] += rows; out["summary"].append(s)
    settings.RERANKER_ENABLED = True

    print("== 组 D 变体 1/2: full（Rewrite 开） ==")
    rows, s = run_group("D", GROUP_D, True, "D_full")
    out["rows"] += rows; out["summary"].append(s)

    settings.QUERY_REWRITE_ENABLED = False
    print("== 组 D 变体 2/2: no_rewrite（Rewrite 关） ==")
    rows, s = run_group("D", GROUP_D, True, "D_no_rewrite")
    out["rows"] += rows; out["summary"].append(s)
    settings.QUERY_REWRITE_ENABLED = True

    settings.LLM_CACHE_ENABLED = True

    OUT.write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")

    print("===== Agent 端到端消融（LLM-as-judge，0-5 分） =====")
    for s in out["summary"]:
        print(f"{s['variant']:<16} n={s['n']:<3} 平均分={s['avg_score']:.2f}  延迟avg={s['latency_avg_ms']/1000:.1f}s")
    print(f"\n结果已写入 {OUT}")


if __name__ == "__main__":
    main()
