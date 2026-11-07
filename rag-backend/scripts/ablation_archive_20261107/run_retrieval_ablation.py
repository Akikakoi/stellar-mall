"""检索链路消融实验：BM25 单路 / 向量单路 / RRF 混合（无精排）/ RRF 混合 + Rerank。

复用 app.rag 现有实现保证与线上一致：
  - 向量/BM25 查询与 RRF 融合逻辑镜像 retriever.hybrid_retrieve（单路时去掉另一路）；
  - Rerank 直接调用 retriever.rerank（BGE CrossEncoder，CPU）；
  - 评测指标（文档级）：Recall@12、MRR、nDCG@5、延迟。

用法（cwd 必须是 rag-backend 根目录）：
  python scripts/ablation/run_retrieval_ablation.py
输出：带时间戳的新 JSON；--dry-run 离线检查，--repeats 默认两轮。
"""
import argparse
import json
import math
import random
import sys
import time
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent.parent  # rag-backend/
sys.path.insert(0, str(ROOT))


HERE = Path(__file__).resolve().parent
EVAL_SET = HERE / "eval_set.json"

TOP_K = 12
RERANK_TOP_K = TOP_K
RRF_K = 60


def document_metrics(keys, relevant):
    ranked = list(dict.fromkeys(key if key is not None else f"__unknown_{i}"
                                for i, key in enumerate(keys)))
    rel_set = set(relevant)
    if not rel_set:
        raise ValueError("每个问题必须有相关文档标注")
    gains = [int(key in rel_set) for key in ranked]
    hits = [key for key in ranked if key in rel_set]
    dcg = sum(g / math.log2(i + 2) for i, g in enumerate(gains[:5]))
    idcg = sum(1 / math.log2(i + 2) for i in range(min(len(rel_set), 5)))
    return {"recall12": len(hits) / len(rel_set),
            "mrr": next((1 / (i + 1) for i, g in enumerate(gains) if g), 0.0),
            "ndcg5": dcg / idcg, "hit_docs": hits, "ranked_docs": ranked}


def parse_args(stem):
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path,
                        default=HERE / f"{stem}_{datetime.now():%Y%m%d_%H%M%S_%f}.json")
    parser.add_argument("--repeats", type=int, default=2)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--seed", type=int, default=20261107)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    if args.repeats < 1 or args.limit < 0:
        parser.error("repeats 必须为正数，limit 不可为负数")
    return args


def preflight(queries):
    import hashlib
    from elasticsearch.helpers import scan
    from app.config import settings
    from app.rag.es_client import get_es, KB_ES_INDEX
    if settings.RETRIEVER_TOP_K != TOP_K:
        raise ValueError("本实验固定12条候选，当前配置不匹配")
    labels, chunks = set(), []
    for hit in scan(get_es(), index=KB_ES_INDEX,
                    query={"query": {"match_all": {}}, "_source": ["doc_name", "name", "content"]}):
        labels.add(_doc_key(hit["_source"]))
        chunks.append(json.dumps([hit["_id"], hit["_source"]], ensure_ascii=False, sort_keys=True))
    missing = sorted({key for q in queries for key in q["relevant"]} - labels)
    if missing or not chunks:
        raise RuntimeError(f"知识库为空或缺少标注文档: {missing}")
    return {"kb_chunks": len(chunks),
            "kb_sha256": hashlib.sha256("\n".join(sorted(chunks)).encode()).hexdigest(),
            "eval_sha256": hashlib.sha256(EVAL_SET.read_bytes()).hexdigest(),
            "embedding_model": settings.DASHSCOPE_EMBEDDING_MODEL,
            "reranker_model": settings.RERANKER_MODEL_NAME,
            "reranker_device": settings.RERANKER_DEVICE,
            "llm_model": settings.LLM_MODEL_NAME,
            "temperature": settings.LLM_TEMPERATURE}


def warmup():
    from app.config import settings
    from app.rag.retriever import _get_reranker, _rerank_with_cross_encoder
    from langchain_core.documents import Document
    settings.RERANKER_ENABLED = True
    model = _get_reranker()
    if model is None:
        raise RuntimeError("真实 BGE 不可用，禁止以余弦兜底冒充精排")
    _rerank_with_cross_encoder(model, "预热", [Document(page_content="手机产品参数")], 1)


def clear_embedding_query(query):
    from app.rag.embeddings import get_embeddings
    embeddings = get_embeddings()
    with embeddings._lock:
        embeddings._cache.pop(query, None)


