"""隔离 LLM cache 消融；import/--dry-run 不加载 app、不签 token、不访问服务。

P0 cache_off / P0b cold seed / P1 exact / P2 L2_on / P2b L2_off /
P3 novel / P4 single-flight on/off（unit probe，不代表 Agent/API 吞吐）。
P2/P2b 从同一已 drain 的 QUERIES 快照克隆到两个 UUID namespace/index。
同步完整 Agent online cache 实验：保留变体内部回写，不复用前组写入。
FAQ/Bloom 固定关闭；检索/embedding 进程缓存不重置，工具上下文可能变化，
不能将延迟差全归于模型调用。L2 关闭同时禁止读写；SF 仅在 P4 开启。

在 rag-backend 工作目录，由实验负责人执行：
  python scripts/ablation/run_cache_ablation.py --dry-run --limit 2
  python scripts/ablation/run_cache_ablation.py --output <new-file.json>
默认 timestamp + UUID 新文件，拒绝覆盖。无缓存删除操作；Redis 全部设 TTL，
独立 ES 索引留作证据。运行时 patch 仅适用于专用脚本进程，不可嵌入服务。
"""
from __future__ import annotations

import argparse
import asyncio
import copy
import hashlib
import inspect
import json
import os
import re
import sys
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FutureTimeout
from contextlib import ExitStack
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parent.parent.parent
HERE = Path(__file__).resolve().parent

QUERIES = [
    "星耀 X100 Pro Max 支持卫星通信吗",
    "清逸 AirBook 14 的屏幕参数怎么样",
    "AX3000 路由器支持哪些无线协议",
    "20000mAh 移动电源可以带上飞机吗",
    "星耀 Band 9 支持多少种运动模式",
    "天玑 9300 芯片的平板有哪些",
    "能测心电图和血压趋势的智能手表",
    "戴着睡觉也没感觉的健康监测设备",
    "5500mAh 电池配 120W 快充的手机",
    "支持无线充电的折叠屏手机",
    "可以插存储卡扩容的平板电脑",
    "带 eSIM 独立打电话的智能手表",
]
PARAPHRASES = [
    "请问一下，星耀 X100 Pro Max 支不支持卫星通信呢？",
    "想了解下清逸 AirBook 14 这款笔记本的屏幕参数",
    "路由器 AX3000 都兼容什么无线协议呀",
    "两万毫安的移动电源登机的时候能带吗",
    "星耀 Band 9 这款手环一共有多少种运动模式？",
    "用的天玑 9300 芯片的平板电脑有哪几款",
    "有没有能测心电图、看血压趋势的智能手表推荐",
    "睡觉戴着基本没感觉的健康监测设备有哪些",
    "手机电池 5500 毫安还配 120W 快充的有哪些",
    "折叠屏手机里面支持无线充电的是哪款",
    "哪些平板电脑可以插存储卡来扩容？",
    "智能手表里带 eSIM 能独立打电话的有哪些",
]
NOVEL = [
    "星耀 Watch 4 Ultra 的防水等级是多少",
    "星耀 Creator 14 用什么处理器",
    "星耀 Tab Pro 12.4 电池多大",
    "星耀 Buds Pro 3 降噪深度多少分贝",
    "清逸 ProBook 16 的显卡是什么型号",
    "星耀 SmartRing Nova 续航几天",
]


@dataclass(frozen=True)
class Target:
    run_id: str
    label: str
    uid: str

    @property
    def prefix(self):
        return f"ablation:{self.run_id}:{self.uid}:"

    @property
    def index(self):
        return f"stellar_ablation_cache_{self.uid}"

    def metadata(self):
        return {**asdict(self), "redis_prefix": self.prefix, "es_index": self.index}


class WriteTracker:
    """保留每个真实 future，包括已完成/失败者；不以 sentinel 推断 drain。"""

    def __init__(self, submit):
        self._submit = submit
        self._lock = threading.Lock()
        self._pending = []
        self.submitted = 0

    def submit(self, *args, **kwargs):
        with self._lock:
            future = self._submit(*args, **kwargs)
            self._pending.append(future)
            self.submitted += 1
        return future

    def drain(self, timeout):
        deadline = time.monotonic() + timeout
        completed, errors = 0, []
        while True:
            with self._lock:
                batch = list(self._pending)
            if not batch:
                break
            for future in batch:
                try:
                    future.result(timeout=max(0.0, deadline - time.monotonic()))
                except FutureTimeout:
                    if not future.done():
                        raise TimeoutError(f"cache write drain exceeded {timeout}s")
                    errors.append("producer raised TimeoutError")
                except Exception as exc:
                    errors.append(f"{type(exc).__name__}: {exc}")
                with self._lock:
                    self._pending.remove(future)
                completed += 1
        if errors:
            raise RuntimeError("cache write futures failed: " + "; ".join(errors))
        return {"completed": completed, "submitted_total": self.submitted}


