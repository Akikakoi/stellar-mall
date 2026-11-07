"""Stdlib-only regression tests. Never import the real app, generate tokens or call APIs."""
import ast
from contextlib import contextmanager, redirect_stdout
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
from types import ModuleType, SimpleNamespace
import unittest
from unittest.mock import Mock, patch

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
SPEC = importlib.util.spec_from_file_location("agent_ablation_under_test", HERE / "run_agent_e2e_ablation.py")
harness = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(harness)


def load_function(path, name, namespace):
    """Exercise the actual production call sites via AST without importing dependencies."""
    tree = ast.parse(path.read_text(encoding="utf-8"))
    node = next(n for n in tree.body if isinstance(n, ast.FunctionDef) and n.name == name)
    node.decorator_list = []
    exec(compile(ast.Module(body=[node], type_ignores=[]), str(path), "exec"), namespace)
    return namespace[name]


def fresh_row():
    return {"errors": [], "rerank_trace": [], "rerank_attempts": 0, "real_rerank_calls": 0,
            "rewrite_trace": [], "tool_trace": [], "events": []}


@contextmanager
def fake_runtime():
    def module(name, **values):
        result = ModuleType(name)
        result.__dict__.update(values)
        return result

    settings = SimpleNamespace(
        RERANKER_ENABLED=False, QUERY_REWRITE_ENABLED=False, LLM_CACHE_ENABLED=True,
        QUERY_CACHE_TTL_SECONDS=300, AGENT_ENABLED=False,
        RERANK_TOP_K=2, RETRIEVER_TOP_K=4,
        RERANKER_MODEL_NAME="bge-test", RERANKER_USE_FP16=False,
        RERANKER_DEVICE="cpu", RERANKER_BATCH_SIZE=2,
        LLM_MODEL_NAME="fake", LLM_TEMPERATURE=0,
    )
    docs = [SimpleNamespace(page_content=f"doc {i}", metadata={"id": i}) for i in range(4)]
    faq = SimpleNamespace(get=Mock(return_value=("STALE FAQ", [])), put=Mock())
    old_rerank = Mock(side_effect=AssertionError("production fallback must not execute"))
    retriever = module("app.rag.retriever", rerank=old_rerank, get_query_cache=lambda: faq,
                       _docs_cache=["old cache"], _docs_cache_count=1,
                       _get_all_docs_cached=Mock(return_value=docs))

    class BaseTool:
        def __init__(self, name, fn):
            self.name, self.fn = name, fn

        def invoke(self, args):
            return self.fn(**args)

    class CachedEmbeddings:
        def __init__(self):
            self._inner = SimpleNamespace(embed_query=Mock(return_value=[1.0]))

        def embed_query(self, text):
            return [99.0]

    kb = module("app.agent.tools.kb_tool", settings=settings, rerank=old_rerank,
                hybrid_retrieve=Mock(return_value=docs), List=list, Optional=__import__("typing").Optional,
                logger=SimpleNamespace(warning=Mock()),
                _get_all_docs_cached=retriever._get_all_docs_cached,
                kb_spec_compare=Mock(return_value={"success": True, "sources": []}),
                kb_specs_for_products=Mock(return_value={"success": True, "sources": []}))
    kb._ranked_to_result = lambda query, ranked: {
        "success": True, "sources": [{"content": d.page_content, "score": s} for d, s in ranked]}
    fn = load_function(ROOT / "app/agent/tools/kb_tool.py", "kb_search_tool", vars(kb))
    kb.kb_search_tool = BaseTool("kb_search_tool", fn)
    cache = module("app.rag.llm_cache", get_cache_sync=Mock(return_value="STALE LLM"),
                   put_cache_sync=Mock(), single_flight_sync=Mock())
    nodes = module("app.agent.nodes", get_cache_sync=cache.get_cache_sync,
                   put_cache_sync=cache.put_cache_sync, get_query_cache=retriever.get_query_cache,
                   _resolve_pronoun=Mock(side_effect=lambda query, history: history + query),
                   kb_spec_compare=kb.kb_spec_compare, kb_specs_for_products=kb.kb_specs_for_products,
                   set_agent_event_emitter=Mock(return_value="emitter-token"),
                   reset_agent_event_emitter=Mock())
    chat = SimpleNamespace(invoke=Mock(return_value=SimpleNamespace(content='{"score": 4, "reason": "ok"}')))
    llm = module("app.rag.llm", get_langchain_chat=Mock(return_value=chat))
    nodes.get_langchain_chat = llm.get_langchain_chat
    embeddings = module("app.rag.embeddings", _CachedEmbeddings=CachedEmbeddings)
    graph = module("app.agent.graph")

    def run_agent(**kwargs):
        result = kb.kb_search_tool.invoke({"query": kwargs["query"], "top_k": 2})
        return {"answer": "完整回答" * 600, "intent": "product_consult", "current_tool": "kb_search",
                "tool_results": result, "sources": result.get("sources", [])}

    graph.run_agent = Mock(side_effect=run_agent)
    react = module("app.agent.react_agent", run_react_agent=Mock())
    factory = Mock(return_value=SimpleNamespace(compute_score=Mock(return_value=[0.1, 0.9, 0.2, 0.7])))
    flag = module("FlagEmbedding", FlagReranker=factory)
    rt = SimpleNamespace(settings=settings, retriever=retriever, kb=kb, cache=cache,
                         nodes=nodes, graph=graph, react=react, llm=llm, embeddings=embeddings,
                         tools=SimpleNamespace(BaseTool=BaseTool),
                         messages=SimpleNamespace(SystemMessage=SimpleNamespace, HumanMessage=SimpleNamespace),
                         token=SimpleNamespace(make_mall_user_token=Mock(return_value="FAKE-SECRET")),
                         flag=factory, docs=docs, faq=faq, chat=chat)
    modules = [retriever, kb, cache, nodes, llm, embeddings, graph, react, flag]
    with patch.dict(sys.modules, {m.__name__: m for m in modules}):
        yield rt