def reserve_output(path):
    with path.open("x", encoding="utf-8") as stream:
        json.dump({"status": "running"}, stream)


def _doc_key(chunk_meta: dict) -> str | None:
    """chunk → 文档级标签：KB 文档用 doc_name，SPU 文档用 SPU:商品名。"""
    if chunk_meta.get("doc_name"):
        return chunk_meta["doc_name"]
    if chunk_meta.get("name"):
        return f"SPU:{chunk_meta['name']}"
    return None


def retrieve(query: str, mode: str, use_rerank: bool, tags_filter=None):
    """mode: bm25 | vector | rrf。返回 (docs, 各阶段耗时)。"""
    from app.rag.es_client import KB_ES_INDEX, es_tags_filter, get_es
    from app.rag.embeddings import get_embeddings
    from app.rag.retriever import _get_reranker, _rerank_with_cross_encoder
    if mode not in {"bm25", "vector", "rrf"}:
        raise ValueError(f"未知模式: {mode}")
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

    if any(result.get("timed_out") or result.get("_shards", {}).get("failed", 0) for result in results):
        raise RuntimeError("ES 查询超时或分片失败")
    retrieve_ms = (time.perf_counter() - t0) * 1000 - emb_ms

    if use_rerank and docs:
        t = time.perf_counter()
        model = _get_reranker()
        if model is None:
            raise RuntimeError("真实 BGE 不可用，禁止精排消融静默降级")
        scored = _rerank_with_cross_encoder(model, query, docs, RERANK_TOP_K)
        rerank_ms = (time.perf_counter() - t) * 1000
        docs = [d for d, _ in scored]
    else:
        rerank_ms = 0.0

    return docs, {"embed_ms": emb_ms, "retrieve_ms": retrieve_ms, "rerank_ms": rerank_ms}


def eval_variant(queries, mode, use_rerank):
    from collections import OrderedDict
    if not queries:
        raise ValueError("拒绝空评测")
    per_query = []
    for qi, q in enumerate(queries):
        if mode != "bm25":
            clear_embedding_query(q["query"])
        docs, timing = retrieve(q["query"], mode, use_rerank)
        keys = [_doc_key(d.metadata) for d in docs]
        row = {"id": q["id"], "type": q["type"], "query": q["query"],
               **document_metrics(keys, q["relevant"]), "chunk_doc_keys": keys,
               "latency_ms": sum(timing.values()), **timing}
        per_query.append(row)
        print(f"  [{qi+1}/{len(queries)}] {q['id']} recall={row['recall12']:.3f} "
              f"mrr={row['mrr']:.3f} ndcg5={row['ndcg5']:.3f} "
              f"lat={row['latency_ms']:.0f}ms", flush=True)

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


def main():
    args = parse_args("results_retrieval")
    queries = json.loads(EVAL_SET.read_text(encoding="utf-8"))["queries"]
    if args.limit:
        queries = queries[:args.limit]
    variants = [("bm25_only", "bm25", False), ("vector_only", "vector", False),
                ("rrf_hybrid", "rrf", False), ("rrf_hybrid_rerank", "rrf", True)]
    if args.dry_run:
        print(json.dumps({"queries": len(queries), "repeats": args.repeats,
                          "variants": [v[0] for v in variants], "api_calls": 0}))
        return
    corpus = preflight(queries)
    reserve_output(args.output)
    warmup()
    results = {"metadata": {**corpus, "repeats": args.repeats, "seed": args.seed,
                            "metric_version": 2, "candidate_budget": TOP_K,
                            "metric_scope": "12 chunks, deduplicate documents before all ranking metrics",
                            "latency_scope": "cold query embedding + ES + warm BGE; no HTTP"},
               "status": "running", "variants": {}}
    rng = random.Random(args.seed)
    for repeat in range(1, args.repeats + 1):
        order = list(variants)
        rng.shuffle(order)
        for name, mode, use_rerank in order:
            print(f"== repeat={repeat} variant={name} ==", flush=True)
            result = eval_variant(queries, mode, use_rerank)
            for row in result["per_query"]:
                row["repeat"] = repeat
            results["variants"].setdefault(name, []).append(result)
            args.output.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    results["status"] = "complete"
    args.output.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    for name, repetitions in results["variants"].items():
        for rep, result in enumerate(repetitions, 1):
            print(name, rep, json.dumps(result["summary"], ensure_ascii=False))
    print(f"结果已写入 {args.output}")


if __name__ == "__main__":
    main()
