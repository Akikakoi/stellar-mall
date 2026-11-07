"""纯 stdlib 离线测试：不导入 app 配置，不连接 Redis/ES，不签真实 token。

python -B scripts/ablation/test_cache_ablation.py
核心 key/L1/single-flight 函数由 AST 从原源码加载，不复制算法、不需要 pydantic_settings。
"""
from __future__ import annotations

import ast
import asyncio
import builtins
import copy
import hashlib
import importlib.util
import io
import json
import logging
import os
import socket
import sys
import threading
import time
import types
import unittest
import uuid
from collections import deque
from concurrent.futures import ThreadPoolExecutor
from contextlib import ExitStack, redirect_stdout
from pathlib import Path
from unittest.mock import Mock, patch

HERE = Path(__file__).resolve().parent
SCRIPT = HERE / "run_cache_ablation.py"
spec = importlib.util.spec_from_file_location("offline_cache_ablation", SCRIPT)
ablation = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = ablation
spec.loader.exec_module(ablation)


def inline_coroutine(coroutine):
    # 全部 async 依赖均为无 I/O stub；避免 Windows asyncio 自管 socketpair。
    try:
        coroutine.send(None)
    except StopIteration as completed:
        return completed.value
    finally:
        coroutine.close()
    raise AssertionError("offline coroutine unexpectedly suspended for I/O")


def setUpModule():
    global offline_patches
    offline_patches = ExitStack()
    offline_patches.enter_context(patch.object(asyncio, "run", side_effect=inline_coroutine))
    offline_patches.enter_context(patch.object(socket.socket, "connect", side_effect=AssertionError("network forbidden")))


def tearDownModule():
    offline_patches.close()


def targets():
    run_id = uuid.uuid4().hex
    return {label: ablation.Target(run_id, label, uuid.uuid4().hex)
            for label in ("seed", "l2_on", "l2_off", "novel")}


class FakeRedis:
    def __init__(self):
        self.data = {"llm:exact:production": ("string", "do-not-touch", -1)}
        self.operations = []

    def ping(self):
        return True

    def scan_iter(self, match):
        assert match.startswith("ablation:") and match.endswith("*")
        self.operations.append(("scan", match))
        return iter(sorted(key for key in self.data if key.startswith(match[:-1])))

    def get(self, key):
        self.operations.append(("get", key))
        return self.data.get(key, (None, None, None))[1]

    def pttl(self, key):
        return self.data.get(key, (None, None, -2))[2]

    def type(self, key):
        return self.data[key][0]

    def psetex(self, key, ttl, value):
        assert key.startswith("ablation:") and ttl > 0
        self.operations.append(("psetex", key))
        self.data[key] = ("string", value, ttl)

    def setex(self, key, ttl, value):
        self.psetex(key, ttl * 1000, value)

    def sadd(self, key, *values):
        assert key.startswith("ablation:")
        old = self.data.get(key, ("set", set(), -1))
        self.data[key] = ("set", old[1] | set(values), old[2])
        return self

    def smembers(self, key):
        return self.data[key][1]

    def pexpire(self, key, ttl):
        kind, value, _ = self.data[key]
        self.data[key] = (kind, value, ttl)
        return self

    def expire(self, key, ttl):
        return self.pexpire(key, ttl * 1000)

    def pipeline(self, transaction):
        assert transaction
        return self

    def execute(self):
        return []

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        pass


class FakeES:
    def __init__(self):
        self.docs = {"stellar_llm_cache": {"production": {"answer": "do-not-touch"}}}
        self.indices = self
        self.operations = []

    def own(self, index):
        assert index.startswith("stellar_ablation_cache_")
        self.operations.append(index)

    def exists(self, index):
        self.own(index)
        return index in self.docs

    def create(self, index, mappings):
        self.own(index)
        assert index not in self.docs
        self.docs[index] = {}

    def refresh(self, index):
        self.own(index)
        assert index in self.docs

    def count(self, index):
        self.own(index)
        return {"count": len(self.docs[index])}

    def search(self, index, **kwargs):
        self.own(index)
        return {"hits": {"hits": [{"_id": key, "_source": copy.deepcopy(value)}
                                  for key, value in self.docs[index].items()]}}

    def index(self, index, id, document):
        self.own(index)
        self.docs[index][id] = copy.deepcopy(document)


