"""离线评估：ItemCF / 内容偏好 / 热度 / 混合推荐。

做法：
- 只读 stellar_user_behavior 与 stellar_spu，不写任何业务表；
- 每个用户按时间留出「最后一次交互的商品」作为测试集，训练集删除该商品的所有该用户事件；
- 行为权重 view=1 / cart=3 / favorite=4 / order=5，30 天半衰；
- ItemCF 使用 item-item 余弦相似度，跨类目共现权重 0.35，与线上接口保持一致；
- 指标：Recall@K、NDCG@K、覆盖率、新品占比、稀疏类目占比、平均类目数。

用法：
  python scripts/recommend_eval.py
  python scripts/recommend_eval.py --k 5,10,20 --out .workbuddy/tmp/recommend_eval.json
"""
from __future__ import annotations

import argparse
import json
import math
import os
import sys
from collections import defaultdict
from datetime import datetime

import pymysql

try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass

DB = dict(host="127.0.0.1", port=3306, user="stellar", password="123456",
          database="stellar_mall", charset="utf8mb4")

EVENT_WEIGHT = {"view_item": 1.0, "add_to_cart": 3.0, "favorite": 4.0, "order_placed": 5.0}
BEHAVIOR_DAYS = 90
MAX_ITEMS_PER_USER = 40
MAX_SIMILAR_PER_ITEM = 60
MAX_PER_CATEGORY = 4
ITEM_SCORE_CAP = 15.0
SPARSE_CATS = {285, 286, 10, 284, 8}
NEW_IDS = set(range(181, 214))
W_CF, W_CONTENT, W_POPULAR = 0.50, 0.40, 0.10


