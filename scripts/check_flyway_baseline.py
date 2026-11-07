"""检查现有 MySQL 是否已包含 V2-V28 的 schema，判断能否安全执行 Flyway baseline 28。

用法：
    pip install pymysql
    DB_PASSWORD=... python scripts/check_flyway_baseline.py

环境变量：DB_HOST(默认 127.0.0.1)、DB_PORT(3306)、DB_USER(stellar)、
DB_PASSWORD、DB_NAME(stellar_mall)。

退出码：0 可 baseline；1 缺少 V2-V28 对象，禁止 baseline；已有 Flyway 历史时只报告当前版本。
"""
from __future__ import annotations

import os
import sys

import pymysql

REQUIRED_TABLES = {
    "stellar_user_address", "stellar_review", "stellar_coupon", "stellar_user_coupon",
    "stellar_banner", "stellar_review_comment", "stellar_user_message", "stellar_after_sale",
    "stellar_wallet", "stellar_wallet_transaction", "stellar_home_module", "stellar_email_code",
    "stellar_notification_log", "stellar_points_rule", "stellar_user_points",
    "stellar_points_record", "stellar_points_product", "stellar_points_redemption",
    "stellar_checkin_record", "stellar_points_payment", "stellar_user_behavior",
    "stellar_site_config", "stellar_stock_log", "stellar_browse_history",
    "stellar_service_item", "stellar_spu_service",
}
REQUIRED_COLUMNS = {
    "stellar_mall_order": {
        "tracking_no", "delivery_company", "delivery_time",
        "points_deducted", "points_amount", "consignee", "phone",
    },
    "stellar_mall_order_item": {"extra_amount", "service_info"},
    "stellar_cart": {"extra_amount", "service_info"},
    "stellar_spu": {"per_user_limit"},
    "stellar_mall_user": {"email"},
}


def main() -> int:
    conn = pymysql.connect(
        host=os.environ.get("DB_HOST", "127.0.0.1"),
        port=int(os.environ.get("DB_PORT", "3306")),
        user=os.environ.get("DB_USER", "stellar"),
        password=os.environ.get("DB_PASSWORD", ""),
        database=os.environ.get("DB_NAME", "stellar_mall"),
        charset="utf8mb4",
    )
    try:
        with conn.cursor() as cur:
            cur.execute(
                "SELECT table_name FROM information_schema.tables "
                "WHERE table_schema = DATABASE()"
            )
            tables = {row[0] for row in cur.fetchall()}

            if "flyway_schema_history" in tables:
                cur.execute(
                    "SELECT version, description, success FROM flyway_schema_history "
                    "ORDER BY installed_rank DESC LIMIT 5"
                )
                print("已存在 flyway_schema_history，最近记录：")
                for row in cur.fetchall():
                    print("  ", row)
                print("无需 baseline；直接执行 migrate 即可。")
                return 0

            missing_tables = sorted(REQUIRED_TABLES - tables)
            missing_columns = {}
            for table, required in REQUIRED_COLUMNS.items():
                cur.execute(
                    "SELECT column_name FROM information_schema.columns "
                    "WHERE table_schema = DATABASE() AND table_name = %s",
                    (table,),
                )
                have = {row[0] for row in cur.fetchall()}
                missing = sorted(required - have)
                if missing:
                    missing_columns[table] = missing

            cur.execute(
                "SELECT COUNT(*) FROM information_schema.columns "
                "WHERE table_schema = DATABASE() AND table_name = 'stellar_mall_order' "
                "AND column_name = 'is_refunded'"
            )
            has_is_refunded = cur.fetchone()[0] > 0

            problems = []
            if missing_tables:
                problems.append(f"缺少表: {missing_tables}")
            if missing_columns:
                problems.append(f"缺少列: {missing_columns}")
            if "phone" in _columns(conn, "stellar_mall_user"):
                problems.append("stellar_mall_user.phone 仍存在（V25 未应用）")
            if "stellar_sms_code" in tables:
                problems.append("stellar_sms_code 仍存在（V15 未应用）")

            if problems:
                print("禁止 baseline 28：")
                for problem in problems:
                    print("  -", problem)
                return 1

            print("V2-V28 schema 检查通过，可 safe baseline 到 28。")
            print("is_refunded 已存在" if has_is_refunded else "is_refunded 缺失，baseline 28 后由 V29 补齐")
            print("建议一次性启动参数：--spring.flyway.enabled=true "
                  "--spring.flyway.baseline-on-migrate=true --spring.flyway.baseline-version=28")
            return 0
    finally:
        conn.close()


def _columns(conn, table: str) -> set[str]:
    with conn.cursor() as cur:
        cur.execute(
            "SELECT column_name FROM information_schema.columns "
            "WHERE table_schema = DATABASE() AND table_name = %s",
            (table,),
        )
        return {row[0] for row in cur.fetchall()}


if __name__ == "__main__":
    sys.exit(main())