"""把消融实验的原始产物压成"可入库"的精简版。

原始 JSON 里有一大半是复现用不上的过程快照：
  agent_e2e : sources / tool_trace / events / graph_result / tool_results
              （单条 row 约 116KB，96% 是这些过程数据）
  cache     : seed.snapshot 全量快照、phases[].detail[].result 完整响应

精简后保留"结论能被核对"的字段：指标、耗时、命中层、judge 分数与理由、
工具名与调用链、错误信息。原始文件备份到 _raw/（已 gitignore），不丢数据。

用法：
    python scripts/ablation/compact_results.py --dry-run   # 只看体积变化
    python scripts/ablation/compact_results.py             # 原地压缩
"""
from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path

BASE = Path(__file__).resolve().parent
RAW_DIR = BASE / "_raw"

# agent_e2e 单条 row 保留的字段（其余过程快照丢弃）
AGENT_KEEP = [
    "order", "repeat", "group", "id", "variant", "item", "reference_conflict",
    "answer", "score", "reason", "judge_raw", "status", "errors", "intent",
    "latency_ms", "judge_latency_ms", "tool_used", "uses_react",
    "rerank_attempts", "real_rerank_calls", "kb_tool_used",
    "nonempty_without_kb", "rerank_exercised",
]

# 追踪类字段 → 只留"有哪些工具、按什么顺序"
def _trace_tools(trace) -> list:
    out = []
    for step in trace or []:
        if isinstance(step, dict):
            name = step.get("tool") or step.get("name") or step.get("action")
            if name:
                out.append(name)
    return out


def _head_sources(sources, n: int = 3) -> list:
    out = []
    for s in (sources or [])[:n]:
        if isinstance(s, dict):
            out.append({k: s.get(k) for k in ("doc_id", "doc_name", "name", "title", "score") if k in s})
        else:
            out.append(str(s)[:80])
    return out


def compact_agent(doc: dict) -> dict:
    rows = []
    for row in doc.get("rows") or []:
        slim = {k: row.get(k) for k in AGENT_KEEP if k in row}
        slim["sources_count"] = len(row.get("sources") or [])
        slim["sources_head"] = _head_sources(row.get("sources"))
        slim["tool_path"] = _trace_tools(row.get("tool_trace"))
        slim["events_count"] = len(row.get("events") or [])
        gr = row.get("graph_result")
        if isinstance(gr, dict):
            slim["graph_intent"] = gr.get("intent")
            slim["graph_answer_len"] = len(str(gr.get("answer") or ""))
        rows.append(slim)
    doc["rows"] = rows
    doc["compacted"] = "去掉 sources/tool_trace/events/graph_result/tool_results 全量过程快照，保留指标与调用链摘要"
    return doc


def compact_cache(doc: dict) -> dict:
    seed = doc.get("seed")
    if isinstance(seed, dict) and "snapshot" in seed:
        snap = seed.get("snapshot")
        doc["seed"] = {
            "sha256": seed.get("sha256"),
            "source": seed.get("source"),
            "snapshot_bytes": len(json.dumps(snap, ensure_ascii=False)) if snap is not None else 0,
            "snapshot_omitted": True,
        }
    for phase in doc.get("phases") or []:
        detail = phase.get("detail")
        if isinstance(detail, list):
            phase["detail"] = [
                {
                    "query": d.get("query"),
                    "ok": d.get("ok"),
                    "elapsed_ms": d.get("elapsed_ms"),
                    "latency_ms": d.get("latency_ms"),
                    "drain_ms": d.get("drain_ms"),
                    "layers": d.get("layers"),
                    "errors": d.get("errors"),
                    "warnings": d.get("warnings"),
                    "result_omitted": bool(d.get("result")),
                }
                for d in detail
                if isinstance(d, dict)
            ]
    doc["compacted"] = "seed.snapshot 与 phases[].detail[].result 已省略，保留分层命中与耗时"
    return doc


# 文件名前缀 → 压缩器
HANDLERS = [
    ("results_agent_e2e", compact_agent),
    ("results_cache", compact_cache),
]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true", help="只打印体积变化，不写文件")
    ap.add_argument("--files", nargs="*", help="指定文件名，默认处理所有匹配 *_2*.json")
    args = ap.parse_args()

    targets = []
    for p in sorted(BASE.glob("results_*_2026*.json")):
        if args.files and p.name not in args.files:
            continue
        handler = next((h for prefix, h in HANDLERS if p.name.startswith(prefix)), None)
        if handler:
            targets.append((p, handler))

    if not targets:
        print("没有匹配的文件")
        return

    total_before = total_after = 0
    for path, handler in targets:
        before = path.stat().st_size
        doc = json.loads(path.read_text(encoding="utf-8"))
        doc = handler(doc)
        # separators 去掉多余空格，indent 保留 1 空格便于 git diff
        text = json.dumps(doc, ensure_ascii=False, indent=1, separators=(",", ": "))
        after = len(text.encode("utf-8"))
        total_before += before
        total_after += after
        print(f"{path.name:52s} {before/1024:9.1f}KB -> {after/1024:8.1f}KB")
        if args.dry_run:
            continue
        RAW_DIR.mkdir(exist_ok=True)
        backup = RAW_DIR / path.name
        if not backup.exists():
            shutil.copy2(path, backup)
        path.write_text(text + "\n", encoding="utf-8")

    print(f"{'合计':52s} {total_before/1024:9.1f}KB -> {total_after/1024:8.1f}KB")
    if args.dry_run:
        print("(dry-run，未写入)")
    else:
        print(f"原始文件已备份到 {RAW_DIR.relative_to(BASE.parent.parent)}")


if __name__ == "__main__":
    main()
