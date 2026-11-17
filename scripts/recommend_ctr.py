"""推荐位 CTR 统计：曝光、点击、点击率、来源分布。

数据来源：stellar_user_behavior
- 曝光：event_type=view_item_list，scene=recommend_*
- 点击：event_type=view_item，scene=recommend_*
- 曝光事件 extra.itemIds 记录本次推荐商品列表，可算单商品曝光量

用法：
  python scripts/recommend_ctr.py
  python scripts/recommend_ctr.py --days 7 --scene recommend_home
  python scripts/recommend_ctr.py --out .workbuddy/tmp/recommend_ctr.json
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from collections import Counter, defaultdict
from datetime import datetime

import pymysql

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

DB = dict(host="127.0.0.1", port=3306, user="stellar", password="123456",
          database="stellar_mall", charset="utf8mb4")
SCENES = ("recommend_home", "recommend_detail", "recommend_cart")


def load_rows(days: int, scene: str | None):
    conn = pymysql.connect(**DB)
    try:
        with conn.cursor() as cur:
            sql = """
                SELECT user_id, device_id, event_type, scene, spu_id, position, extra, event_time
                FROM stellar_user_behavior
                WHERE scene LIKE 'recommend_%%'
                  AND event_type IN ('view_item_list', 'view_item')
                  AND event_time >= DATE_SUB(NOW(), INTERVAL %s DAY)
            """
            params = [days]
            if scene:
                sql += " AND scene = %s"
                params.append(scene)
            sql += " ORDER BY event_time"
            cur.execute(sql, params)
            return cur.fetchall()
    finally:
        conn.close()


def parse_extra(raw):
    try:
        return json.loads(raw) if raw else {}
    except Exception:
        return {}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--days", type=int, default=30)
    ap.add_argument("--scene", default=None)
    ap.add_argument("--out", default="recommend_ctr_result.json")
    args = ap.parse_args()

    rows = load_rows(args.days, args.scene)
    stats = defaultdict(lambda: dict(
        exposures=0, clicks=0, item_impressions=0,
        exposure_devices=set(), click_devices=set(),
        exposure_users=set(), click_users=set(),
        sources=Counter(), modules=Counter(), daily=defaultdict(lambda: dict(exposures=0, clicks=0))
    ))

    for user_id, device_id, event_type, scene, spu_id, position, extra_raw, event_time in rows:
        s = stats[scene]
        day = event_time.date().isoformat()
        extra = parse_extra(extra_raw)
        if event_type == "view_item_list":
            s["exposures"] += 1
            ids = extra.get("itemIds") or []
            s["item_impressions"] += len(ids) if isinstance(ids, list) else 0
            if device_id:
                s["exposure_devices"].add(device_id)
            if user_id:
                s["exposure_users"].add(user_id)
            if extra.get("module"):
                s["modules"][extra["module"]] += 1
            s["daily"][day]["exposures"] += 1
        elif event_type == "view_item":
            s["clicks"] += 1
            if device_id:
                s["click_devices"].add(device_id)
            if user_id:
                s["click_users"].add(user_id)
            if extra.get("source"):
                s["sources"][extra["source"]] += 1
            if extra.get("module"):
                s["modules"][extra["module"]] += 1
            s["daily"][day]["clicks"] += 1

    result = []
    for scene in sorted(stats):
        s = stats[scene]
        exp = s["exposures"]
        clicks = s["clicks"]
        result.append(dict(
            scene=scene,
            exposures=exp,
            clicks=clicks,
            ctr=round(clicks / exp, 4) if exp else 0.0,
            item_impressions=s["item_impressions"],
            item_ctr=round(clicks / s["item_impressions"], 4) if s["item_impressions"] else 0.0,
            exposure_devices=len(s["exposure_devices"]),
            click_devices=len(s["click_devices"]),
            exposure_users=len(s["exposure_users"]),
            click_users=len(s["click_users"]),
            sources=dict(s["sources"]),
            modules=dict(s["modules"]),
            daily={d: dict(exposures=v["exposures"], clicks=v["clicks"],
                           ctr=round(v["clicks"] / v["exposures"], 4) if v["exposures"] else 0.0)
                   for d, v in sorted(s["daily"].items())},
        ))

    header = f"{'场景':<20}{'曝光':>8}{'点击':>8}{'CTR':>9}{'商品曝光':>10}{'商品CTR':>10}{'曝光设备':>10}{'点击设备':>10}"
    print(header)
    print("-" * len(header))
    for r in result:
        print(f"{r['scene']:<20}{r['exposures']:>8}{r['clicks']:>8}{r['ctr']:>9.3f}"
              f"{r['item_impressions']:>10}{r['item_ctr']:>10.3f}"
              f"{r['exposure_devices']:>10}{r['click_devices']:>10}")
        if r["sources"]:
            print("  来源分布:", r["sources"])
        if r["daily"]:
            print("  按天:", r["daily"])

    payload = dict(
        generated_at=datetime.now().isoformat(timespec="seconds"),
        days=args.days,
        scene=args.scene,
        scenes=SCENES,
        results=result,
        note="曝光按推荐位进入视口计一次；点击为推荐卡点击。数据量小时 CTR 波动大，仅用于趋势观察。",
    )
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    print("saved", os.path.abspath(args.out))


if __name__ == "__main__":
    main()