def _text(value):
    return value.decode("utf-8") if isinstance(value, bytes) else value


def _relative(key, target):
    key = _text(key)
    if not key.startswith(target.prefix):
        raise ValueError("refusing Redis key outside this experiment target")
    return key[len(target.prefix):]


def snapshot_digest(snapshot):
    # TTL 倒计时不参与内容 hash；副本恢复相同 seed TTL，不篡改 ES created_at。
    content = copy.deepcopy(snapshot)
    for entry in content["redis"]:
        entry.pop("ttl_ms", None)
    raw = json.dumps(content, sort_keys=True, ensure_ascii=False, separators=(",", ":"))
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()


class Stores:
    """只处理创建并登记过的 UUID 目标；无删除、flush 或通配索引操作。"""

    def __init__(self, redis, es, mapping, targets):
        self.redis, self.es, self.mapping = redis, es, mapping
        self.targets = set(targets)
        self.created = set()

    def check(self, target):
        if target not in self.targets:
            raise ValueError("unregistered cache target")
        uuid.UUID(hex=target.run_id)
        uuid.UUID(hex=target.uid)

    def create(self, target):
        self.check(target)
        if next(iter(self.redis.scan_iter(match=target.prefix + "*")), None) is not None:
            raise RuntimeError("UUID Redis namespace already exists; refusing reuse")
        if self.es.indices.exists(index=target.index):
            raise RuntimeError("UUID ES index already exists; refusing reuse")
        self.es.indices.create(index=target.index, mappings=copy.deepcopy(self.mapping))
        self.created.add(target)

    def snapshot(self, target):
        self.check(target)
        if target not in self.created:
            raise ValueError("target was not created by this process")
        self.es.indices.refresh(index=target.index)
        entries = []
        for key in sorted(set(self.redis.scan_iter(match=target.prefix + "*"))):
            relative = _relative(key, target)
            ttl = self.redis.pttl(key)
            if ttl <= 0:
                raise RuntimeError(f"missing/expired/non-TTL experiment key: {relative}")
            kind = _text(self.redis.type(key))
            if kind == "string":
                value = _text(self.redis.get(key))
            elif kind == "set":
                value = sorted(_relative(member, target) for member in self.redis.smembers(key))
            else:
                raise RuntimeError(f"unsupported experiment Redis type: {kind}")
            entries.append({"key": relative, "kind": kind, "value": value, "ttl_ms": ttl})
        count = int(self.es.count(index=target.index)["count"])
        if count > 10000:
            raise RuntimeError("snapshot exceeds 10000 documents; refusing truncated seed")
        response = self.es.search(index=target.index, query={"match_all": {}}, size=10000, source=True)
        docs = sorted(({"id": hit["_id"], "source": hit["_source"]}
                       for hit in response["hits"]["hits"]), key=lambda d: d["id"])
        if len(docs) != count or any(not d["source"].get("vec") for d in docs):
            raise RuntimeError("incomplete ES seed snapshot (count or vectors)")
        return {"redis": entries, "es": docs}

    def clone(self, snapshot, target):
        self.create(target)  # 仅空目标；永远不清空/覆盖旧 namespace
        for entry in snapshot["redis"]:
            key = target.prefix + entry["key"]
            ttl = int(entry["ttl_ms"])
            if ttl <= 0:
                raise ValueError("seed has no usable TTL")
            if entry["kind"] == "string":
                self.redis.psetex(key, ttl, entry["value"])
            elif entry["kind"] == "set":
                with self.redis.pipeline(transaction=True) as pipe:
                    pipe.sadd(key, *(target.prefix + value for value in entry["value"]))
                    pipe.pexpire(key, ttl)
                    pipe.execute()
            else:
                raise ValueError("unsupported seed key type")
        for doc in snapshot["es"]:
            self.es.index(index=target.index, id=doc["id"], document=copy.deepcopy(doc["source"]))
        actual = self.snapshot(target)
        if snapshot_digest(actual) != snapshot_digest(snapshot):
            raise RuntimeError("clone verification failed")
        return snapshot_digest(actual)


