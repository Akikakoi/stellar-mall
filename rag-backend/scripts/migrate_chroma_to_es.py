"""P2 一次性迁移脚本：Chroma 知识库 collection → Elasticsearch。

搬迁内容：ecommerce_knowledge_base 全量 chunk（text + vector + metadata），
LLM 语义缓存（llm_semantic_cache）为可丢弃数据，不迁移。

用法（在 rag-backend 目录、全局 Python 环境下）:
    python scripts/migrate_chroma_to_es.py

步骤：读取 Chroma → 规整 metadata（tags 拆数组 / doc_id 转 str / 剔除 None）
→ bulk 写入 stellar_kb_chunks → refresh → 数量校验。幂等：按原 id 覆盖写入。
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

# 保证可从 rag-backend 目录直接运行
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import chromadb

from app.core.logger import logger
from app.rag.es_client import KB_ES_INDEX, es_bulk_index, es_refresh, ensure_kb_index, get_es

CHROMA_DIR = Path(__file__).resolve().parent.parent / "data" / "chroma"
KB_COLLECTION = "ecommerce_knowledge_base"


def _split_tags(raw) -> list:
    if not raw:
        return []
    if isinstance(raw, list):
        return [str(t).strip() for t in raw if str(t).strip()]
    return [t.strip() for t in re.split(r"[,，]", str(raw)) if t.strip()]


def _normalize(meta: dict) -> dict:
    doc = dict(meta or {})
    if "tags" in doc:
        doc["tags"] = _split_tags(doc.get("tags"))
    if doc.get("doc_id") is not None:
        doc["doc_id"] = str(doc["doc_id"])
    return {k: v for k, v in doc.items() if v is not None}


def main() -> int:
    # 1) 读 Chroma
    client = chromadb.PersistentClient(path=str(CHROMA_DIR))
    col = client.get_collection(KB_COLLECTION)
    batch = col.get(include=["documents", "metadatas", "embeddings"])
    # Chroma 1.x 返回 numpy 数组，不能用 `or []` 判空（ambiguous truth value）
    ids = list(batch.get("ids") or [])
    docs = [str(d) for d in batch.get("documents")] if batch.get("documents") is not None else []
    metas = list(batch.get("metadatas")) if batch.get("metadatas") is not None else []
    vecs = [list(v) for v in batch.get("embeddings")] if batch.get("embeddings") is not None else []
    total = len(ids)
    print(f"[Chroma] collection={KB_COLLECTION} 共 {total} 条 chunk")

    # 向量维度校验（text-embedding-v2 = 1536）
    dims = {len(v) for v in vecs}
    if dims != {1536}:
        print(f"[ABORT] 向量维度异常: {dims}，预期 {{1536}}")
        return 1

    # 2) 写 ES（按原 id 覆盖，幂等）
    ensure_kb_index()
    payload = []
    for i, _id in enumerate(ids):
        doc = _normalize(metas[i])
        doc["content"] = docs[i]
        doc["vec"] = list(vecs[i])
        payload.append(doc)
    ok = es_bulk_index(KB_ES_INDEX, payload, ids=ids)
    es_refresh(KB_ES_INDEX)
    print(f"[ES] bulk 写入完成: {ok}/{total}")

    # 3) 校验
    es_count = int(get_es().count(index=KB_ES_INDEX)["count"])
    print(f"[校验] Chroma={total}  ES={es_count}")
    if es_count < total:
        print("[FAIL] ES 数量少于 Chroma，请检查 bulk 错误日志")
        return 1

    # 4) 抽样 spot-check：同一 id 内容一致
    sample = min(3, total)
    for i in range(sample):
        got = get_es().get(index=KB_ES_INDEX, id=ids[i])["_source"]
        if got.get("content") != docs[i]:
            print(f"[FAIL] 抽样不一致 id={ids[i]}")
            return 1
    print(f"[PASS] 迁移完成，抽样 {sample} 条内容一致")
    return 0


if __name__ == "__main__":
    logger.remove()  # 迁移脚本用 print 输出，避免 loguru 重复刷屏
    raise SystemExit(main())
