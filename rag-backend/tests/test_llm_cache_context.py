"""L2 答案缓存上下文匹配测试。

验证 context_keys（来源内容指纹）只放宽分数/顺序/问句差异，
证据集合不同或老条目无指纹时仍保持严格过滤。

运行：
    cd rag-backend
    python -m pytest tests/test_llm_cache_context.py -v
"""
from app.agent import nodes as nodes_module
from app.agent.nodes import _evidence_keys
from app.rag import llm_cache as cache_module
from app.rag.llm_cache import _context_matches


def test_same_evidence_ignores_hash_and_order():
    meta = {"context_hash": "old", "context_keys_json": '["b", "a"]'}
    assert _context_matches(meta, "new", ["a", "b"]) is True


def test_different_evidence_is_rejected():
    meta = {"context_hash": "h", "context_keys_json": '["a", "b"]'}
    assert _context_matches(meta, "h", ["a", "c"]) is False


def test_subset_and_superset_are_rejected():
    meta = {"context_hash": "h", "context_keys_json": '["a"]'}
    assert _context_matches(meta, "h", ["a", "b"]) is False
    assert _context_matches(meta, "h", []) is False


def test_legacy_entry_falls_back_to_context_hash():
    assert _context_matches({"context_hash": "h"}, "h", ["a"]) is True
    assert _context_matches({"context_hash": "h"}, "other", ["a"]) is False


def test_malformed_keys_fall_back_to_context_hash():
    meta = {"context_hash": "h", "context_keys_json": "not-json"}
    assert _context_matches(meta, "h", ["a"]) is True
    assert _context_matches(meta, "other", ["a"]) is False


def test_no_context_signal_allows_legacy_behaviour():
    assert _context_matches({}, None, None) is True
    assert _context_matches({}, None, ["a"]) is True


def test_evidence_keys_ignore_score_order_and_query_text():
    a = {
        "success": True, "query": "问句一", "message": "搜索「问句一」",
        "sources": [
            {"doc_name": "doc", "chunk_index": 1, "content": "规格", "score": 0.9},
            {"doc_name": "doc", "chunk_index": 2, "content": "参数", "score": 0.1},
        ],
    }
    b = {
        "success": True, "query": "问句二", "message": "搜索「问句二」",
        "sources": [
            {"doc_name": "doc", "chunk_index": 2, "content": "参数", "score": 0.2},
            {"doc_name": "doc", "chunk_index": 1, "content": "规格", "score": 0.8},
        ],
    }
    assert _evidence_keys(a) == _evidence_keys(b)


def test_evidence_keys_cover_all_tool_evidence():
    base = {"success": True, "data": [{"id": 1, "total_stock": 10}], "kb_specs": None}
    assert _evidence_keys(base) != _evidence_keys({**base, "data": [{"id": 1, "total_stock": 9}]})
    assert _evidence_keys(base) != _evidence_keys(
        {**base, "kb_specs": [{"doc_name": "doc", "content": "规格"}]}
    )


def test_evidence_keys_skip_invalid_and_empty():
    assert _evidence_keys({}) == []
    assert _evidence_keys(None) == []
    assert len(_evidence_keys({"success": True})) == 1


def test_prompt_hash_reads_latest_cache_value(monkeypatch):
    monkeypatch.setattr(cache_module, "AGENT_PROMPT_HASH", "fresh-hash")
    assert nodes_module._prompt_hash("AGENT_PROMPT_HASH") == "fresh-hash"


def test_module_wrappers_forward_context_keys(monkeypatch):
    calls = {}

    class FakeCache:
        def get_sync(self, *args, **kwargs):
            calls["get"] = kwargs
            return None

        def put_sync(self, *args, **kwargs):
            calls["put"] = kwargs

    monkeypatch.setattr(cache_module, "get_llm_cache", lambda: FakeCache())
    cache_module.get_cache_sync("q", "m", 0.0, "h", "ctx", context_keys=["k"])
    cache_module.put_cache_sync("q", "m", 0.0, "h", "ctx", "a", [], "i", 1, context_keys=["k"])
    assert calls["get"]["context_keys"] == ["k"]
    assert calls["put"]["context_keys"] == ["k"]
