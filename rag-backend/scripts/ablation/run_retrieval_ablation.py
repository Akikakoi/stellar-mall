"""检索链路消融实验：BM25 单路 / 向量单路 / RRF 混合（无精排）/ RRF 混合 + Rerank。

复用 app.rag 现有实现保证与线上一致：
  - 向量/BM25 查询与 RRF 融合逻辑镜像 retriever.hybrid_retrieve（单路时去掉另一路）；
  - Rerank 直接调用 retriever.rerank（BGE CrossEncoder，CPU）；
  - 评测指标（文档级）：Recall@12、MRR、nDCG@5、延迟。

用法（cwd 必须是 rag-backend 根目录）：
  python scripts/ablation/run_retrieval_ablation.py
输出：scripts/ablation/results_retrieval.json + 控制台对照表
"""
import json
import os
import sys
import time
from pathlib import Path

# L1 缓存 Redis 端口对齐本机 docker（6380），消融检索不依赖缓存，仅避免误连
os.environ.setdefault("LLM_CACHE_REDIS_URL", "redis://127.0.0.1:6380/11")

ROOT = Path(__file__).resolve().parent.parent.parent  # rag-backend/
sys.path.insert(0, str(ROOT))

from app.rag.es_client import KB_ES_INDEX, es_tags_filter, get_es  # noqa: E402
from app.rag.embeddings import get_embeddings  # noqa: E402
from app.rag.retriever import rerank  # noqa: E402
from app.config import settings  # noqa: E402

HERE = Path(__file__).resolve().parent
EVAL_SET = HERE / "eval_set.json"
OUT = HERE / "results_retrieval.json"

TOP_K = settings.RETRIEVER_TOP_K          # 12
RERANK_TOP_K = TOP_K                      # 精排后仍评 top12，保证可比
RRF_K = 60


def _doc_key(chunk_meta: dict) -> str | None:
    """chunk → 文档级标签：KB 文档用 doc_name，SPU 文档用 SPU:商品名。"""
    if chunk_meta.get("doc_name"):
        return chunk_meta["doc_name"]
    if chunk_meta.get("name"):
        return f"SPU:{chunk_meta['name']}"
    return None


def retrieve(query: str, mode: str, use_rerank: bool, tags_filter=None):
    """mode: bm25 | vector | rrf。返回 (docs, 各阶段耗时)。"""
    es = get_es()
    t0 = time.perf_counter()
    tag_flt = es_tags_filter(tags_filter)
    src_excl = {"excludes": ["vec"]}
    emb_ms = 0.0

    def _search_knn():
        nonlocal emb_ms
        t = time.perf_counter()
        q_emb = get_embeddings().embed_query(query)
        emb_ms = (time.perf_counter() - t) * 1000
        return es.search(
            index=KB_ES_INDEX,
            knn={"field": "vec", "query_vector": q_emb, "k": TOP_K,
                 "num_candidates": max(TOP_K * 5, 50), "filter": tag_flt},
            size=TOP_K, _source=src_excl,
        )

    def _search_bm25():
        return es.search(
            index=KB_ES_INDEX,
            query={"bool": {"must": [{"match": {"content": {"query": query}}}],
                            "filter": tag_flt}},
            size=TOP_K, _source=src_excl,
        )

    results = []
    if mode == "vector":
        results = [_search_knn()]
    elif mode == "bm25":
        results = [_search_bm25()]
    else:
        results = [_search_knn(), _search_bm25()]

    if mode == "rrf":
        scores: dict = {}
        docs_by_id: dict = {}
        for res in results:
            for rank, hit in enumerate(res["hits"]["hits"]):
                hid = hit["_id"]
                scores[hid] = scores.get(hid, 0.0) + 1.0 / (RRF_K + rank + 1)
                if hid not in docs_by_id:
                    src = hit["_source"]
                    from langchain_core.documents import Document
                    docs_by_id[hid] = Document(
                        page_content=src.get("content", ""),
                        metadata={k: v for k, v in src.items() if k != "content"},
                    )
        ranked_ids = sorted(scores, key=lambda i: scores[i], reverse=True)[:TOP_K]
        docs = [docs_by_id[i] for i in ranked_ids]
    else:
        from langchain_core.documents import Document
        docs = [
            Document(
                page_content=h["_source"].get("content", ""),
                metadata={k: v for k, v in h["_source"].items() if k != "content"},
            )
            for h in results[0]["hits"]["hits"]
        ]

    retrieve_ms = (time.perf_counter() - t0) * 1000

    if use_rerank:
        t = time.perf_counter()
        scored = rerank(query, docs, RERANK_TOP_K)
        rerank_ms = (time.perf_counter() - t) * 1000
        docs = [d for d, _ in scored]
    else:
        rerank_ms = 0.0

    return docs, {"embed_ms": emb_ms, "retrieve_ms": retrieve_ms, "rerank_ms": rerank_ms}