COUNTERS = ("hits", "misses", "read_errors", "read_skips", "write_oks", "write_fails", "write_skips")
NATIVE_COUNTERS = ("l1_hits", "l2_hits", "misses", "write_oks", "write_fails")


class LayerMeter:
    def __init__(self):
        self.lock = threading.Lock()
        self.data = {layer: {kind: dict.fromkeys(COUNTERS, 0)
                             for kind in ("answer", "intent", "rewrite")}
                     for layer in ("l1", "l2")}

    def incr(self, layer, cache_type, counter):
        with self.lock:
            row = self.data.setdefault(layer, {}).setdefault(cache_type, dict.fromkeys(COUNTERS, 0))
            row[counter] += 1

    def snapshot(self):
        with self.lock:
            return copy.deepcopy(self.data)


def layer_delta(before, after):
    return {layer: {kind: {key: counts[key] - before.get(layer, {}).get(kind, {}).get(key, 0)
                          for key in COUNTERS}
                    for kind, counts in kinds.items()} for layer, kinds in after.items()}


def layer_count(layers, layer, counter, kind=None):
    kinds = layers.get(layer, {})
    if kind is not None:
        return kinds.get(kind, {}).get(counter, 0)
    return sum(row.get(counter, 0) for row in kinds.values())


class Experiment:
    def __init__(self, cache, es_module, settings, targets, timeout, ttl):
        self.cache, self.es_module, self.settings = cache, es_module, settings
        self.targets, self.timeout, self.ttl = targets, timeout, ttl
        self.current = targets["seed"]
        self.meter = LayerMeter()
        self.stack = ExitStack()
        self.tracker = WriteTracker(cache._CACHE_WRITE_EXECUTOR.submit)
        self.stores = None

    def __enter__(self):
        c, s, stack = self.cache, self.settings, self.stack
        if (c._redis_client is not None or c._redis_sync is not None
                or c._semantic_collection is not None or c._sf_sync_map):
            raise RuntimeError("requires a fresh dedicated script process, not a running app")
        try:
            # 在 Redis/ES cache 初始化之前安装隔离函数和 ES 索引名。
            redis_key, pid_key = c._redis_key, c._pid_index_key
            stack.enter_context(patch.object(c, "_redis_key", lambda *a, **k: self.current.prefix + redis_key(*a, **k)))
            stack.enter_context(patch.object(c, "_pid_index_key", lambda *a, **k: self.current.prefix + pid_key(*a, **k)))
            stack.enter_context(patch.object(self.es_module, "CACHE_ES_INDEX", self.current.index))
            stack.enter_context(patch.object(c, "_semantic_collection", None))
            stack.enter_context(patch.object(c, "_semantic_available", False))
            stack.enter_context(patch.object(c._CACHE_WRITE_EXECUTOR, "submit", self.tracker.submit))
            overrides = {
                "LLM_CACHE_ENABLED": True, "LLM_CACHE_REDIS_ENABLED": True,
                "LLM_CACHE_SEMANTIC_ENABLED": True, "LLM_CACHE_SINGLE_FLIGHT_ENABLED": False,
                "LLM_CACHE_BLOOM_ENABLED": False, "QUERY_CACHE_TTL_SECONDS": 0,
                "LLM_CACHE_REDIS_TTL_ANSWER": self.ttl, "LLM_CACHE_REDIS_TTL_INTENT": self.ttl,
                "LLM_CACHE_SEMANTIC_TTL_ANSWER": self.ttl, "LLM_CACHE_SEMANTIC_TTL_INTENT": self.ttl,
            }
            for key, value in overrides.items():
                stack.enter_context(patch.object(s, key, value))
            for name, layer, action in (
                ("_redis_get_sync", "l1", "read"), ("_redis_get", "l1", "read"),
                ("_redis_put_sync", "l1", "write"), ("_redis_put", "l1", "write"),
                ("_semantic_search_sync", "l2", "read"), ("_semantic_search", "l2", "read"),
                ("_semantic_put", "l2", "write"),
            ):
                stack.enter_context(patch.object(c, name, self._instrument(getattr(c, name), layer, action)))
            # ESCacheCollection.add 未检查 bulk 成功条数；本轮索引上改为显式失败。
            original_bulk = self.es_module.es_bulk_index

            def checked_bulk(index, docs, ids=None):
                if index not in {target.index for target in self.targets.values()}:
                    raise RuntimeError("cache experiment attempted a non-owned bulk write")
                count = original_bulk(index, docs, ids=ids)
                if count != len(docs):
                    raise RuntimeError(f"ES cache bulk wrote {count}/{len(docs)} documents")
                return count

            stack.enter_context(patch.object(self.es_module, "es_bulk_index", checked_bulk))
            # get(async) 内部 create_task 回写不属于 executor；仅支持同步 Agent。
            async def unsupported_async(*args, **kwargs):
                raise RuntimeError("ablation supports sync Agent/get_sync/put_sync only")

            stack.enter_context(patch.object(c.LLMCache, "get", unsupported_async))
            stack.enter_context(patch.object(c.LLMCache, "put", unsupported_async))
            if not asyncio.run(c.init_redis()):
                raise RuntimeError("L1 Redis unavailable; refusing zero-hit experiment")
            c._redis_sync.ping()
            self.stores = Stores(c._redis_sync, self.es_module.get_es(), self.es_module._CACHE_MAPPING,
                                 self.targets.values())
            self.stores.create(self.current)
            self.activate(self.current, enabled=True, l1=True, l2=True)
            return self
        except BaseException:
            self.__exit__(*sys.exc_info())
            raise

    def _instrument(self, original, layer, action):
        signature = inspect.signature(original)

        def start(args, kwargs):
            kind = signature.bind_partial(*args, **kwargs).arguments.get("cache_type", "answer")
            enabled = self.settings.LLM_CACHE_ENABLED and (
                self.settings.LLM_CACHE_REDIS_ENABLED if layer == "l1"
                else self.settings.LLM_CACHE_SEMANTIC_ENABLED)
            if not enabled:
                self.meter.incr(layer, kind, "read_skips" if action == "read" else "write_skips")
            return kind, enabled

        def finish(kind, result):
            counter = ("hits" if result else "misses") if action == "read" else ("write_oks" if result else "write_fails")
            self.meter.incr(layer, kind, counter)
            return result

        def failed(kind):
            self.meter.incr(layer, kind, "read_errors" if action == "read" else "write_fails")

        def sync(*args, **kwargs):
            kind, enabled = start(args, kwargs)
            if not enabled:
                return None if action == "read" else False
            try:
                return finish(kind, original(*args, **kwargs))
            except Exception:
                failed(kind)
                raise

        async def asynchronous(*args, **kwargs):
            kind, enabled = start(args, kwargs)
            if not enabled:
                return None if action == "read" else False
            try:
                return finish(kind, await original(*args, **kwargs))
            except Exception:
                failed(kind)
                raise

        return asynchronous if inspect.iscoroutinefunction(original) else sync

    def activate(self, target, *, enabled, l1, l2):
        self.drain()  # 未排空后台写入时禁止改变动态 key 前缀、索引或开关
        self.stores.check(target)
        if target not in self.stores.created:
            raise RuntimeError("activate requires an already created target")
        self.current = target
        s, c = self.settings, self.cache
        s.LLM_CACHE_ENABLED = enabled
        s.LLM_CACHE_REDIS_ENABLED = l1
        s.LLM_CACHE_SEMANTIC_ENABLED = l2
        # 原 L1 初始化之后不查 settings；改变可用性且用包装器阻止读写。
        c._redis_available = bool(enabled and l1)
        self.es_module.CACHE_ES_INDEX = target.index
        c._semantic_collection = None
        c._semantic_available = False
        if enabled and l2:
            collection = c._get_semantic_collection()
            if collection is None or collection._index != target.index:
                raise RuntimeError("L2 failed to initialize the isolated index")

    def drain(self):
        result = self.tracker.drain(self.timeout)
        if self.stores and self.current in self.stores.created:
            self.stores.es.indices.refresh(index=self.current.index)
        return result

    def __exit__(self, *exc):
        # 超时/异常不可先恢复 key 函数，否则未完成 worker 会写入生产前缀。
        # 专用进程退出 wait=True：安全优先，可能等待底层客户端超时。
        # 若 shutdown 本身被中断，保持隔离 patch；绝不在 worker 存活时恢复。
        self.cache._CACHE_WRITE_EXECUTOR.shutdown(wait=True)
        try:
            asyncio.run(self.cache.close_redis())
        finally:
            self.stack.close()


