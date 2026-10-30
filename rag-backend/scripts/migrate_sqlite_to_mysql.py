"""P3-2 一次性迁移脚本：rag-backend SQLite → 商城 MySQL（stellar_mall 库）。

迁移 5 张表（表名已带 stellar_rag_ 前缀，由 app.models 定义）：
    stellar_rag_users / stellar_rag_conversations / stellar_rag_messages
    / stellar_rag_kb_documents / stellar_rag_operation_logs

原则：
  - 保 id（kb_documents.id 被 ES chunk id `doc{kb_id}_chunk{i}` 引用，必须一致）
  - 目标表已有数据则中止（防主键冲突/重复迁移），支持 --force 清空重迁
  - 迁移后重置 AUTO_INCREMENT 为 max(id)+1
  - 行数校验 + 抽样校验

用法（rag-backend 目录、全局 Python）:
    python scripts/migrate_sqlite_to_mysql.py [--force]
"""
from __future__ import annotations

import sqlite3
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.config import settings
from app.core.database import Base, engine

SQLITE_PATH = Path(__file__).resolve().parent.parent / "data" / "sqlite" / "app.db"

# 迁移顺序 = 外键依赖序
TABLES = [
    "stellar_rag_users",
    "stellar_rag_conversations",
    "stellar_rag_messages",
    "stellar_rag_kb_documents",
    "stellar_rag_operation_logs",
]


def main() -> int:
    force = "--force" in sys.argv
    if not SQLITE_PATH.exists():
        print(f"[ABORT] SQLite 文件不存在: {SQLITE_PATH}")
        return 1

    src = sqlite3.connect(str(SQLITE_PATH))
    src.row_factory = sqlite3.Row

    # 1) 建目标表（幂等，已存在则跳过）
    from app.models import user, conversation, message, kb_document, operation_log  # noqa
    Base.metadata.create_all(bind=engine)

    with engine.begin() as conn:
        for t in TABLES:
            n = conn.exec_driver_sql(f"SELECT COUNT(*) FROM `{t}`").scalar()
            if n:
                if not force:
                    print(f"[ABORT] 目标表 {t} 已有 {n} 行，防止重复迁移。确认无误后用 --force 清空重迁")
                    return 1
                conn.exec_driver_sql(f"SET FOREIGN_KEY_CHECKS=0")
                conn.exec_driver_sql(f"TRUNCATE TABLE `{t}`")
                conn.exec_driver_sql(f"SET FOREIGN_KEY_CHECKS=1")
                print(f"[force] 已清空 {t}（{n} 行）")

    # 2) 逐表迁移（保 id）
    from sqlalchemy import table as sa_table, column as sa_col, MetaData, Table, insert
    meta = MetaData()
    total_ok = 0
    for t in TABLES:
        rows = [dict(r) for r in src.execute(f"SELECT * FROM {t.replace('stellar_rag_', '')}")]
        if not rows:
            print(f"[{t}] 源 0 行，跳过")
            continue
        tbl = Table(t, meta, autoload_with=engine)
        cols = [c.name for c in tbl.columns]
        payload = [{c: r[c] for c in cols if c in r} for r in rows]
        with engine.begin() as conn:
            conn.execute(insert(tbl), payload)
            max_id = conn.exec_driver_sql(f"SELECT COALESCE(MAX(id), 0) FROM `{t}`").scalar()
            if t != "stellar_rag_operation_logs" or max_id:
                conn.exec_driver_sql(f"ALTER TABLE `{t}` AUTO_INCREMENT = {max_id + 1}")
        total_ok += len(payload)
        print(f"[{t}] 迁移 {len(payload)} 行（id 1..{max_id}）")

    # 3) 校验
    with engine.connect() as conn:
        ok = True
        for t in TABLES:
            s = src.execute(f"SELECT COUNT(*) FROM {t.replace('stellar_rag_', '')}").fetchone()[0]
            d = conn.exec_driver_sql(f"SELECT COUNT(*) FROM `{t}`").scalar()
            mark = "OK" if s == d else "FAIL"
            if s != d:
                ok = False
            print(f"[校验] {t}: sqlite={s} mysql={d} {mark}")
        # 抽样：kb_documents 首行 filename 一致性
        s1 = src.execute("SELECT id, filename FROM kb_documents ORDER BY id LIMIT 1").fetchone()
        d1 = conn.exec_driver_sql(
            "SELECT id, filename FROM `stellar_rag_kb_documents` ORDER BY id LIMIT 1").fetchone()
        if s1 and (d1 is None or d1[0] != s1[0] or d1[1] != s1[1]):
            ok = False
            print(f"[FAIL] 抽样不一致: {s1} vs {d1}")

    print(f"\n[RESULT] {'PASS' if ok else 'FAIL'}  共迁移 {total_ok} 行")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