def job(variant="full", group="R", repeat=1):
    return {"id": "A02", "group": group, "repeat": repeat, "variant": variant,
            "order": 1, "item": dict(harness.GROUP_R[0])}


class AblationTests(unittest.TestCase):
    def test_dry_run_does_not_import_runtime_or_create_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "unused.json"
            with patch.object(harness, "load_runtime", side_effect=AssertionError("runtime import")), \
                    redirect_stdout(io.StringIO()) as stdout:
                self.assertEqual(harness.main(["--dry-run", "--limit", "1", "--output", str(output)]), 0)
            matrix = json.loads(stdout.getvalue())
            self.assertEqual(matrix["expected_rows"], 8)
            self.assertFalse(output.exists())

    def test_schedule_is_deterministic_paired_and_complete(self):
        schedule = harness.build_schedule()
        self.assertEqual(schedule, harness.build_schedule())
        self.assertEqual(len(schedule), 72)
        self.assertNotEqual(schedule, harness.build_schedule(seed=100))
        self.assertEqual((len(harness.GROUP_R), len(harness.GROUP_D)), (8, 10))
        for first, second in zip(schedule[::2], schedule[1::2]):
            self.assertEqual((first["id"], first["repeat"], first["item"]),
                             (second["id"], second["repeat"], second["item"]))
            self.assertIn("full", (first["variant"], second["variant"]))
        self.assertEqual(len(harness.build_schedule(repeats=3, limit=1)), 12)

    def test_strict_judge_rejects_invalid_json_and_non_integer_scores(self):
        invalid = ['{"score": true, "reason": "x"}', '{"score": 3.0, "reason": "x"}',
                   '{"score": "4", "reason": "x"}', '{"score": 50, "reason": "x"}',
                   '{"score": -1, "reason": "x"}', '{"score": 2, "reason": ""}',
                   '{"score": 2, "reason": "x", "extra": 1}',
                   '{"score": 2, "score": 5, "reason": "x"}',
                   '```json\n{"score": 4, "reason": "x"}\n```',
                   'prefix {"score": 4, "reason": "x"}', '[]', 'null']
        for text in invalid:
            with self.subTest(text=text), self.assertRaises((ValueError, TypeError)):
                harness.parse_judgment(text)
        for score in range(6):
            self.assertEqual(harness.parse_judgment(json.dumps({"score": score, "reason": "ok"}))["score"], score)

    def test_identity_bypasses_preloaded_reranker_and_keeps_candidates(self):
        with fake_runtime() as rt:
            backend = harness.StrictBGE(rt.settings)
            backend.model = Mock()
            before = dict(vars(rt.settings))
            original = rt.kb.rerank
            row = harness.run_sample(rt, job("no_rerank"), "FAKE-SECRET", backend)
            self.assertEqual(row["status"], "ok")
            self.assertEqual(row["real_rerank_calls"], 0)
            backend.model.compute_score.assert_not_called()
            rt.flag.assert_not_called()
            self.assertEqual(row["sources"], [{"content": "doc 0", "score": 0.5}, {"content": "doc 1", "score": 0.5}])
            self.assertEqual(row["rerank_trace"][0]["candidate_count"], 4)
            self.assertEqual(row["rerank_trace"][0]["output_count"], 2)
            self.assertEqual(rt.kb.hybrid_retrieve.call_args.kwargs["top_k"], 4)
            self.assertEqual(vars(rt.settings), before)
            self.assertIs(rt.retriever.rerank, original)
            self.assertIs(rt.kb.rerank, original)
            self.assertTrue(row["kb_tool_used"])

    def test_full_uses_bge_and_judge_receives_full_answer_and_context(self):
        with fake_runtime() as rt:
            sample = job()
            sample["item"]["context"] = "历史上下文"
            row = harness.run_sample(rt, sample, "FAKE-SECRET", harness.StrictBGE(rt.settings))
            self.assertEqual(row["status"], "ok")
            self.assertEqual(row["real_rerank_calls"], 1)
            self.assertEqual(row["sources"][0]["content"], "doc 1")
            rt.flag.assert_called_once_with("bge-test", use_fp16=False, device="cpu")
            sent = json.loads(rt.chat.invoke.call_args.args[0][1].content)
            self.assertEqual(sent["context"], "历史上下文")
            self.assertEqual(sent["answer"], row["answer"])
            self.assertGreater(len(sent["answer"]), 1500)
            self.assertEqual(rt.graph.run_agent.call_args.kwargs["conversation_history"], [("user", "历史上下文")])

    def test_load_inference_and_score_failures_cannot_hide_behind_tool_fallback(self):
        for failure in ("load", "compute", "count", "nan"):
            with self.subTest(failure=failure), fake_runtime() as rt:
                if failure == "load":
                    rt.flag.side_effect = RuntimeError("load failed")
                elif failure == "compute":
                    rt.flag.return_value.compute_score.side_effect = RuntimeError("inference failed")
                else:
                    rt.flag.return_value.compute_score.return_value = [1] if failure == "count" else [float("nan")] * 4
                row = harness.run_sample(rt, job(), "FAKE-SECRET", harness.StrictBGE(rt.settings))
                self.assertEqual(row["status"], "error")
                self.assertIsNone(row["score"])
                self.assertTrue(any(e["stage"] == "rerank" for e in row["errors"]))
                self.assertTrue(row["answer"])  # Production kb_search_tool swallowed the exception.
                rt.chat.invoke.assert_not_called()

    def test_cache_and_flags_restore_after_exception_including_late_bindings(self):
        with fake_runtime() as rt:
            before = dict(vars(rt.settings))
            original = rt.kb.rerank
            old_docs = rt.retriever._docs_cache
            old_get = rt.faq.get
            cached = rt.embeddings._CachedEmbeddings()
            late = ModuleType("app.late_test_binding")
            with patch.dict(sys.modules, {late.__name__: late}):
                with self.assertRaisesRegex(RuntimeError, "abort"):
                    with harness.experiment_patch(rt, "no_rerank", harness.StrictBGE(rt.settings), fresh_row()):
                        self.assertFalse(rt.settings.LLM_CACHE_ENABLED)
                        self.assertEqual(rt.settings.QUERY_CACHE_TTL_SECONDS, 0)
                        self.assertIsNone(rt.nodes.get_cache_sync())
                        self.assertIsNone(rt.nodes.get_query_cache().get("q"))
                        self.assertIsNone(rt.nodes.get_query_cache().put("q", "a", []))
                        self.assertEqual(cached.embed_query("q"), [1.0])
                        late.rerank = rt.retriever.rerank
                        self.assertIs(rt.kb.rerank, late.rerank)
                        raise RuntimeError("abort")
                self.assertIs(late.rerank, original)
            self.assertEqual(vars(rt.settings), before)
            self.assertIs(rt.retriever._docs_cache, old_docs)
            self.assertIs(rt.faq.get, old_get)
            self.assertIs(rt.nodes.get_cache_sync, rt.cache.get_cache_sync)
            self.assertEqual(cached.embed_query("q"), [99.0])
            rt.nodes.reset_agent_event_emitter.assert_called_once_with("emitter-token")
            rt.cache.put_cache_sync.assert_not_called()

    def test_no_rewrite_blocks_unguarded_other_intent_resolver(self):
        with fake_runtime() as rt:
            original = rt.nodes._resolve_pronoun
            row = fresh_row()
            with harness.experiment_patch(rt, "no_rewrite", harness.StrictBGE(rt.settings), row):
                self.assertEqual(rt.nodes._resolve_pronoun("它多少钱", "X200"), "它多少钱")
                self.assertFalse(rt.settings.QUERY_REWRITE_ENABLED)
                self.assertTrue(rt.settings.RERANKER_ENABLED)
            original.assert_not_called()
            self.assertTrue(row["rewrite_trace"][0]["disabled"])
            self.assertIs(rt.nodes._resolve_pronoun, original)

    def test_react_completion_is_in_wall_latency_and_preserves_trace(self):
        with fake_runtime() as rt:
            rt.graph.run_agent.side_effect = None
            rt.graph.run_agent.return_value = {"uses_react": True, "answer": "", "intent": "other"}
            rt.react.run_react_agent.return_value = {
                "answer": "完整 ReAct 回答", "tool_trace": [{"tool": "_product_search_tool", "args": {}}],
                "sources": [{"content": "react evidence"}]}
            clock = [0.0]
            def react_call(**kwargs):
                clock[0] += 7.0
                return rt.react.run_react_agent.return_value
            rt.react.run_react_agent.side_effect = react_call
            with patch.object(harness.time, "perf_counter", side_effect=lambda: clock[0]):
                row = harness.run_sample(rt, job(), "FAKE-SECRET", harness.StrictBGE(rt.settings))
            self.assertEqual(row["latency_ms"], 7000)
            self.assertEqual(row["answer"], "完整 ReAct 回答")
            self.assertEqual(row["intent"], "other")
            self.assertEqual(row["sources"], [{"content": "react evidence"}])
            self.assertTrue(row["nonempty_without_kb"])
            self.assertEqual(row["react_tool_trace"][0]["tool"], "_product_search_tool")
            self.assertEqual(rt.react.run_react_agent.call_args.kwargs, rt.graph.run_agent.call_args.kwargs)

    def test_judge_and_caught_llm_errors_are_not_scored(self):
        with fake_runtime() as rt:
            rt.chat.invoke.return_value.content = '{"score": 50, "reason": "bad"}'
            row = harness.run_sample(rt, job(), "FAKE-SECRET", harness.StrictBGE(rt.settings))
            self.assertEqual(row["status"], "error")
            self.assertIsNone(row["score"])
            self.assertIn('50', row["judge_raw"])
            self.assertTrue(row["answer"])
        row = fresh_row()
        observed = harness.ObservedChat(SimpleNamespace(invoke=Mock(side_effect=RuntimeError("API down"))), row)
        with self.assertRaises(RuntimeError):
            observed.invoke([])
        self.assertEqual(row["errors"][0]["stage"], "llm.invoke")

    def test_summary_excludes_errors_and_pairs_only_valid_same_repeat(self):
        schedule = harness.build_schedule(limit=1)
        rows = []
        with fake_runtime() as rt:
            backend = harness.StrictBGE(rt.settings)
            for sample in schedule:
                rows.append(harness.run_sample(rt, sample, "FAKE-SECRET", backend))
        rows[0]["status"], rows[0]["score"] = "error", None
        result = harness.summarize(rows, schedule)
        self.assertEqual(result["row_count"], 8)
        self.assertEqual(result["error_count"], 1)
        self.assertEqual(sum(s["valid_pairs"] for s in result["paired_by_repeat"]), 3)
        self.assertEqual(sum(s["error_or_missing_pairs"] for s in result["paired_by_repeat"]), 1)
        for summary in result["summary"]:
            self.assertEqual(summary["avg_score"], 4)

    def test_conflicting_reference_is_excluded_from_primary_scores(self):
        schedule = harness.build_schedule(repeats=1, limit=1)
        rows = []
        with fake_runtime() as rt:
            backend = harness.StrictBGE(rt.settings)
            for sample in schedule:
                rows.append(harness.run_sample(rt, sample, "FAKE-SECRET", backend))
        rows[0]["reference_conflict"] = "contradictory corpus"
        result = harness.summarize(rows, schedule)
        summary = next(s for s in result["summary"]
                       if s["variant"] == f"{rows[0]['group']}_{rows[0]['variant']}")
        self.assertEqual(summary["scored_n"], 0)
        self.assertIsNone(summary["avg_score"])
        self.assertEqual(summary["error_count"], 0)
        self.assertEqual(summary["reference_conflict_count"], 1)

    def test_business_mutations_are_blocked_before_execution(self):
        with fake_runtime() as rt:
            write = Mock()
            tool = rt.tools.BaseTool("_add_to_cart_tool", write)
            row = fresh_row()
            with harness.experiment_patch(rt, "full", harness.StrictBGE(rt.settings), row):
                with self.assertRaisesRegex(RuntimeError, "只读消融禁止业务写入"):
                    tool.invoke({"sku_id": 1})
            write.assert_not_called()
            self.assertTrue(row["errors"])

    def test_existing_output_is_refused_before_runtime_or_token(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "existing.json"
            output.write_text("old result", encoding="utf-8")
            with patch.object(harness, "load_runtime", side_effect=AssertionError("runtime")):
                with self.assertRaises(FileExistsError):
                    harness.main(["--output", str(output)])
            self.assertEqual(output.read_text(encoding="utf-8"), "old result")

    def test_startup_failure_is_checkpointed_without_token_generation(self):
        with tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "startup-error.json"
            with patch.object(harness, "load_runtime", side_effect=ImportError("missing dependency")), \
                    redirect_stdout(io.StringIO()):
                self.assertEqual(harness.main(["--limit", "1", "--output", str(output)]), 1)
            result = json.loads(output.read_text(encoding="utf-8"))
            self.assertFalse(result["completed"])
            self.assertEqual(result["row_count"], 0)
            self.assertEqual(result["expected_row_count"], 8)
            self.assertEqual(result["fatal_error"]["type"], "ImportError")
            self.assertTrue(all(s["avg_score"] is None for s in result["summary"]))

    def test_empty_answer_is_an_error_and_skips_judge(self):
        with fake_runtime() as rt:
            rt.graph.run_agent.side_effect = None
            rt.graph.run_agent.return_value = {"answer": "", "intent": "other"}
            row = harness.run_sample(rt, job(), "FAKE-SECRET", harness.StrictBGE(rt.settings))
            self.assertEqual(row["status"], "error")
            self.assertIsNone(row["score"])
            self.assertFalse(row["nonempty_without_kb"])
            rt.chat.invoke.assert_not_called()

    def test_fake_cli_writes_all_rows_without_secrets(self):
        with fake_runtime() as rt, tempfile.TemporaryDirectory() as tmp:
            output = Path(tmp) / "result.json"
            with patch.object(harness, "load_runtime", return_value=rt), redirect_stdout(io.StringIO()):
                self.assertEqual(harness.main(["--limit", "1", "--output", str(output)]), 0)
            result = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(result["row_count"], 8)
            self.assertTrue(result["completed"])
            self.assertEqual(result["real_rerank_calls"], 6)
            self.assertNotIn("FAKE-SECRET", output.read_text(encoding="utf-8"))
            self.assertTrue(rt.settings.LLM_CACHE_ENABLED)


if __name__ == "__main__":
    unittest.main()
