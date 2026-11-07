"""离线验证实验口径，避免重复片段与计时错误污染真实模型结果。"""
import json
import math
from pathlib import Path
import subprocess
import sys
from types import ModuleType, SimpleNamespace
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import run_retrieval_ablation as retrieval


class RetrievalAblationTests(unittest.TestCase):
    def test_duplicate_relevant_chunks_do_not_inflate_ndcg(self):
        result = retrieval.document_metrics(["a"] * 12, ["a"])
        self.assertEqual(result["ndcg5"], 1.0)
        self.assertEqual(result["hit_docs"], ["a"])
        self.assertEqual(result["recall12"], 1.0)

    def test_nonrelevant_duplicates_are_deduplicated_before_ranking(self):
        result = retrieval.document_metrics(["x", "x", "a", "a"], ["a"])
        self.assertEqual(result["mrr"], .5)
        self.assertAlmostEqual(result["ndcg5"], 1 / math.log2(3))

    def test_missing_metadata_does_not_disappear(self):
        result = retrieval.document_metrics([None, None, "a"], ["a"])
        self.assertAlmostEqual(result["mrr"], 1 / 3)

    def test_no_hits_and_missing_labels(self):
        self.assertEqual(retrieval.document_metrics([], ["a"])["ndcg5"], 0)
        with self.assertRaises(ValueError):
            retrieval.document_metrics(["a"], [])

    def test_metrics_stay_in_unit_interval(self):
        import random
        rng = random.Random(7)
        for _ in range(200):
            result = retrieval.document_metrics(rng.choices(["a", "b", "c", None], k=12), ["a", "b"])
            for metric in ("recall12", "mrr", "ndcg5"):
                self.assertGreaterEqual(result[metric], 0)
                self.assertLessEqual(result[metric], 1)

    def test_embedding_time_is_counted_once(self):
        es_module = ModuleType("app.rag.es_client")
        es_module.KB_ES_INDEX = "test"
        es_module.es_tags_filter = lambda _: []
        es_module.get_es = lambda: SimpleNamespace(search=lambda **_: {"hits": {"hits": []}})
        emb_module = ModuleType("app.rag.embeddings")
        emb_module.get_embeddings = lambda: SimpleNamespace(embed_query=lambda _: [1.0])
        reranker_module = ModuleType("app.rag.retriever")
        reranker_module._get_reranker = lambda: None
        reranker_module._rerank_with_cross_encoder = lambda *_: []
        modules = {"app.rag.es_client": es_module, "app.rag.embeddings": emb_module,
                   "app.rag.retriever": reranker_module}
        with patch.dict(sys.modules, modules), patch.object(retrieval.time, "perf_counter", side_effect=[0, 1, 3, 7]):
            _, timing = retrieval.retrieve("query", "vector", False)
        self.assertEqual(timing["embed_ms"], 2000)
        self.assertEqual(timing["retrieve_ms"], 5000)
        self.assertEqual(sum(timing.values()), 7000)

    def test_cli_dry_runs_are_nonempty(self):
        for script, count in (("run_retrieval_ablation.py", 40), ("run_rewrite_ablation.py", 10)):
            process = subprocess.run([sys.executable, str(HERE / script), "--dry-run"],
                                     text=True, capture_output=True, check=True)
            plan = json.loads(process.stdout)
            self.assertEqual(plan["queries"], count)
            self.assertEqual(plan["repeats"], 2)
            self.assertEqual(plan["api_calls"], 0)

    def test_rewrite_failure_cannot_be_scored_as_raw_query(self):
        import run_rewrite_ablation as rewrite
        parent = ModuleType("app.agent")
        nodes = ModuleType("app.agent.nodes")
        parent.nodes = nodes
        nodes.put_cache_sync = lambda **_: None

        def fail(_):
            raise RuntimeError("API unavailable")

        nodes.get_langchain_chat = lambda: SimpleNamespace(invoke=fail)

        def resolver(query, history):
            try:
                return nodes.get_langchain_chat().invoke([query, history])
            except RuntimeError:
                return query

        nodes._resolve_pronoun = resolver
        with patch.dict(sys.modules, {"app.agent": parent, "app.agent.nodes": nodes}):
            with self.assertRaisesRegex(RuntimeError, "改写模型调用失败"):
                rewrite.rewrite_with_evidence("它多少钱", "手机")

    def test_existing_result_cannot_be_overwritten(self):
        with self.assertRaises(FileExistsError):
            retrieval.reserve_output(HERE / "eval_set.json")


if __name__ == "__main__":
    unittest.main()
