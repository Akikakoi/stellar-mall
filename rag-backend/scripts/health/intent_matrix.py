"""实验 2：意图分类混淆矩阵（16 类 × 10 条）。

直接调用 INTENT_CLASSIFICATION_PROMPT（与线上一致的 prompt 与温度），
绕过缓存与规则快路径——测的是纯 LLM 分类的混淆情况。
规则快路径的命中情况单独统计（ predicting 时先跑规则，命中记为 rule-hit ）。

用法：cd rag-backend && python scripts/health/intent_matrix.py
输出：scripts/health/results_intent_matrix.json + 控制台混淆矩阵
"""
import json
import re
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from app.config import settings  # noqa: E402
from app.agent.prompts import INTENT_CLASSIFICATION_PROMPT  # noqa: E402
from app.agent.nodes import _INTENT_RULES  # noqa: E402
from app.rag.llm import get_langchain_chat  # noqa: E402
from langchain_core.messages import SystemMessage, HumanMessage  # noqa: E402

HERE = Path(__file__).resolve().parent
OUT = HERE / "results_intent_matrix.json"


def rule_hit(query: str) -> str | None:
    for rule_intent, pattern in _INTENT_RULES:
        if pattern.search(query):
            return rule_intent
    return None


def classify(query: str) -> tuple[str, float]:
    llm = get_langchain_chat()
    prompt = INTENT_CLASSIFICATION_PROMPT.format(history="（无）", query=query)
    for attempt in range(2):
        try:
            resp = llm.invoke([SystemMessage(content="你是一个严格的JSON输出助手。"),
                               HumanMessage(content=prompt)])
            content = resp.content if hasattr(resp, "content") else str(resp)
            content = content.strip()
            if content.startswith("```json"):
                content = content[7:]
            if content.endswith("```"):
                content = content[:-3]
            m = re.search(r'\{.*\}', content, re.S)
            result = json.loads(m.group(0) if m else content)
            return result.get("intent", "other"), float(result.get("confidence", 0.5))
        except Exception:
            if attempt == 1:
                return "parse_error", 0.0
            time.sleep(1)
    return "parse_error", 0.0


def main():
    data = json.loads((HERE / "intent_eval_set.json").read_text(encoding="utf-8"))
    intents = data["intents"]
    jobs = []  # (expected, query)
    for case in data["cases"]:
        for q in case["queries"]:
            jobs.append((case["intent"], q))
    print(f"共 {len(jobs)} 条，4 线程并发分类（DeepSeek {settings.LLM_MODEL_NAME}）\n")

    def _run(job):
        expected, q = job
        t0 = time.perf_counter()
        pred, conf = classify(q)
        return {"expected": expected, "query": q, "predicted": pred,
                "confidence": conf, "rule_hit": rule_hit(q),
                "latency_ms": (time.perf_counter() - t0) * 1000}

    with ThreadPoolExecutor(max_workers=4) as ex:
        rows = list(ex.map(_run, jobs))

    # 混淆矩阵
    idx = {name: i for i, name in enumerate(intents)}
    matrix = [[0] * len(intents) for _ in intents]
    for r in rows:
        p = r["predicted"] if r["predicted"] in idx else "other"
        matrix[idx[r["expected"]]][idx[p]] += 1

    per_intent = []
    for name in intents:
        row = [r for r in rows if r["expected"] == name]
        correct = sum(1 for r in row if r["predicted"] == name)
        per_intent.append({
            "intent": name, "n": len(row), "correct": correct,
            "accuracy": round(correct / len(row), 3) if row else 0,
        })

    # 最常见混淆对
    confusions = []
    for i, name in enumerate(intents):
        for j, pname in enumerate(intents):
            if i != j and matrix[i][j] > 0:
                confusions.append({"expected": name, "predicted": pname,
                                   "count": matrix[i][j]})
    confusions.sort(key=lambda x: -x["count"])

    rule_hits = sum(1 for r in rows if r["rule_hit"])
    result = {
        "model": settings.LLM_MODEL_NAME,
        "total": len(rows),
        "overall_accuracy": round(sum(1 for r in rows if r["predicted"] == r["expected"]) / len(rows), 3),
        "rule_fast_path_hits": rule_hits,
        "avg_latency_ms": round(sum(r["latency_ms"] for r in rows) / len(rows)),
        "per_intent": per_intent,
        "top_confusions": confusions[:15],
        "matrix": {"intents": intents, "rows_expected_cols_predicted": matrix},
        "detail": rows,
    }
    OUT.write_text(json.dumps(result, ensure_ascii=False, indent=1), encoding="utf-8")

    print(f"整体准确率: {result['overall_accuracy']:.1%}  "
          f"规则快路径命中: {rule_hits}/{len(rows)}  平均延迟 {result['avg_latency_ms']}ms\n")
    print(f"{'意图':<18}{'准确率':>8}")
    for p in per_intent:
        print(f"{p['intent']:<18}{p['accuracy']:>7.0%}  ({p['correct']}/{p['n']})")
    print("\nTop 混淆（期望 → 预测）:")
    for c in result["top_confusions"][:10]:
        print(f"  {c['expected']} → {c['predicted']}: {c['count']} 次")
    print(f"\n结果已写入 {OUT}")


if __name__ == "__main__":
    main()
