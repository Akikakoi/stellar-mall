# -*- coding: utf-8 -*-
"""P2 验收：ES RRF 混合检索质量冒烟（语义/精确/tags/乱码）。"""
import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
sys.path.insert(0, r"d:\Desktop\stellar-mall\rag-backend")

from app.rag.retriever import hybrid_retrieve

CASES = [
    ("语义兜底", "夏天冷藏食物的大柜子", None, ["冰箱", "冰柜", "冷藏"]),
    ("语义兜底2", "给老人用的手机", None, ["手机", "老人"]),
    ("精确词", "X100 Pro 续航", None, ["X100", "续航"]),
    ("tags过滤", "手机充电速度", ["手机"], ["手机"]),
    ("乱码护栏", "asdfqwer zxcv 12345 !@#", None, []),
]

for name, q, tags, expect in CASES:
    docs = hybrid_retrieve(q, top_k=5, tags_filter=tags)
    got = []
    for d in docs:
        meta = d.metadata or {}
        label = meta.get("doc_name") or meta.get("name") or meta.get("title") or "?"
        got.append(str(label))
    hit = any(any(e in g for g in got) for e in expect) if expect else True
    print(f"[{name}] q={q!r} tags={tags} -> {len(docs)} 条, 命中={hit}")
    for g in got:
        print(f"    - {g}")
