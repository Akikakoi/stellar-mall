"""Query Rewrite（指代消解）消融：多轮场景下 rewrite 开 / 关 / oracle 上界对照。

复用 app.agent.nodes._resolve_pronoun（线上同一实现：LLM 改写 + 三层缓存），
检索走 run_retrieval_ablation.retrieve 的 rrf+rerank 全量配置。

变体：
  raw          原始追问直接检索（模拟 rewrite 关闭）
  rewrite_on   结合对话历史 LLM 改写后检索（模拟 rewrite 开启）
  oracle       人工自包含完整问句检索（改写质量上界）

用法（cwd 必须是 rag-backend 根目录）：
  python scripts/ablation/run_rewrite_ablation.py
输出：scripts/ablation/results_rewrite.json
"""
import json
import os
import sys
import time
from pathlib import Path

os.environ.setdefault("LLM_CACHE_REDIS_URL", "redis://127.0.0.1:6380/11")

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from run_retrieval_ablation import retrieve, _doc_key, _log2  # noqa: E402
from app.agent.nodes import _resolve_pronoun  # noqa: E402

HERE = Path(__file__).resolve().parent
EVAL_SET = HERE / "eval_set.json"
OUT = HERE / "results_rewrite.json"

# 与线上检索配置一致：RRF 混合 + Rerank
MODE, USE_RERANK = "rrf", True


def eval_one(query_text, q):
    docs, timing = retrieve(query_text, MODE, USE_RERANK)
    keys = [_doc_key(d.metadata) for d in docs]
    rel_set = set(q["relevant"])
    gains = [1.0 if k in rel_set else 0.0 for k in keys]
    n_rel = len(rel_set)
    recall = len({k for k in keys if k in rel_set}) / n_rel if n_rel else 0.0
    mrr = next((1.0 / (i + 1) for i, g in enumerate(gains) if g > 0), 0.0)
    dcg = sum(g / _log2(i + 2) for i, g in enumerate(gains[:5]))
    idcg = sum(1.0 / _log2(i + 2) for i in range(min(n_rel, 5)))
    ndcg5 = dcg / idcg if idcg else 0.0
    return {
        "id": q["id"], "recall12": recall, "mrr": mrr, "ndcg5": ndcg5,
        "latency_ms": timing["embed_ms"] + timing["retrieve_ms"] + timing["rerank_ms"],
        "hit_docs": [k for k in keys if k in rel_set],
    }


def summarize(rows, label):
    n = len(rows)
    return {
        "variant": label, "n": n,
        "recall12": sum(r["recall12"] for r in rows) / n,
        "mrr": sum(r["mrr"] for r in rows) / n,
        "ndcg5": sum(r["ndcg5"] for r in rows) / n,
        "latency_avg_ms": sum(r["latency_ms"] for r in rows) / n,
    }


def main():
    eval_data = json.loads(EVAL_SET.read_text(encoding="utf-8"))
    d_queries = [q for q in eval_data["queries"] if q["type"] == "multi_turn"]
    print(f"多轮指代 query 共 {len(d_queries)} 条\n")

    print("== 预热 ==")
    retrieve("预热：星耀手机续航", MODE, USE_RERANK)
    print("预热完成\n")

    out = {"raw": [], "rewrite_on": [], "oracle": [], "rewrites": []}

    # raw：原始追问
    print("== 变体 1/3: raw（rewrite 关） ==")
    for q in d_queries:
        r = eval_one(q["query"], q)
        out["raw"].append(r)
        print(f"  {q['id']} recall={r['recall12']:.2f} mrr={r['mrr']:.2f}")

    # rewrite_on：_resolve_pronoun 走线上同款 LLM 改写
    print("\n== 变体 2/3: rewrite_on（LLM 指代消解） ==")
    llm_calls = 0
    for q in d_queries:
        history = f"用户: {q['context']}"
        t0 = time.perf_counter()
        # 注意：首个调用会真实请求 LLM（rewrite 缓存若已有则命中）
        rewritten = _resolve_pronoun(q["query"], history)
        rewrite_ms = (time.perf_counter() - t0) * 1000
        llm_calls += 1
        out["rewrites"].append({"id": q["id"], "raw": q["query"],
                                "rewritten": rewritten, "rewrite_ms": rewrite_ms})
        print(f"  {q['id']}: {q['query']!r} -> {rewritten!r} ({rewrite_ms:.0f}ms)")
        r = eval_one(rewritten, q)
        out["rewrite_on"].append(r)
        print(f"      recall={r['recall12']:.2f} mrr={r['mrr']:.2f}")

    # oracle：自包含上界
    print("\n== 变体 3/3: oracle（自包含上界） ==")
    for q in d_queries:
        r = eval_one(q["oracle"], q)
        out["oracle"].append(r)
        print(f"  {q['id']} recall={r['recall12']:.2f} mrr={r['mrr']:.2f}")

    out["llm_calls"] = llm_calls
    out["summary"] = [summarize(out["raw"], "raw（rewrite 关）"),
                      summarize(out["rewrite_on"], "rewrite_on"),
                      summarize(out["oracle"], "oracle 上界")]
    OUT.write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")

    print("\n===== Query Rewrite 消融对照（10 条多轮指代 query） =====")
    print(f"{'变体':<20}{'recall@12':>10}{'MRR':>8}{'nDCG@5':>8}{'延迟avg':>10}")
    for s in out["summary"]:
        print(f"{s['variant']:<20}{s['recall12']:>10.3f}{s['mrr']:>8.3f}"
              f"{s['ndcg5']:>8.3f}{s['latency_avg_ms']:>9.0f}ms")
    print(f"\n结果已写入 {OUT}")


if __name__ == "__main__":
    main()