def core_module():
    """加载实际算法而不执行 app imports、模块初始化或任何外部请求。"""
    source = ablation.ROOT / "app" / "rag" / "llm_cache.py"
    tree = ast.parse(source.read_text(encoding="utf-8"))
    names = {"_sha256", "_redis_key", "_pid_index_key", "_CacheMetrics", "_SyncFlight",
             "_single_flight_sync", "single_flight_sync", "_redis_get_sync", "_redis_put_sync",
             "_do_put_sync", "LLMCache", "get_cache_metrics"}
    body = [node for node in tree.body if isinstance(node, (ast.FunctionDef, ast.ClassDef)) and node.name in names]
    assert {node.name for node in body} == names
    future = ast.ImportFrom(module="__future__", names=[ast.alias(name="annotations")], level=0)
    module = types.ModuleType("offline_llm_core")
    settings = types.SimpleNamespace(**{
        "LLM_CACHE_ENABLED": True, "LLM_CACHE_REDIS_ENABLED": True,
        "LLM_CACHE_SEMANTIC_ENABLED": True, "LLM_CACHE_SINGLE_FLIGHT_ENABLED": False,
        "LLM_CACHE_BLOOM_ENABLED": True, "QUERY_CACHE_TTL_SECONDS": 300,
        "LLM_CACHE_REDIS_TTL_ANSWER": 7200, "LLM_CACHE_REDIS_TTL_INTENT": 3600,
        "LLM_CACHE_SEMANTIC_TTL_ANSWER": 7200, "LLM_CACHE_SEMANTIC_TTL_INTENT": 3600,
    })
    module.__dict__.update(threading=threading, time=time, hashlib=hashlib, json=json,
                           asyncio=asyncio, deque=deque, settings=settings, logger=logging.getLogger("offline"))
    selected = ast.fix_missing_locations(ast.Module(body=[future] + body, type_ignores=[]))
    exec(compile(selected, str(source), "exec"), module.__dict__)
    module._metrics = module._CacheMetrics()
    module._sf_sync_map = {}
    module._sf_sync_lock = threading.Lock()
    module._SF_WAIT_TIMEOUT = 120.0
    module._redis_available = False
    module._redis_sync = module._redis_client = None
    module._semantic_collection = None
    module._semantic_available = False
    module._bloom_may_exist = lambda *a: True
    module._bloom_add = lambda *a: None
    return module


def mock_runtime():
    c = core_module()
    redis, es = FakeRedis(), FakeES()
    es_module = types.SimpleNamespace(CACHE_ES_INDEX="stellar_llm_cache", _CACHE_MAPPING={}, get_es=lambda: es)
    es_module.es_bulk_index = lambda index, docs, ids=None: len(docs)
    c._CACHE_WRITE_EXECUTOR = ThreadPoolExecutor(max_workers=4)
    initialization = []

    async def init_redis():
        initialization.append((c._redis_key("q", "m", 0, "h", None), es_module.CACHE_ES_INDEX))
        c._redis_sync, c._redis_client, c._redis_available = redis, object(), True
        return True

    async def close_redis():
        c._redis_sync = c._redis_client = None
        c._redis_available = False

    async def redis_get(*args, cache_type="answer", **kwargs):
        return None

    async def redis_put(*args, cache_type="answer", **kwargs):
        return True

    def semantic_search(*args, cache_type="answer", **kwargs):
        return None

    async def semantic_get(*args, cache_type="answer", **kwargs):
        return None

    async def semantic_put(*args, cache_type="answer", **kwargs):
        return True

    def collection():
        if c._semantic_collection is None:
            c._semantic_collection = types.SimpleNamespace(_index=es_module.CACHE_ES_INDEX)
        return c._semantic_collection

    c.init_redis, c.close_redis = init_redis, close_redis
    c._redis_get, c._redis_put = redis_get, redis_put
    c._semantic_search_sync, c._semantic_search = semantic_search, semantic_get
    c._semantic_put, c._get_semantic_collection = semantic_put, collection
    return c, es_module, redis, es, initialization