def eval_variant(queries, mode, use_rerank, warmup=2):
    from collections import OrderedDict
    per_query = []
    for qi, q in enumerate(queries):
        docs, timing = retrieve(q["query"], mode, use_rerank)
        keys = [_doc_key(d.metadata) for d in docs]
        rel_set = set(q["relevant"])

        # 文档级相关性：去重后命中文档数 / 标注文档数
        gains = [1.0 if k in rel_set else 0.0 for k in keys]
        n_rel_retrieved = len({k for k in keys if k in rel_set})
        n_rel_total = len(rel_set)
        recall = n_rel_retrieved / n_rel_total if n_rel_total else 0.0

        mrr = 0.0
        for i, g in enumerate(gains):
            if g > 0:
                mrr = 1.0 / (i + 1)
                break

        dcg = sum(g / _log2(i + 2) for i, g in enumerate(gains[:5]))
        idcg = sum(1.0 / _log2(i + 2) for i in range(min(n_rel_total, 5)))
        ndcg5 = dcg / idcg if idcg > 0 else 0.0

        per_query.append({
            "id": q["id"], "type": q["type"],
            "recall12": recall, "mrr": mrr, "ndcg5": ndcg5,
            "hit_docs": [k for k in keys if k in rel_set],
            "latency_ms": timing["embed_ms"] + timing["retrieve_ms"] + timing["rerank_ms"],
            "embed_ms": timing["embed_ms"], "retrieve_ms": timing["retrieve_ms"],
            "rerank_ms": timing["rerank_ms"],
        })
        print(f"  [{qi+1}/{len(queries)}] {q['id']} recall={recall:.2f} mrr={mrr:.2f} "
              f"ndcg5={ndcg5:.2f} lat={per_query[-1]['latency_ms']:.0f}ms")

    def _avg(field):
        vals = [r[field] for r in per_query]
        return sum(vals) / len(vals) if vals else 0.0

    lats = sorted(r["latency_ms"] for r in per_query)
    summary = {
        "recall12": _avg("recall12"), "mrr": _avg("mrr"), "ndcg5": _avg("ndcg5"),
        "latency_avg_ms": _avg("latency_ms"),
        "latency_p50_ms": lats[len(lats) // 2],
        "latency_p95_ms": lats[min(len(lats) - 1, int(len(lats) * 0.95))],
        "embed_ms_avg": _avg("embed_ms"), "retrieve_ms_avg": _avg("retrieve_ms"),
        "rerank_ms_avg": _avg("rerank_ms"),
    }
    # 分类型小计
    by_type = OrderedDict()
    for t in ("exact", "semantic", "multi_constraint", "multi_turn"):
        rows = [r for r in per_query if r["type"] == t]
        if rows:
            by_type[t] = {
                "n": len(rows),
                "recall12": sum(r["recall12"] for r in rows) / len(rows),
                "mrr": sum(r["mrr"] for r in rows) / len(rows),
                "ndcg5": sum(r["ndcg5"] for r in rows) / len(rows),
            }
    return {"summary": summary, "by_type": by_type, "per_query": per_query}


def _log2(x):
    import math
    return math.log2(x)


def main():
    eval_data = json.loads(EVAL_SET.read_text(encoding="utf-8"))
    queries = eval_data["queries"]

    # 预热：embedding 客户端 + ES 连接（不计入指标）
    print("== 预热（embedding / ES / 首次查询） ==")
    retrieve("预热查询：星耀手机", "rrf", False)
    print("预热完成\n")

    variants = [
        ("bm25_only", "bm25", False),
        ("vector_only", "vector", False),
        ("rrf_hybrid", "rrf", False),
        ("rrf_hybrid_rerank", "rrf", True),
    ]
    results = {}
    for name, mode, use_rerank in variants:
        print(f"== 变体: {name} ==")
        results[name] = eval_variant(queries, mode, use_rerank)
        s = results[name]["summary"]
        print(f"  --> recall@12={s['recall12']:.3f}  mrr={s['mrr']:.3f}  ndcg@5={s['ndcg5']:.3f}  "
              f"lat_avg={s['latency_avg_ms']:.0f}ms (rerank={s['rerank_ms_avg']:.0f}ms)\n")

    OUT.write_text(json.dumps(results, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"结果已写入 {OUT}")

    # 控制台对照表
    print("\n===== 检索消融对照表（40 条，文档级指标） =====")
    print(f"{'变体':<20}{'recall@12':>10}{'MRR':>8}{'nDCG@5':>8}{'P50延迟':>10}{'P95延迟':>10}")
    for name in results:
        s = results[name]["summary"]
        print(f"{name:<20}{s['recall12']:>10.3f}{s['mrr']:>8.3f}{s['ndcg5']:>8.3f}"
              f"{s['latency_p50_ms']:>9.0f}ms{s['latency_p95_ms']:>9.0f}ms")

    print("\n===== 分 query 类型 recall@12 =====")
    types = ("exact", "semantic", "multi_constraint", "multi_turn")
    print(f"{'变体':<20}" + "".join(f"{t:>18}" for t in types))
    for name in results:
        bt = results[name]["by_type"]
        print(f"{name:<20}" + "".join(f"{bt[t]['recall12']:>18.3f}" for t in types))


if __name__ == "__main__":
    main()