class NoFAQ:
    def get(self, *args, **kwargs):
        return None

    def put(self, *args, **kwargs):
        return None


def prepare_models(queries):
    from run_retrieval_ablation import warmup
    from app.rag.embeddings import get_embeddings
    started = time.perf_counter()
    warmup()
    embeddings = get_embeddings()
    for query in dict.fromkeys(queries):
        embeddings.embed_query(query)
    return {"latency_ms": (time.perf_counter() - started) * 1000,
            "embedding_queries": len(set(queries)), "bge_ready": True}


def read_only_mall(method, path, original, *args, **kwargs):
    if method.upper() not in {"GET", "HEAD"}:
        raise RuntimeError(f"只读消融禁止业务写入: {method} {path}")
    return original(method, path, *args, **kwargs)


def make_agent(user_id):
    """仅实际执行时调用；FAQ patch 必须早于 graph/nodes 导入。"""
    from app.agent.graph import run_agent
    from app.agent.react_agent import run_react_agent
    from mall_token import make_mall_user_token
    from app.agent.tools import mall_client
    from run_agent_e2e_ablation import patch_bindings

    token = os.environ.get("MALL_TOKEN") or make_mall_user_token(user_id=user_id)
    original_mall = mall_client.call_mall

    def agent(query):
        kwargs = {"query": query, "user_id": user_id, "mall_token": token, "conversation_history": []}
        with patch_bindings(mall_client, "call_mall", lambda method, path, *a, **k:
                            read_only_mall(method, path, original_mall, *a, **k)):
            result = run_agent(**kwargs)
            if result.get("uses_react"):
                rr = run_react_agent(**kwargs)
                result = {**result, **rr, "uses_react": True,
                          "react_tool_trace": rr.get("tool_trace", [])}
        return result

    return agent