class IsolationTests(unittest.TestCase):
    def test_key_and_pid_namespace_and_init_order(self):
        c, em, redis, es, initialization = mock_runtime()
        ts = targets()
        original_key, original_pid = c._redis_key, c._pid_index_key
        with ablation.Experiment(c, em, c.settings, ts, timeout=2, ttl=60) as exp:
            self.assertTrue(initialization[0][0].startswith(ts["seed"].prefix))
            self.assertEqual(initialization[0][1], ts["seed"].index)
            args = ("query", "model", 0.2, "hash", None)
            self.assertTrue(c._redis_put_sync(*args, {"answer": "full"}, ttl=60, product_ids=[3]))
            key = c._redis_key(*args)
            pid = c._pid_index_key(3)
            self.assertEqual(key, ts["seed"].prefix + original_key(*args))
            self.assertEqual(pid, ts["seed"].prefix + original_pid(3))
            self.assertEqual(redis.smembers(pid), {key})
            self.assertEqual(redis.pttl(key), 60000)
            self.assertEqual(redis.pttl(pid), 60000)
            self.assertTrue(c._redis_get_sync(*args))
            exp.activate(ts["seed"], enabled=True, l1=False, l2=True)
            ops = len(redis.operations)
            self.assertIsNone(c._redis_get_sync(*args))
            self.assertFalse(c._redis_put_sync(*args, {"answer": "disabled"}))
            self.assertEqual(len(redis.operations), ops)
            exp.activate(ts["seed"], enabled=True, l1=True, l2=False)
            self.assertTrue(c._redis_get_sync(*args))
            self.assertFalse(asyncio.run(c._semantic_put(query="q")))
            self.assertEqual(ablation.layer_count(exp.meter.snapshot(), "l2", "write_skips"), 1)
        self.assertIs(c._redis_key, original_key)
        self.assertIs(c._pid_index_key, original_pid)
        self.assertEqual(em.CACHE_ES_INDEX, "stellar_llm_cache")
        self.assertEqual(redis.data["llm:exact:production"][1], "do-not-touch")
        self.assertEqual(es.docs["stellar_llm_cache"]["production"]["answer"], "do-not-touch")

    def test_l2_variants_equal_seed_no_previous_variant_pollution(self):
        ts, redis, es = targets(), FakeRedis(), FakeES()
        stores = ablation.Stores(redis, es, {}, ts.values())
        seed_target = ts["seed"]
        stores.create(seed_target)
        redis.psetex(seed_target.prefix + "llm:exact:answer:seed", 60000, '{"answer":"seed"}')
        redis.sadd(seed_target.prefix + "llm:exact:idx:pid:1", seed_target.prefix + "llm:exact:answer:seed")
        redis.pexpire(seed_target.prefix + "llm:exact:idx:pid:1", 60000)
        es.index(seed_target.index, "seed-id", {"query": ablation.QUERIES[0], "answer": "seed",
                                              "vec": [1, 0], "hit_count": 0, "created_at": 1})
        seed = stores.snapshot(seed_target)
        digest = ablation.snapshot_digest(seed)
        for label in ("l2_on", "l2_off"):
            self.assertEqual(stores.clone(seed, ts[label]), digest)
            self.assertEqual(stores.snapshot(ts[label]), seed)
        # 模拟 P2 的 L1 回写、新 L2 文档和旧 L2 hit_count 更新。
        on = ts["l2_on"]
        redis.psetex(on.prefix + "llm:exact:answer:paraphrase", 60000, "P2-write")
        redis.sadd(on.prefix + "llm:exact:idx:pid:1", on.prefix + "llm:exact:answer:paraphrase")
        es.index(on.index, "new-id", {"vec": [0, 1], "query": ablation.PARAPHRASES[0]})
        es.docs[on.index]["seed-id"]["hit_count"] = 99
        self.assertNotEqual(ablation.snapshot_digest(stores.snapshot(on)), digest)
        self.assertEqual(ablation.snapshot_digest(stores.snapshot(ts["l2_off"])), digest)
        self.assertEqual(ablation.snapshot_digest(stores.snapshot(seed_target)), digest)
        self.assertEqual(ablation.snapshot_digest(seed), digest)
        self.assertFalse(any("paraphrase" in key for key in redis.data if key.startswith(ts["l2_off"].prefix)))
        with self.assertRaises(RuntimeError):
            stores.clone(seed, ts["l2_off"])
        with self.assertRaises(ValueError):
            stores.snapshot(ablation.Target(uuid.uuid4().hex, "unknown", uuid.uuid4().hex))
        self.assertEqual(redis.data["llm:exact:production"][1], "do-not-touch")

    def test_snapshot_rejects_non_ttl_or_cross_namespace_members(self):
        ts, redis, es = targets(), FakeRedis(), FakeES()
        stores = ablation.Stores(redis, es, {}, ts.values())
        target = ts["seed"]
        stores.create(target)
        key = target.prefix + "idx"
        redis.sadd(key, "llm:exact:production")
        with self.assertRaises(RuntimeError):
            stores.snapshot(target)
        redis.pexpire(key, 1000)
        with self.assertRaises(ValueError):
            stores.snapshot(target)

    def test_partial_es_bulk_is_failure(self):
        c, em, _, _, _ = mock_runtime()
        em.es_bulk_index = lambda index, docs, ids=None: 0
        ts = targets()
        with ablation.Experiment(c, em, c.settings, ts, timeout=2, ttl=60):
            with self.assertRaisesRegex(RuntimeError, "0/1"):
                em.es_bulk_index(ts["seed"].index, [{"answer": "a"}])
            with self.assertRaisesRegex(RuntimeError, "non-owned"):
                em.es_bulk_index("stellar_llm_cache", [])


