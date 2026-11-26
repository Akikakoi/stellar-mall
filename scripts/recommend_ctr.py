"""推荐位 CTR 统计：曝光、点击、点击率、来源分布，并按 A/B 分桶对比。

数据来源：stellar_user_behavior
- 曝光：event_type=view_item_list，scene=recommend_*
- 点击：event_type=view_item，scene=recommend_*
- 曝光事件 extra.itemIds 记录本次推荐商品列表，可算单商品曝光量
- A/B 桶：曝光与点击的 extra.abBucket（A 对照 / B 实验），历史数据为 unknown

用法：
  python scripts/recommend_ctr.py
  python scripts/recommend_ctr.py --days 7 --scene recommend_home
  python scripts/recommend_ctr.py --out .workbuddy/tmp/recommend_ctr.json
"""
from __future__ import annotations

import argparse
import json
import math
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


def wilson_interval(x: int, n: int, z: float = 1.96) -> tuple[float, float]:
    """比例的 Wilson 置信区间。小样本下比正态近似稳，曝光几十次也不会给出负下界。"""
    if n <= 0:
        return (0.0, 0.0)
    p = x / n
    denom = 1 + z * z / n
    center = (p + z * z / (2 * n)) / denom
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / denom
    return (max(0.0, center - half), min(1.0, center + half))