class WarningCapture:
    def __init__(self):
        self.lock, self.records = threading.Lock(), []

    def __call__(self, message):
        record = message.record
        with self.lock:
            self.records.append({"level": record["level"].name, "message": record["message"]})

    def snapshot(self):
        with self.lock:
            return copy.deepcopy(self.records)


def response_errors(result):
    errors = []
    if not isinstance(result, dict):
        return ["Agent did not return a dict"]
    answer = result.get("answer")
    if not isinstance(answer, str) or not answer.strip():
        errors.append("empty/non-string answer")
    elif any(text in answer for text in (
        "系统暂时无法处理", "智能体服务暂时不可用", "智能体模式已关闭",
        "服务暂时不可用", "暂时无法完成", "请稍后重试",
    )):
        errors.append("Agent returned a fallback/error answer")

    def walk(value, path):
        if isinstance(value, dict):
            if value.get("error") or value.get("success") is False:
                errors.append(f"{path}: {value.get('error') or value.get('message') or 'success=false'}")
            for key, child in value.items():
                walk(child, f"{path}.{key}")
        elif isinstance(value, list):
            for i, child in enumerate(value):
                walk(child, f"{path}[{i}]")

    walk(result, "result")
    return errors


def check_phase(phase):
    layers, name = phase["layers"], phase["phase"]
    checks = []

    def check(label, passed):
        checks.append({"criterion": label, "passed": bool(passed)})

    hits = sum(layer_count(layers, layer, "hits") for layer in ("l1", "l2"))
    misses = sum(layer_count(layers, layer, "misses") for layer in ("l1", "l2"))
    writes = sum(layer_count(layers, layer, "write_oks") for layer in ("l1", "l2"))
    check("all requested answers succeeded", phase["n_success"] == phase["n"] and phase["n"] > 0)
    check("no layer/native write failures", phase["delta"]["write_fails"] == 0 and all(
        layer_count(layers, layer, "write_fails") == 0 for layer in ("l1", "l2")))
    if name == "P0_cache_off":
        check("cache-off: no hits or writes", hits == 0 and writes == 0)
    elif name == "P0b_cold_populate":
        check("cold seed exercised misses", misses > 0)
        for layer in ("l1", "l2"):
            check(f"{layer} answer seed written (not merely intent)", layer_count(layers, layer, "write_oks", "answer") > 0)
    elif name == "P1_l1_exact":
        check("at least one L1 ANSWER hit", layer_count(layers, "l1", "hits", "answer") > 0)
    elif name == "P2_l2_on":
        check("at least one L2 ANSWER hit; zero hits is NOT success", layer_count(layers, "l2", "hits", "answer") > 0)
    elif name == "P2b_l2_off":
        check("no paraphrase L1 answer hits from previous variant", layer_count(layers, "l1", "hits", "answer") == 0)
    elif name == "P3_novel":
        check("novel really missed (unexpected hits invalidate all-miss claim)", hits == 0 and misses > 0)
    if name in ("P0_cache_off", "P1_l1_exact", "P2b_l2_off"):
        check("L2 disabled: no actual reads/writes", all(
            layer_count(layers, "l2", counter) == 0 for counter in ("hits", "misses", "write_oks", "write_fails")))
    return checks