class DrainTests(unittest.TestCase):
    def test_drain_waits_for_every_worker_not_fast_sentinel(self):
        entered, release = threading.Event(), threading.Event()
        with ThreadPoolExecutor(max_workers=3) as pool:
            tracker = ablation.WriteTracker(pool.submit)

            def slow():
                entered.set()
                if not release.wait(2):
                    raise TimeoutError("test producer not released")

            tracker.submit(slow)
            fast = tracker.submit(lambda: "fast")
            self.assertTrue(entered.wait(1))
            self.assertEqual(fast.result(1), "fast")
            try:
                with self.assertRaises(TimeoutError):
                    tracker.drain(0.01)
            finally:
                release.set()
            self.assertEqual(tracker.drain(2)["completed"], 2)
            self.assertEqual(tracker.drain(2)["completed"], 0)

    def test_drain_collects_nested_submissions(self):
        with ThreadPoolExecutor(max_workers=2) as pool:
            tracker = ablation.WriteTracker(pool.submit)
            tracker.submit(lambda: tracker.submit(lambda: 2))
            self.assertEqual(tracker.drain(2)["completed"], 2)

    def test_drain_propagates_completed_failure_and_waits_others(self):
        with ThreadPoolExecutor(max_workers=2) as pool:
            tracker = ablation.WriteTracker(pool.submit)
            bad = tracker.submit(lambda: 1 / 0)
            try:
                bad.result(1)
            except ZeroDivisionError:
                pass
            tracker.submit(lambda: "ok")
            with self.assertRaisesRegex(RuntimeError, "ZeroDivisionError"):
                tracker.drain(2)
            self.assertEqual(tracker.drain(2)["completed"], 0)

    def test_no_namespace_switch_on_drain_timeout(self):
        c, em, _, _, _ = mock_runtime()
        ts, release = targets(), threading.Event()
        with ablation.Experiment(c, em, c.settings, ts, timeout=0.01, ttl=60) as exp:
            exp.stores.create(ts["l2_on"])
            c._CACHE_WRITE_EXECUTOR.submit(lambda: release.wait(2))
            try:
                with self.assertRaises(TimeoutError):
                    exp.activate(ts["l2_on"], enabled=True, l1=True, l2=True)
                self.assertEqual(exp.current, ts["seed"])
                self.assertEqual(em.CACHE_ES_INDEX, ts["seed"].index)
            finally:
                release.set()