def two_prop_z(x1: int, n1: int, x2: int, n2: int):
    """两比例双尾 z 检验：返回 (z, p, 差值 p2-p1, 差值 95% CI 下界, 上界)。"""
    if n1 <= 0 or n2 <= 0:
        return (0.0, 1.0, 0.0, 0.0, 0.0)
    p1, p2 = x1 / n1, x2 / n2
    pooled = (x1 + x2) / (n1 + n2)
    se_pooled = math.sqrt(pooled * (1 - pooled) * (1 / n1 + 1 / n2))
    diff = p2 - p1
    se_diff = math.sqrt(p1 * (1 - p1) / n1 + p2 * (1 - p2) / n2)
    if se_pooled <= 0:
        return (0.0, 1.0, diff, diff, diff)
    z = diff / se_pooled
    p_value = math.erfc(abs(z) / math.sqrt(2))
    return (z, p_value, diff, diff - 1.96 * se_diff, diff + 1.96 * se_diff)


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
        extra = parse_extra(extra_raw)
        # A/B 分桶写在 extra.abBucket 里；历史数据没有该字段，归为 unknown
        bucket = extra.get("abBucket") or "unknown"
        s = stats[(scene, bucket)]
        day = event_time.date().isoformat()
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
    for (scene, bucket) in sorted(stats):
        s = stats[(scene, bucket)]
        exp = s["exposures"]
        clicks = s["clicks"]
        result.append(dict(
            scene=scene,
            bucket=bucket,
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

    header = (f"{'场景':<20}{'桶':>6}{'曝光':>8}{'点击':>8}{'CTR':>9}"
              f"{'商品曝光':>10}{'商品CTR':>10}{'曝光设备':>10}{'点击设备':>10}")
    print(header)
    print("-" * len(header))
    for r in result:
        print(f"{r['scene']:<20}{r['bucket']:>6}{r['exposures']:>8}{r['clicks']:>8}{r['ctr']:>9.3f}"
              f"{r['item_impressions']:>10}{r['item_ctr']:>10.3f}"
              f"{r['exposure_devices']:>10}{r['click_devices']:>10}")
        if r["sources"]:
            print("  来源分布:", r["sources"])
        if r["daily"]:
            print("  按天:", r["daily"])

    # A/B 对比：曝光（分流是否均衡）与 CTR（效果差异）分开算，不混在一张表里
    ab_raw = defaultdict(lambda: dict(
        exposures=0, clicks=0, item_impressions=0,
        exposure_devices=set(), exposure_users=set(), click_devices=set()
    ))
    for (scene, bucket), s in stats.items():
        v = ab_raw[bucket]
        v["exposures"] += s["exposures"]
        v["clicks"] += s["clicks"]
        v["item_impressions"] += s["item_impressions"]
        v["exposure_devices"] |= s["exposure_devices"]
        v["exposure_users"] |= s["exposure_users"]
        v["click_devices"] |= s["click_devices"]

    if ab_raw:
        total_exp = sum(v["exposures"] for v in ab_raw.values())
        print("\n[曝光分布] 判断两组拿到的流量是否均衡（理想接近 50/50）")
        header = f"{'桶':<8}{'曝光次数':>10}{'占比':>8}{'曝光设备':>10}{'曝光用户':>10}{'商品曝光':>10}"
        print(header)
        print("-" * len(header))
        for b in sorted(ab_raw):
            v = ab_raw[b]
            share = v["exposures"] / total_exp if total_exp else 0.0
            print(f"{b:<8}{v['exposures']:>10}{share:>8.1%}"
                  f"{len(v['exposure_devices']):>10}{len(v['exposure_users']):>10}{v['item_impressions']:>10}")

        print("\n[CTR 对比] 效果指标；区间为 Wilson 95%（小样本比正态近似稳）")
        header = f"{'桶':<8}{'点击':>8}{'曝光CTR':>10}{'CTR 95% 区间':>22}{'商品CTR':>10}{'点击设备':>10}"
        print(header)
        print("-" * len(header))
        for b in sorted(ab_raw):
            v = ab_raw[b]
            ctr = v["clicks"] / v["exposures"] if v["exposures"] else 0.0
            lo, hi = wilson_interval(v["clicks"], v["exposures"])
            item_ctr = v["clicks"] / v["item_impressions"] if v["item_impressions"] else 0.0
            print(f"{b:<8}{v['clicks']:>8}{ctr:>10.3f}"
                  f"{f'[{lo:.3f}, {hi:.3f}]':>22}{item_ctr:>10.3f}{len(v['click_devices']):>10}")

        a, bb = ab_raw.get("A"), ab_raw.get("B")
        if a and bb and a["exposures"] and bb["exposures"]:
            z, p, diff, lo, hi = two_prop_z(a["clicks"], a["exposures"], bb["clicks"], bb["exposures"])
            enough = min(a["clicks"], bb["clicks"]) >= 10 and min(a["exposures"], bb["exposures"]) >= 100
            verdict = "显著" if (p < 0.05 and enough) else "不显著"
            print(f"\nB - A：曝光CTR {diff:+.4f}，95% CI [{lo:+.4f}, {hi:+.4f}]，z={z:.2f}，p={p:.3f} → {verdict}")
            if not enough:
                print(f"  样本不足：A {a['exposures']} 曝光/{a['clicks']} 点击，"
                      f"B {bb['exposures']} 曝光/{bb['clicks']} 点击（建议每组 ≥100 曝光、≥10 点击）")
        else:
            print("\nA/B 至少一组还没有曝光数据，暂无法对比。")

    payload = dict(
        generated_at=datetime.now().isoformat(timespec="seconds"),
        days=args.days,
        scene=args.scene,
        scenes=SCENES,
        results=result,
        ab={
            b: dict(
                exposures=v["exposures"],
                clicks=v["clicks"],
                ctr=round(v["clicks"] / v["exposures"], 4) if v["exposures"] else 0.0,
                item_impressions=v["item_impressions"],
                exposure_devices=len(v["exposure_devices"]),
                exposure_users=len(v["exposure_users"]),
                click_devices=len(v["click_devices"]),
            )
            for b, v in ab_raw.items()
        },
        note="曝光按推荐位进入视口计一次；点击为推荐卡点击。"
             "桶来自 extra.abBucket（A 对照 / B 实验 / unknown 未参与或历史数据）。"
             "曝光分布看分流是否均衡，CTR 才是效果指标；每组 <100 曝光或 <10 点击时不下结论。",
    )
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    print("saved", os.path.abspath(args.out))


if __name__ == "__main__":
    main()