def run_phase(name, queries, agent, exp, warnings):
    before, layer_before = exp.cache.get_cache_metrics(), exp.meter.snapshot()
    detail = []
    for query in queries:
        row_before = exp.meter.snapshot()
        log_start = len(warnings.snapshot())
        result, errors = {}, []
        t0 = time.perf_counter()
        try:
            result = agent(query)
            errors.extend(response_errors(result))
        except Exception as exc:
            errors.append(f"{type(exc).__name__}: {exc}")
        elapsed = (time.perf_counter() - t0) * 1000
        # 延迟含完整 Agent/ReAct，不含 drain；每请求后排空保证逐条归属。
        drain_start = time.perf_counter()
        drain_error = None
        try:
            drain = exp.drain()
        except Exception as exc:
            drain = None
            drain_error = f"{type(exc).__name__}: {exc}"
            errors.append(drain_error)
        logs = warnings.snapshot()[log_start:]
        for log in logs:
            if log["level"] in ("ERROR", "CRITICAL") or re.search(
                r"失败|异常|超时|不可用|error|fail|timeout", log["message"], re.I
            ):
                errors.append(f"logged {log['level']}: {log['message']}")
        layers = layer_delta(row_before, exp.meter.snapshot())
        for counter in ("read_errors", "write_fails"):
            if any(layer_count(layers, layer, counter) for layer in ("l1", "l2")):
                errors.append(f"cache {counter}")
        detail.append({
            "query": query, "answer": result.get("answer", "") if isinstance(result, dict) else "",
            "result": result, "errors": errors, "ok": not errors,
            "elapsed_ms": elapsed, "latency_ms": elapsed if not errors else None,
            "drain_ms": (time.perf_counter() - drain_start) * 1000,
            "drain": drain, "layers": layers, "warnings": logs,
        })
        if drain_error:
            break  # 未完成写入时不继续请求/切换目标；保留部分证据
    after = exp.cache.get_cache_metrics()
    layers = layer_delta(layer_before, exp.meter.snapshot())
    lats = [row["latency_ms"] for row in detail if row["ok"]]
    delta = {key: after[key] - before[key] for key in NATIVE_COUNTERS}
    total = delta["l1_hits"] + delta["l2_hits"] + delta["misses"]
    phase = {
        "phase": name, "target": exp.current.metadata(), "n": len(queries),
        "n_attempted": len(detail), "n_success": len(lats), "n_failed": len(detail) - len(lats),
        "latency_avg_ms": sum(lats) / len(lats) if lats else None,
        "latency_min_ms": min(lats) if lats else None, "latency_max_ms": max(lats) if lats else None,
        "delta": delta, "layers": layers,
        "hit_ratio": (delta["l1_hits"] + delta["l2_hits"]) / total if total else None,
        "switches": {key: getattr(exp.settings, key) for key in (
            "LLM_CACHE_ENABLED", "LLM_CACHE_REDIS_ENABLED", "LLM_CACHE_SEMANTIC_ENABLED")},
        "detail": detail, "drain_failed": any(row["drain"] is None for row in detail),
    }
    phase["checks"] = check_phase(phase)
    phase["passed"] = all(check["passed"] for check in phase["checks"])
    return phase


