# -*- coding: utf-8 -*-
"""P2 验收：HTTP 端到端（管理端上传/删除闭环 + chat 流式 + ES 数量对照）。"""
import sys, io, time, json
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.path.insert(0, r"d:\Desktop\stellar-mall\rag-backend")

import requests
from app.config import settings
from app.core.security import create_access_token
from app.rag.es_client import get_es, KB_ES_INDEX

# rag-backend 全局 API 前缀为 /api（main.py include_router 时统一加）
BASE = "http://127.0.0.1:8000/api"
TOKEN = create_access_token(1, extra={"role": "ADMIN"})
H = {"Authorization": f"Bearer {TOKEN}"}

def es_count() -> int:
    return int(get_es().count(index=KB_ES_INDEX)["count"])

def retrieve_one(query: str) -> list:
    from app.rag.retriever import hybrid_retrieve
    return [str((d.metadata or {}).get("doc_name") or "?") for d in hybrid_retrieve(query, top_k=5)]

# ---------- 1. 管理端上传 ----------
marker = "量子检验员专属冰箱贴纸ULID8319"
files = {"file": ("p2验收-量子贴纸.md", f"# 量子检验员专属冰箱贴纸\n\n{marker} 是本商城限量周边，仅用于 P2 迁移验收。\n".encode("utf-8"), "text/markdown")}
c0 = es_count()
r = requests.post(f"{BASE}/kb/documents", headers=H, files=files, data={"tags": "P2验收,周边"}, timeout=120)
doc = r.json()
print(f"[上传] http={r.status_code} resp={json.dumps(doc, ensure_ascii=False)[:400]}")
if doc.get("code") != 0 or not doc.get("data"):
    print("[ABORT] 上传失败")
    raise SystemExit(1)
doc_id = doc["data"]["id"]

time.sleep(1.5)  # 等 refresh
c1 = es_count()
print(f"[数量] ES: {c0} -> {c1} (+{c1 - c0})")

# chunks 预览接口
r = requests.get(f"{BASE}/kb/documents/{doc_id}/chunks", headers=H, timeout=30)
print(f"[chunks] http={r.status_code} count={len(r.json()['data'].get('chunks', []))}")

# ---------- 2. 新内容可检索（写路径 → 读路径闭环） ----------
hits = retrieve_one(marker)
print(f"[检索] marker 命中: {any(marker in h for h in hits)} -> {hits[:2]}")

# ---------- 3. 删除闭环 ----------
r = requests.delete(f"{BASE}/kb/documents/{doc_id}", headers=H, timeout=60)
print(f"[删除] http={r.status_code} msg={r.json().get('message')}")
time.sleep(1.5)
c2 = es_count()
hits2 = retrieve_one(marker)
print(f"[数量] ES: {c1} -> {c2}；marker 仍可召回: {any(marker in h for h in hits2)}")

# ---------- 4. chat 流式端到端 ----------
r = requests.post(f"{BASE}/chat", headers=H, json={"query": "X100 Pro Max 的电池容量是多少？"}, stream=True, timeout=180)
tokens, got_sources, got_done, cur_event = [], False, False, ""
for line in r.iter_lines(decode_unicode=True):
    if not line:
        continue
    if line.startswith("event: "):
        cur_event = line[7:].strip()
        continue
    if line.startswith("data: "):
        try:
            payload = json.loads(line[6:])
        except Exception:
            continue
        if cur_event == "token":
            tokens.append(payload.get("content", ""))
        elif cur_event == "sources":
            got_sources = True
        elif cur_event == "done":
            got_done = True
answer = "".join(tokens)
print(f"[chat] http={r.status_code} sources={got_sources} done={got_done} tokens={len(tokens)}")
print(f"[chat] answer: {answer[:200]}")

ok_all = (c2 == c0) and got_done and len(answer) > 10
print(f"\n[RESULT] {'PASS' if ok_all else 'CHECK'}")
