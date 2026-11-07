"""Query Rewrite（指代消解）消融：多轮场景下 rewrite 开 / 关 / oracle 上界对照。

复用 app.agent.nodes._resolve_pronoun（线上同一实现：LLM 改写 + 三层缓存），
检索走 run_retrieval_ablation.retrieve 的 rrf+rerank 全量配置。

变体：
  raw          原始追问直接检索（模拟 rewrite 关闭）
  rewrite_on   结合对话历史 LLM 改写后检索（模拟 rewrite 开启）
  oracle       人工自包含完整问句检索（改写质量上界）

用法（cwd 必须是 rag-backend 根目录）：
  python scripts/ablation/run_rewrite_ablation.py
输出：带时间戳的新 JSON；--dry-run 离线检查，--repeats 默认两轮。
"""
import json
import random
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from run_retrieval_ablation import (retrieve, _doc_key, document_metrics, parse_args,
                                    preflight, warmup, clear_embedding_query, reserve_output)

HERE = Path(__file__).resolve().parent
EVAL_SET = HERE / "eval_set.json"

# 与线上检索配置一致：RRF 混合 + Rerank
MODE, USE_RERANK = "rrf", True


def eval_one(query_text, q):
    clear_embedding_query(query_text)
    docs, timing = retrieve(query_text, MODE, USE_RERANK)
    keys = [_doc_key(d.metadata) for d in docs]
    return {"id": q["id"], "query": query_text,
            **document_metrics(keys, q["relevant"]), **timing,
            "latency_ms": sum(timing.values()), "chunk_doc_keys": keys}


def rewrite_with_evidence(query, history):
    from types import SimpleNamespace
    from unittest.mock import patch
    from app.agent import nodes
    client = nodes.get_langchain_chat()
    failures, calls = [], []

    def invoke(messages):
        calls.append(1)
        try:
            return client.invoke(messages)
        except Exception as exc:
            failures.append(exc)
            raise

    with patch.object(nodes, "get_langchain_chat", return_value=SimpleNamespace(invoke=invoke)), \
         patch.object(nodes, "put_cache_sync", return_value=None):
        result = nodes._resolve_pronoun(query, history)
    if failures:
        raise RuntimeError("改写模型调用失败，不能将回退原句作为成功实验") from failures[0]
    if len(calls) != 1:
        raise RuntimeError(f"预期每次一次真实改写调用，实际 {len(calls)}")
    return result


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
    args = parse_args("results_rewrite")
    data = json.loads(EVAL_SET.read_text(encoding="utf-8"))
    queries = [q for q in data["queries"] if q["type"] == "multi_turn"]
    if args.limit:
        queries = queries[:args.limit]
    if args.dry_run:
        print(json.dumps({"queries": len(queries), "repeats": args.repeats,
                          "variants": ["raw", "rewrite_on", "oracle"], "api_calls": 0}))
        return
    from app.config import settings
    corpus = preflight(queries)
    reserve_output(args.output)
    previous_cache = settings.LLM_CACHE_ENABLED
    settings.LLM_CACHE_ENABLED = False
    out = {"raw": [], "rewrite_on": [], "oracle": [], "rewrites": [], "status": "running",
           "metadata": {**corpus, "repeats": args.repeats, "seed": args.seed, "metric_version": 2,
                        "rewrite_scope": "forced rewrite helper, not production trigger coverage",
                        "latency_scope": "cold embedding + ES + warm BGE + rewrite LLM (rewrite_on only)",
                        "llm_cache_enabled": False}}
    rng = random.Random(args.seed)
    try:
        warmup()
        for repeat in range(1, args.repeats + 1):
            for q in queries:
                variants = ["raw", "rewrite_on", "oracle"]
                rng.shuffle(variants)
                for name in variants:
                    query = q["oracle"] if name == "oracle" else q["query"]
                    rewrite_ms = 0.0
                    if name == "rewrite_on":
                        started = time.perf_counter()
                        query = rewrite_with_evidence(query, f"用户: {q['context']}")
                        rewrite_ms = (time.perf_counter() - started) * 1000
                        out["rewrites"].append({"id": q["id"], "repeat": repeat, "raw": q["query"],
                                                "rewritten": query, "rewrite_ms": rewrite_ms})
                    row = eval_one(query, q)
                    row.update(repeat=repeat, rewrite_ms=rewrite_ms)
                    row["latency_ms"] += rewrite_ms
                    out[name].append(row)
                    args.output.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
                    print(f"repeat={repeat} {q['id']} {name}: recall={row['recall12']:.3f} "
                          f"MRR={row['mrr']:.3f} nDCG={row['ndcg5']:.3f}", flush=True)
        out["rewrite_attempts"] = len(out["rewrites"])
        out["summary"] = [summarize(out[name], name) for name in ("raw", "rewrite_on", "oracle")]
        out["by_repeat"] = {str(rep): [summarize([r for r in out[name] if r["repeat"] == rep], name)
                                       for name in ("raw", "rewrite_on", "oracle")]
                            for rep in range(1, args.repeats + 1)}
        out["status"] = "complete"
        args.output.write_text(json.dumps(out, ensure_ascii=False, indent=2), encoding="utf-8")
    finally:
        settings.LLM_CACHE_ENABLED = previous_cache
    print(json.dumps(out["summary"], ensure_ascii=False, indent=2))
    print(f"结果已写入 {args.output}")


if __name__ == "__main__":
    main()