def single_flight_probe(cache, timeout=10.0):
    """真实 SF 算法+假 producer，无 Agent/API。Barrier 同时释放 6 worker；
    on leader 等实际 5 follower 进入 Event.wait，off 等 6 producer 都进入。
    不靠固定 sleep 猜测并发重叠；计数加锁。
    """
    results = []
    for enabled in (True, False):
        barrier, release, lock = threading.Barrier(6), threading.Event(), threading.Lock()
        counts = {"producer": 0, "waiters": 0}
        flight_class = cache._SyncFlight

        class ObservedEvent:
            def __init__(self):
                self.event = threading.Event()

            def wait(self, timeout=None):
                with lock:
                    counts["waiters"] += 1
                    if counts["waiters"] == 5:
                        release.set()
                return self.event.wait(timeout)

            def set(self):
                self.event.set()

            def is_set(self):
                return self.event.is_set()

        def flight_factory():
            flight = flight_class()
            flight.done = ObservedEvent()
            return flight

        def producer():
            with lock:
                counts["producer"] += 1
                if not enabled and counts["producer"] == 6:
                    release.set()
            if not release.wait(timeout):
                raise TimeoutError("single-flight probe did not overlap all 6 workers")
            return "unit-probe-answer"

        key = "ablation:sf:" + uuid.uuid4().hex

        def worker(_):
            try:
                barrier.wait(timeout)
                start = time.perf_counter()
                answer = cache.single_flight_sync(key, producer)
                return {"answer": answer, "latency_ms": (time.perf_counter() - start) * 1000, "error": None}
            except Exception as exc:
                return {"answer": "", "latency_ms": None, "error": f"{type(exc).__name__}: {exc}"}

        with patch.object(cache.settings, "LLM_CACHE_SINGLE_FLIGHT_ENABLED", enabled), \
                patch.object(cache, "_SyncFlight", flight_factory), \
                patch.object(cache, "_SF_WAIT_TIMEOUT", timeout), \
                ThreadPoolExecutor(max_workers=6) as pool:
            rows = list(pool.map(worker, range(6)))
        expected = 1 if enabled else 6
        results.append({
            "phase": "P4_single_flight_on" if enabled else "P4_single_flight_off",
            "boundary": "unit probe only; synthetic producer, no Agent/API throughput claim",
            "key": key, "n": 6, "n_success": sum(row["error"] is None for row in rows),
            "producer_calls": counts["producer"], "expected_producer_calls": expected,
            "observed_waiters": counts["waiters"], "detail": rows,
            "passed": counts["producer"] == expected and all(
                row["error"] is None and row["answer"] == "unit-probe-answer" for row in rows),
        })
    return results


def positive_int(value):
    number = int(value)
    if number <= 0:
        raise argparse.ArgumentTypeError("must be positive")
    return number


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, help="new JSON file; never overwrite evidence")
    parser.add_argument("--limit", type=positive_int, default=len(QUERIES))
    parser.add_argument("--dry-run", action="store_true", help="stdlib-only; no token, app imports, files or API")
    parser.add_argument("--drain-timeout", type=positive_int, default=180)
    parser.add_argument("--ttl-seconds", type=positive_int, default=7200)
    parser.add_argument("--user-id", type=positive_int, default=4)
    return parser.parse_args(argv)