def load_data():
    conn = pymysql.connect(**DB)
    try:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT id, name, category_id, min_price, sale_count, comment_count, is_new, is_hot
                FROM stellar_spu WHERE status = 1
            """)
            items = {}
            for r in cur.fetchall():
                items[int(r[0])] = dict(id=int(r[0]), name=r[1], cat=int(r[2]),
                                        price=float(r[3] or 0), sale=int(r[4] or 0),
                                        comments=int(r[5] or 0), is_new=int(r[6] or 0),
                                        is_hot=int(r[7] or 0))
            cur.execute("""
                SELECT user_id, spu_id, event_type, event_time
                FROM stellar_user_behavior
                WHERE user_id IS NOT NULL AND spu_id IS NOT NULL
                  AND event_type IN ('view_item','add_to_cart','favorite','order_placed')
                  AND event_time >= DATE_SUB(NOW(), INTERVAL %s DAY)
                ORDER BY user_id, event_time, id
            """, (BEHAVIOR_DAYS,))
            behaviors = cur.fetchall()
            cur.execute("SELECT id, name FROM stellar_category")
            cats = {int(r[0]): r[1] for r in cur.fetchall()}
    finally:
        conn.close()
    return items, behaviors, cats


def build_user_items(behaviors, items):
    now = datetime.now()
    user_items = defaultdict(lambda: defaultdict(float))
    last_time = defaultdict(dict)
    for uid, spu, etype, etime in behaviors:
        if int(spu) not in items:
            continue
        w = EVENT_WEIGHT.get(etype, 0)
        if w <= 0:
            continue
        uid, spu = int(uid), int(spu)
        days = max(0.0, (now - etime).total_seconds() / 86400.0)
        user_items[uid][spu] += w * math.exp(-days / 30.0)
        prev = last_time[uid].get(spu)
        if prev is None or etime > prev:
            last_time[uid][spu] = etime
    for scores in user_items.values():
        for spu in list(scores):
            scores[spu] = min(scores[spu], ITEM_SCORE_CAP)
    return user_items, last_time


def split_last_item(user_items, last_time):
    train, tests = {}, {}
    for uid, scores in user_items.items():
        if len(scores) < 3:
            continue
        # 按时间留出最后一次交互的商品；时间并列时用评分、id 兜底保证可复现
        test_item = max(scores, key=lambda spu: (last_time[uid].get(spu), scores[spu], spu))
        train_scores = {spu: s for spu, s in scores.items() if spu != test_item}
        if train_scores:
            train[uid] = train_scores
            tests[uid] = test_item
    return train, tests


def build_model(train, items):
    norm = defaultdict(float)
    dot = defaultdict(lambda: defaultdict(float))
    for scores in train.values():
        pairs = sorted(scores.items(), key=lambda kv: kv[1], reverse=True)[:MAX_ITEMS_PER_USER]
        for spu, s in pairs:
            norm[spu] += s * s
        for i in range(len(pairs)):
            for j in range(i + 1, len(pairs)):
                a, sa = pairs[i]
                b, sb = pairs[j]
                dot[a][b] += sa * sb
                dot[b][a] += sa * sb

    similar = {}
    for a, targets in dot.items():
        na = norm.get(a, 0.0)
        if na <= 0:
            continue
        sims = []
        for b, d in targets.items():
            nb = norm.get(b, 0.0)
            if nb <= 0:
                continue
            sim = d / math.sqrt(na * nb)
            if sim > 0.01:
                sims.append((b, sim))
        sims.sort(key=lambda x: x[1], reverse=True)
        similar[a] = sims[:MAX_SIMILAR_PER_ITEM]

    popularity = defaultdict(float)
    for scores in train.values():
        for spu, s in scores.items():
            popularity[spu] += s
    for spu, it in items.items():
        base = 0.0
        if it["sale"] > 0:
            base += math.log1p(it["sale"]) * 0.5
        if it["comments"] > 0:
            base += math.log1p(it["comments"]) * 0.2
        base += 0.8 if it["is_hot"] else 0.0
        base += 0.4 if it["is_new"] else 0.0
        if base > 0:
            popularity[spu] += base

    user_cat, user_price = {}, {}
    for uid, scores in train.items():
        cat = defaultdict(float)
        total = 0.0
        weighted_price = 0.0
        for spu, s in scores.items():
            it = items[spu]
            total += s
            cat[it["cat"]] += s
            weighted_price += it["price"] * s
        if total > 0:
            for k in cat:
                cat[k] /= total
            user_price[uid] = weighted_price / total
        user_cat[uid] = cat

    return dict(similar=similar, popularity=popularity, user_cat=user_cat, user_price=user_price)


def norm_scores(scores):
    if not scores:
        return {}
    mx = max(scores.values())
    if mx <= 0:
        return scores
    return {k: v / mx for k, v in scores.items()}


def price_affinity(price, avg):
    if avg <= 0:
        return 0.5
    return max(0.0, 1.0 - abs(price - avg) / max(avg, 100.0))


def diversify(ranked, items, k):
    out, cat_count = [], defaultdict(int)
    for spu, score in ranked:
        if len(out) >= k:
            break
        cat = items[spu]["cat"]
        if cat_count[cat] >= MAX_PER_CATEGORY:
            continue
        out.append((spu, score))
        cat_count[cat] += 1
    if len(out) < k:
        picked = {x[0] for x in out}
        for spu, score in ranked:
            if len(out) >= k:
                break
            if spu not in picked:
                out.append((spu, score))
                picked.add(spu)
    return out


def rec_popular(uid, k, train, items, model):
    seen = set(train.get(uid, {}))
    ranked = [(spu, math.log1p(pop)) for spu, pop in model["popularity"].items() if spu not in seen]
    ranked.sort(key=lambda x: x[1], reverse=True)
    return [spu for spu, _ in ranked[:k]]


def rec_content(uid, k, train, items, model):
    seen = set(train.get(uid, {}))
    cats = model["user_cat"].get(uid, {})
    avg = model["user_price"].get(uid, 0.0)
    ranked = []
    for spu, it in items.items():
        if spu in seen:
            continue
        score = 0.8 * cats.get(it["cat"], 0.0) + 0.2 * price_affinity(it["price"], avg)
        ranked.append((spu, score))
    ranked.sort(key=lambda x: x[1], reverse=True)
    return [spu for spu, _ in ranked[:k]]


def rec_itemcf(uid, k, train, items, model):
    seen = set(train.get(uid, {}))
    cf = defaultdict(float)
    for src, s in train.get(uid, {}).items():
        for cand, sim in model["similar"].get(src, []):
            if cand in seen:
                continue
            same_cat = items[src]["cat"] == items[cand]["cat"]
            cf[cand] += s * sim * (1.0 if same_cat else 0.35)
    if not cf:
        return rec_popular(uid, k, train, items, model)
    ranked = sorted(cf.items(), key=lambda x: x[1], reverse=True)
    return [spu for spu, _ in ranked[:k]]


def rec_hybrid(uid, k, train, items, model):
    seen = set(train.get(uid, {}))
    cf, content, pop = defaultdict(float), {}, {}
    for src, s in train.get(uid, {}).items():
        for cand, sim in model["similar"].get(src, []):
            if cand in seen:
                continue
            same_cat = items[src]["cat"] == items[cand]["cat"]
            cf[cand] += s * sim * (1.0 if same_cat else 0.35)
    cats = model["user_cat"].get(uid, {})
    avg = model["user_price"].get(uid, 0.0)
    for spu, it in items.items():
        if spu in seen:
            continue
        content[spu] = 0.8 * cats.get(it["cat"], 0.0) + 0.2 * price_affinity(it["price"], avg)
        pop[spu] = math.log1p(model["popularity"].get(spu, 0.0))
    cf_n, content_n, pop_n = norm_scores(cf), norm_scores(content), norm_scores(pop)
    ranked = []
    for spu in set(cf_n) | set(content_n) | set(pop_n):
        cat_pref = cats.get(items[spu]["cat"], 0.0)
        score = (W_CF * cf_n.get(spu, 0.0) + W_CONTENT * content_n.get(spu, 0.0)
                 + W_POPULAR * pop_n.get(spu, 0.0)) * (0.8 + 0.4 * cat_pref)
        ranked.append((spu, score))
    ranked.sort(key=lambda x: x[1], reverse=True)
    ranked = diversify(ranked, items, k)
    return [spu for spu, _ in ranked]


def evaluate(name, fn, train, tests, items, model, k):
    recall, ndcg = [], []
    recommended, cats_per_user = set(), []
    new_cnt = sparse_cnt = 0
    for uid, test_item in tests.items():
        recs = fn(uid, k, train, items, model)
        recommended.update(recs)
        cats_per_user.append(len({items[s]["cat"] for s in recs}))
        new_cnt += sum(1 for s in recs if s in NEW_IDS)
        sparse_cnt += sum(1 for s in recs if items[s]["cat"] in SPARSE_CATS)
        if test_item in recs:
            recall.append(1.0)
            ndcg.append(1.0 / math.log2(recs.index(test_item) + 2))
        else:
            recall.append(0.0)
            ndcg.append(0.0)
    total_recs = max(1, len(tests) * k)
    return dict(
        strategy=name,
        k=k,
        users=len(tests),
        recall=sum(recall) / max(1, len(recall)),
        ndcg=sum(ndcg) / max(1, len(ndcg)),
        coverage=len(recommended) / max(1, len(items)),
        new_share=new_cnt / total_recs,
        sparse_share=sparse_cnt / total_recs,
        avg_categories=sum(cats_per_user) / max(1, len(cats_per_user)),
    )


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--k", default="5,10,20")
    ap.add_argument("--out", default="recommend_eval_result.json")
    args = ap.parse_args()
    ks = [int(x) for x in args.k.split(",") if x.strip()]

    items, behaviors, cats = load_data()
    user_items, last_time = build_user_items(behaviors, items)
    train, tests = split_last_item(user_items, last_time)
    model = build_model(train, items)
    print(f"items={len(items)} users={len(user_items)} eval_users={len(tests)} "
          f"train_pairs={sum(len(v) for v in train.values())}")

    algos = [
        ("popular", rec_popular),
        ("itemcf", rec_itemcf),
        ("content", rec_content),
        ("hybrid", rec_hybrid),
    ]
    results = []
    for k in ks:
        for name, fn in algos:
            results.append(evaluate(name, fn, train, tests, items, model, k))

    header = f"{'策略':<10}{'K':>4}{'Recall':>9}{'NDCG':>9}{'覆盖率':>9}{'新品占比':>10}{'稀疏类目占比':>14}{'平均类目数':>12}"
    print(header)
    print("-" * len(header))
    for r in results:
        print(f"{r['strategy']:<10}{r['k']:>4}{r['recall']:>9.3f}{r['ndcg']:>9.3f}"
              f"{r['coverage']:>9.3f}{r['new_share']:>10.3f}{r['sparse_share']:>14.3f}"
              f"{r['avg_categories']:>12.2f}")

    details = {}
    for uid, test_item in tests.items():
        recs = rec_hybrid(uid, 10, train, items, model)
        details[str(uid)] = dict(
            test_item=test_item,
            test_name=items[test_item]["name"] if test_item in items else "",
            recommended=[dict(id=s, name=items[s]["name"], category=cats.get(items[s]["cat"], ""))
                         for s in recs],
        )

    payload = dict(
        generated_at=datetime.now().isoformat(timespec="seconds"),
        sample_note=f"仅 {len(tests)} 个用户有 >=3 个不同商品行为，指标用于相对对比，不代表线上效果",
        k_list=ks,
        results=results,
        per_user=details,
    )
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    print("saved", os.path.abspath(args.out))


if __name__ == "__main__":
    main()