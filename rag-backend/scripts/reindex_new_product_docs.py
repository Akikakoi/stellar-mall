"""把 2026-11-17 新增商品 KB 文档重新按更细粒度分块。

默认 doc_id 88-120、chunk_size=400、overlap=80（原默认 1024/200，整份文档只有 1 个 chunk）。
用法：
  python scripts/reindex_new_product_docs.py --dry-run
  python scripts/reindex_new_product_docs.py --doc-id 88
  python scripts/reindex_new_product_docs.py
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.config import settings
from app.core.database import SessionLocal
from app.models.kb_document import KbDocument
from app.models.user import User
from app.services.kb_service import KbService

DOC_IDS = list(range(88, 121))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--doc-id", type=int, action="append")
    ap.add_argument("--chunk-size", type=int, default=400)
    ap.add_argument("--overlap", type=int, default=80)
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    ids = args.doc_id or DOC_IDS
    settings.CHUNK_SIZE = args.chunk_size
    settings.CHUNK_OVERLAP = args.overlap

    db = SessionLocal()
    try:
        admin = db.query(User).filter(User.username == "admin").first()
        if not admin:
            raise SystemExit("找不到 RAG admin 用户")
        docs = db.query(KbDocument).filter(KbDocument.id.in_(ids)).order_by(KbDocument.id).all()
        print(f"docs={len(docs)} chunk_size={args.chunk_size} overlap={args.overlap}")
        if args.dry_run:
            for d in docs:
                print(d.id, d.filename, d.status, d.chunk_count)
            return

        service = KbService(db)
        ok = 0
        for i, d in enumerate(docs, 1):
            before = d.chunk_count
            try:
                res = service.reindex(d.id, operator=admin)
                status = res.get("status")
                chunks = res.get("chunk_count")
                print(f"[{i}/{len(docs)}] doc={d.id} {status} chunks {before}->{chunks} {d.filename}")
                if status == "ready" and chunks and chunks > 1:
                    ok += 1
            except Exception as e:
                print(f"[{i}/{len(docs)}] FAIL doc={d.id} {e}")
        print(f"done ready-with-multiple-chunks: {ok}/{len(docs)}")
    finally:
        db.close()


if __name__ == "__main__":
    main()