class ProbeAndReportingTests(unittest.TestCase):
    def test_single_flight_actual_core_on_off_six_workers(self):
        c = core_module()
        original = c._SyncFlight
        rows = ablation.single_flight_probe(c, timeout=2)
        self.assertEqual([row["producer_calls"] for row in rows], [1, 6])
        self.assertEqual([row["observed_waiters"] for row in rows], [5, 0])
        self.assertTrue(all(row["passed"] for row in rows))
        self.assertTrue(all(row["n_success"] == 6 for row in rows))
        self.assertEqual(c._sf_sync_map, {})
        self.assertIs(c._SyncFlight, original)
        self.assertFalse(c.settings.LLM_CACHE_SINGLE_FLIGHT_ENABLED)

    def test_failed_samples_full_answer_and_zero_hits(self):
        exp = types.SimpleNamespace(
            cache=types.SimpleNamespace(get_cache_metrics=lambda: dict.fromkeys(ablation.NATIVE_COUNTERS, 0)),
            meter=ablation.LayerMeter(), current=targets()["seed"],
            settings=types.SimpleNamespace(LLM_CACHE_ENABLED=True, LLM_CACHE_REDIS_ENABLED=True,
                                           LLM_CACHE_SEMANTIC_ENABLED=True),
            drain=lambda: {"completed": 0},
        )
        full = "完整答案" * 80
        agent = Mock(side_effect=[{"answer": full}, RuntimeError("offline failure"),
                                  {"answer": "抱歉，系统暂时无法处理您的请求，请稍后重试。"}])
        result = ablation.run_phase("P2_l2_on", ["ok", "error", "fallback"], agent, exp, ablation.WarningCapture())
        self.assertEqual(result["n"], 3)
        self.assertEqual(result["n_success"], 1)
        self.assertEqual(result["n_failed"], 2)
        self.assertEqual(result["detail"][0]["answer"], full)
        self.assertIsNone(result["detail"][1]["latency_ms"])
        self.assertIsNone(result["detail"][2]["latency_ms"])
        self.assertEqual(result["latency_avg_ms"], result["detail"][0]["latency_ms"])
        self.assertFalse(result["passed"])
        # 即便全部请求成功，只有 intent 命中也不能伪装成答案缓存收益。
        result["n_success"] = 3
        exp.meter.incr("l2", "intent", "hits")
        result["layers"] = exp.meter.snapshot()
        self.assertFalse(all(check["passed"] for check in ablation.check_phase(result)))

    def test_business_writes_are_blocked_before_http(self):
        http = Mock(return_value={"ok": True})
        with self.assertRaisesRegex(RuntimeError, "只读消融禁止业务写入"):
            ablation.read_only_mall("POST", "/user/cart", http)
        http.assert_not_called()
        self.assertEqual(ablation.read_only_mall("GET", "/user/category/list", http), {"ok": True})

    def test_make_agent_executes_react_and_preserves_errors(self):
        graph = types.ModuleType("app.agent.graph")
        graph.run_agent = Mock(return_value={"answer": "", "uses_react": True, "intent": "other"})
        react = types.ModuleType("app.agent.react_agent")
        react.run_react_agent = Mock(return_value={"answer": "react full answer", "tool_trace": [{"success": False}]})
        token = types.ModuleType("mall_token")
        token.make_mall_user_token = Mock(return_value="offline-token-not-real")
        tools = types.ModuleType("app.agent.tools")
        tools.mall_client = types.ModuleType("app.agent.tools.mall_client")
        tools.mall_client.call_mall = Mock()
        modules = {"app.agent.graph": graph, "app.agent.react_agent": react, "mall_token": token,
                   "app.agent.tools": tools, "app.agent.tools.mall_client": tools.mall_client}
        with patch.dict(sys.modules, modules), patch.dict(os.environ, {"MALL_TOKEN": ""}):
            agent = ablation.make_agent(4)
            result = agent("q")
        self.assertEqual(result["answer"], "react full answer")
        self.assertTrue(result["uses_react"])
        self.assertTrue(ablation.response_errors(result))
        self.assertEqual(graph.run_agent.call_args, react.run_react_agent.call_args)
        token.make_mall_user_token.assert_called_once_with(user_id=4)

    def test_full_orchestration_clones_before_paraphrases_without_api(self):
        c, em, redis, es, _ = mock_runtime()
        c.settings.LLM_MODEL_NAME = "offline-model"
        c.settings.LLM_TEMPERATURE = 0.0
        c.settings.LLM_CACHE_SEMANTIC_RERANK_THRESHOLD = 0.9
        c.settings.LLM_CACHE_SEMANTIC_K = 5
        c.settings.LLM_CACHE_SEMANTIC_MAXSIZE = 5000

        async def semantic_put(query, answer, cache_type="answer", **kwargs):
            es.index(em.CACHE_ES_INDEX, query, {"query": query, "answer": answer,
                     "vec": [1.0, 0.0], "created_at": time.time(), "hit_count": 0})
            c._metrics.incr("write_oks")
            return True

        def semantic_search(query, model, temperature, system_prompt_hash,
                            cache_type="answer", context_hash=None, query_emb=None,
                            context_keys=None):
            original = dict(zip(ablation.PARAPHRASES, ablation.QUERIES)).get(query, query)
            doc = es.docs[em.CACHE_ES_INDEX].get(original)
            if doc:
                doc["hit_count"] += 1
                c._metrics.incr("l2_hits")
                return {"answer": doc["answer"], "sources": []}
            return None

        c._semantic_put, c._semantic_search_sync = semantic_put, semantic_search
        cache = c.LLMCache()

        def fake_agent(query):
            hit = cache.get_sync(query, "offline-model", 0.0, "hash", "context")
            if hit:
                return hit
            answer = "offline generated: " + query
            cache.put_sync(query, "offline-model", 0.0, "hash", "context", answer, [], "consult", 1)
            return {"answer": answer}

        # 完整执行编排，但 app、存储、Agent/token 入口全部替换为内存 mock。
        modules = {name: types.ModuleType(name) for name in (
            "app", "app.config", "app.rag", "app.core", "app.core.logger",
            "app.rag.retriever", "app.agent", "app.agent.nodes",
        )}
        modules["app.config"].settings = c.settings
        modules["app.core.logger"].logger = types.SimpleNamespace(add=lambda *a, **k: 1, remove=lambda *a: None)
        modules["app.rag.retriever"].get_query_cache = lambda: None
        modules["app.agent.nodes"].get_query_cache = lambda: None
        modules["app.rag.llm_cache"], modules["app.rag.es_client"] = c, em
        modules["app"].rag = modules["app.rag"]
        modules["app"].agent = modules["app.agent"]
        modules["app.rag"].llm_cache, modules["app.rag"].es_client = c, em
        modules["app.rag"].retriever = modules["app.rag.retriever"]
        modules["app.agent"].nodes = modules["app.agent.nodes"]
        report = {"run_id": uuid.uuid4().hex, "phases": []}
        args = ablation.parse_args(["--limit", "1"])
        with patch.dict(sys.modules, modules), patch.object(sys, "path", list(sys.path)), \
                patch.object(ablation, "make_agent", return_value=fake_agent), \
                patch.object(ablation, "prepare_models", return_value={"bge_ready": True}), \
                redirect_stdout(io.StringIO()):
            ablation.execute(args, report, lambda: None)
        self.assertTrue(report["passed"], report["phases"])
        self.assertEqual(len(report["phases"]), 8)
        self.assertEqual(set(report["clone_hashes"].values()), {report["seed"]["sha256"]})
        self.assertEqual(report["variant_start_hashes"], report["clone_hashes"])
        on, off = report["phases"][3:5]
        self.assertNotEqual(on["target"]["redis_prefix"], off["target"]["redis_prefix"])
        self.assertNotEqual(on["target"]["es_index"], off["target"]["es_index"])
        self.assertEqual(ablation.layer_count(on["layers"], "l2", "hits", "answer"), 1)
        self.assertEqual(ablation.layer_count(off["layers"], "l1", "hits", "answer"), 0)
        self.assertEqual(ablation.layer_count(off["layers"], "l2", "write_oks"), 0)
        self.assertEqual(redis.data["llm:exact:production"][1], "do-not-touch")
        self.assertEqual(es.docs["stellar_llm_cache"]["production"]["answer"], "do-not-touch")

    def test_import_and_dry_run_zero_app_token_network_or_file_write(self):
        original_import = builtins.__import__

        def guarded_import(name, *args, **kwargs):
            if name == "app" or name.startswith("app.") or name in ("mall_token", "redis", "elasticsearch"):
                raise AssertionError("dry run imported " + name)
            return original_import(name, *args, **kwargs)

        output = io.StringIO()
        with patch("builtins.__import__", side_effect=guarded_import), \
                patch.object(socket, "create_connection", side_effect=AssertionError("network")), \
                patch.object(Path, "open", side_effect=AssertionError("file write")), redirect_stdout(output):
            # 再次 import 也不能触发 token/API（禁用字节码由 -B 保证）。
            spec.loader.exec_module(ablation)
            self.assertEqual(ablation.main(["--dry-run", "--limit", "2"]), 0)
        plan = json.loads(output.getvalue())
        self.assertEqual(len(plan["queries"]), 2)
        self.assertEqual(len(plan["paraphrases"]), 2)
        self.assertEqual(plan["phases"], [])
        self.assertNotEqual(Path(plan["output"]).name, "results_cache.json")


if __name__ == "__main__":
    unittest.main(verbosity=2)