def execute(args, report, save):
    # 所有 app/token 导入都在 dry-run 分支之后，不改持久配置/环境变量。
    sys.path.insert(0, str(ROOT))
    sys.path.insert(0, str(HERE))
    from app.config import settings
    import app.rag.llm_cache as cache
    import app.rag.es_client as es_module
    from app.core.logger import logger

    targets = {label: Target(report["run_id"], label, uuid.uuid4().hex)
               for label in ("seed", "l2_on", "l2_off", "novel")}
    report["targets"] = [target.metadata() for target in targets.values()]
    report["settings"] = {key: getattr(settings, key) for key in (
        "LLM_MODEL_NAME", "LLM_TEMPERATURE", "LLM_CACHE_SEMANTIC_RERANK_THRESHOLD",
        "LLM_CACHE_SEMANTIC_K", "LLM_CACHE_SEMANTIC_MAXSIZE")}
    report["ttl_seconds"] = args.ttl_seconds
    save()
    warnings = WarningCapture()
    sink = logger.add(warnings, level="WARNING")
    try:
        with Experiment(cache, es_module, settings, targets, args.drain_timeout, args.ttl_seconds) as exp:
            import app.rag.retriever as retriever
            faq = NoFAQ()
            # patch 提供者和已绑定 nodes 引用，不清空也不使用既有 FAQ 缓存。
            with patch.object(retriever, "get_query_cache", lambda: faq):
                agent = make_agent(args.user_id)
                import app.agent.nodes as nodes
                with patch.object(nodes, "get_query_cache", lambda: faq):
                    def phase(name, queries):
                        result = run_phase(name, queries, agent, exp, warnings)
                        report["phases"].append(result)
                        save()
                        print(f"{name}: n={result['n_success']}/{result['n']} passed={result['passed']}")
                        if result["drain_failed"]:
                            raise RuntimeError("drain failed; refusing further cache state transition")
                        return result

                    report["warmup"] = prepare_models(
                        QUERIES[:args.limit] + PARAPHRASES[:args.limit] + NOVEL[:args.limit])
                    save()
                    exp.activate(targets["seed"], enabled=False, l1=False, l2=False)
                    phase("P0_cache_off", QUERIES[:args.limit])
                    empty = exp.stores.snapshot(targets["seed"])
                    if empty["redis"] or empty["es"]:
                        raise RuntimeError("cache-off unexpectedly populated cache")
                    exp.activate(targets["seed"], enabled=True, l1=True, l2=True)
                    phase("P0b_cold_populate", QUERIES[:args.limit])
                    exp.drain()
                    seed = exp.stores.snapshot(targets["seed"])
                    if not seed["redis"] or not seed["es"]:
                        raise RuntimeError("empty seed; cannot run valid L1/L2 ablation")
                    digest = snapshot_digest(seed)
                    report["seed"] = {"sha256": digest, "snapshot": seed,
                                      "source": "P0b QUERIES only; before P1 or PARAPHRASES"}
                    # P1/P2 前创建两个同源副本；每个变体开始前再验 hash。
                    report["clone_hashes"] = {label: exp.stores.clone(seed, targets[label])
                                              for label in ("l2_on", "l2_off")}
                    save()
                    exp.activate(targets["seed"], enabled=True, l1=True, l2=False)
                    phase("P1_l1_exact", QUERIES[:args.limit])
                    for label, name, l2 in (("l2_on", "P2_l2_on", True), ("l2_off", "P2b_l2_off", False)):
                        exp.activate(targets[label], enabled=True, l1=True, l2=l2)
                        actual = snapshot_digest(exp.stores.snapshot(targets[label]))
                        report.setdefault("variant_start_hashes", {})[label] = actual
                        save()
                        if actual != digest:
                            raise RuntimeError(f"{label} did not start from unchanged seed snapshot")
                        phase(name, PARAPHRASES[:args.limit])
                    exp.stores.create(targets["novel"])
                    exp.activate(targets["novel"], enabled=True, l1=True, l2=True)
                    phase("P3_novel", NOVEL[:args.limit])
                    exp.drain()
                    report["phases"].extend(single_flight_probe(cache))
                    report["final_metrics_snapshot"] = cache.get_cache_metrics()
                    report["passed"] = all(phase["passed"] for phase in report["phases"])
                    save()
    finally:
        logger.remove(sink)


def main(argv=None):
    args = parse_args(argv)
    run_id = uuid.uuid4().hex
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S_%fZ")
    output = (args.output or HERE / f"results_cache_{timestamp}_{run_id[:8]}.json").resolve()
    report = {
        "run_id": run_id, "created_at": timestamp, "output": str(output),
        "dry_run": args.dry_run, "passed": False, "phases": [],
        "queries": QUERIES[:args.limit], "paraphrases": PARAPHRASES[:args.limit], "novel": NOVEL[:args.limit],
        "plan": ["P0_cache_off", "P0b_cold_populate", "P1_l1_exact", "P2_l2_on", "P2b_l2_off",
                 "P3_novel", "P4_single_flight_on", "P4_single_flight_off"],
        "boundary": "full synchronous Agent including uses_react; FAQ/Bloom off; SF off except paired unit probe",
        "comparison": "P2/P2b independent clones of drained P0b seed; L2 read+write on/off; intra-variant backfills retained",
        "caveats": ["BGE and all input embeddings warmed before P0; derived tool queries may still differ",
                    "tool context_hash may change; zero answer hits fail criteria",
                    "latencies exclude drain; failed/fallback answers excluded; full answers and warnings retained",
                    "P3 starts in an empty separate target, not a warm-cache novel-query test",
                    "ES indexes retained; experiment Redis keys have TTL; no cache deletion",
                    "drain timeout aborts; teardown waits for workers before restoring patches"],
    }
    if args.dry_run:
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0
    # x 模式避免覆盖旧实验结果；父目录必须已经存在。
    with output.open("x", encoding="utf-8") as stream:
        def save():
            stream.seek(0)
            json.dump(report, stream, ensure_ascii=False, indent=2, default=str)
            stream.truncate()
            stream.flush()

        save()
        try:
            execute(args, report, save)
        except Exception as exc:
            report["fatal_error"] = f"{type(exc).__name__}: {exc}"
            report["passed"] = False
            save()
            print(report["fatal_error"], file=sys.stderr)
    print(f"Evidence: {output}; passed={report['passed']}")
    return 0 if report["passed"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
