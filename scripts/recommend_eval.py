"""离线评估：ItemCF / 内容偏好 / 热度 / 混合推荐 / 搜索意图 / 曝光负反馈。

做法：
- 只读 stellar_user_behavior 与 stellar_spu，不写任何业务表；
- 每个用户按时间留出「最后一次交互的商品」作为测试集，训练集删除该商品的所有该用户事件；
- 行为权重 view=1 / cart=3 / favorite=4 / order=5，30 天半衰；
- ItemCF 使用 item-item 余弦相似度，跨类目共现权重 0.35，与线上接口保持一致；
- 搜索意图：search 关键词与「名称 + 副标题 + 类目名」做 bigram 覆盖率匹配，独立第四路，
  权重 search-intent-weight（线上 0.35）；测试集商品会被显式排除，避免信息泄漏；
- 曝光负反馈：view_item_list 的 itemIds/spuIds 里出现但 train 中从未交互的商品累计疲劳，
  每次降权 0.18、封顶 0.45，曝光半衰 14 天，推荐位权重 1.0 / 普通列表 0.5；
- 指标：Recall@K、NDCG@K、覆盖率、新品占比、稀疏类目占比、平均类目数。

用法：
  python scripts/recommend_eval.py
  python scripts/recommend_eval.py --k 5,10,20 --out .workbuddy/tmp/recommend_eval.json
  python scripts/recommend_eval.py --sweep            # 网格搜索 cf/content/pop/intent 权重
"""
from __future__ import annotations

import argparse
import itertools
import json
import math
import os
import re
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

# 与 RecommendProperties / application.yml 保持一致
W_CF, W_CONTENT, W_POPULAR, W_INTENT = 0.30, 0.30, 0.20, 0.10
SEARCH_INTENT_MIN_SCORE = 0.20
SEARCH_EVENT_WEIGHT = 1.50
EXPOSURE_NEG_ENABLED = True
EXPOSURE_NEG_WEIGHT = 0.18
EXPOSURE_NEG_MAX = 0.45
EXPOSURE_HALF_LIFE_DAYS = 14.0
EXPOSURE_MAX_ITEMS = 60

STOP_CHARS = set("的了用给想买个台款我你他她是和与有没吗呢啊把被在就还也都这那哪怎么样些帮找下一能可")
EXPOSURE_IDS = re.compile(r'"(?:itemIds|spuIds)"\s*:\s*\[([^\]]*)\]')


# ===================== 数据加载 =====================

def load_data():
    conn = pymysql.connect(**DB)
    try:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT s.id, s.name, s.sub_title, s.category_id, s.min_price,
                       s.sale_count, s.comment_count, s.is_new, s.is_hot, c.name
                FROM stellar_spu s LEFT JOIN stellar_category c ON c.id = s.category_id
                WHERE s.status = 1
            """)
            items = {}
            for r in cur.fetchall():
                items[int(r[0])] = dict(
                    id=int(r[0]), name=r[1] or "", sub_title=r[2] or "", cat=int(r[3]),
                    price=float(r[4] or 0), sale=int(r[5] or 0), comments=int(r[6] or 0),
                    is_new=int(r[7] or 0), is_hot=int(r[8] or 0), cat_name=r[9] or "")
            cur.execute("""
                SELECT user_id, spu_id, event_type, keyword, scene, extra, event_time
                FROM stellar_user_behavior
                WHERE user_id IS NOT NULL
                  AND event_time >= DATE_SUB(NOW(), INTERVAL %s DAY)
                  AND (
                        (spu_id IS NOT NULL
                         AND event_type IN ('view_item','add_to_cart','favorite','order_placed'))
                     OR (event_type = 'search' AND keyword IS NOT NULL AND keyword <> '')
                     OR (event_type = 'view_item_list' AND extra IS NOT NULL
                         AND (extra LIKE '%%itemIds%%' OR extra LIKE '%%spuIds%%'))
                  )
                ORDER BY user_id, event_time, id
            """, (BEHAVIOR_DAYS,))
            rows = cur.fetchall()
            cur.execute("SELECT id, name FROM stellar_category")
            cats = {int(r[0]): r[1] for r in cur.fetchall()}
    finally:
        conn.close()
    return items, rows, cats


# ===================== 画像与模型 =====================

def build_user_items(rows, items):
    now = datetime.now()
    user_items = defaultdict(lambda: defaultdict(float))
    last_time = defaultdict(dict)
    for uid, spu, etype, keyword, scene, extra, etime in rows:
        if spu is None or int(spu) not in items:
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
    """留一法：最后一个交互商品作测试集，倒数第二个作详情页 seed（seed 场景用）。"""
    train, tests, seeds = {}, {}, {}
    for uid, scores in user_items.items():
        if len(scores) < 3:
            continue
        ordered = sorted(scores, key=lambda spu: (last_time[uid].get(spu), scores[spu], spu),
                         reverse=True)
        test_item = ordered[0]
        train_scores = {spu: s for spu, s in scores.items() if spu != test_item}
        if train_scores:
            train[uid] = train_scores
            tests[uid] = test_item
            seeds[uid] = ordered[1] if len(ordered) > 1 else None
    return train, tests, seeds


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


# ===================== 搜索意图 =====================

def norm_text(raw):
    return "".join(ch.lower() for ch in (raw or "") if ch.isalnum())


def keyword_bigrams(kw):
    out, seen = [], set()
    for i in range(len(kw) - 1):
        if kw[i] in STOP_CHARS or kw[i + 1] in STOP_CHARS:
            continue
        g = kw[i:i + 2]
        if g not in seen:
            seen.add(g)
            out.append(g)
    return out


def keyword_score(kw, text):
    if not text:
        return 0.0
    if kw in text:
        return 1.0
    grams = keyword_bigrams(kw)
    if not grams:
        return 0.0
    return sum(1 for g in grams if g in text) / len(grams)


def build_search_text(items):
    return {spu: norm_text(it["name"] + " " + it["sub_title"] + " " + it["cat_name"])
            for spu, it in items.items()}


def build_intent(rows, items, search_text, exclude):
    """search 关键词 → 商品意图分；exclude 里的商品（测试集）不参与，避免信息泄漏。"""
    out, keywords = defaultdict(dict), defaultdict(dict)
    now = datetime.now()
    for uid, spu, etype, keyword, scene, extra, etime in rows:
        if etype != "search" or not keyword:
            continue
        uid = int(uid)
        kw = norm_text(keyword)
        if len(kw) < 2 or len(kw) > 40:
            continue
        days = max(0.0, (now - etime).total_seconds() / 86400.0)
        base = SEARCH_EVENT_WEIGHT * math.exp(-days / 30.0)
        skip = exclude.get(uid, set())
        for spu_id, text in search_text.items():
            if spu_id in skip:
                continue
            s = keyword_score(kw, text)
            if s >= SEARCH_INTENT_MIN_SCORE:
                out[uid][spu_id] = out[uid].get(spu_id, 0.0) + s * base
                keywords[uid].setdefault(spu_id, keyword.strip()[:20])
    return out, keywords


# ===================== 曝光负反馈 =====================

def parse_exposure_ids(extra):
    if not extra:
        return []
    m = EXPOSURE_IDS.search(extra)
    if not m:
        return []
    ids = []
    for token in m.group(1).split(","):
        token = token.strip()
        if not token:
            continue
        try:
            ids.append(int(token))
        except ValueError:
            return ids
        if len(ids) >= EXPOSURE_MAX_ITEMS:
            break
    return ids


def exposure_scene_weight(scene):
    if scene is None:
        return 0.5
    if scene.startswith("recommend"):
        return 1.0
    return 0.5 if scene in ("home", "category", "list", "search", "favorites") else 0.0


def build_fatigue(rows, items, train, exclude):
    out = defaultdict(dict)
    now = datetime.now()
    for uid, spu, etype, keyword, scene, extra, etime in rows:
        if etype != "view_item_list":
            continue
        uid = int(uid)
        w = exposure_scene_weight(scene)
        if w <= 0:
            continue
        ids = parse_exposure_ids(extra)
        if not ids:
            continue
        acted = set(train.get(uid, {}))
        skip = exclude.get(uid, set())
        days = max(0.0, (now - etime).total_seconds() / 86400.0)
        decay = math.exp(-days / EXPOSURE_HALF_LIFE_DAYS)
        for spu_id in ids:
            if spu_id not in items or spu_id in acted or spu_id in skip:
                continue
            out[uid][spu_id] = out[uid].get(spu_id, 0.0) + w * decay
    return out


# ===================== 打分 =====================

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


def fatigue_factor(fatigue):
    if not EXPOSURE_NEG_ENABLED or fatigue <= 0:
        return 1.0
    return max(0.0, 1.0 - min(EXPOSURE_NEG_MAX, EXPOSURE_NEG_WEIGHT * fatigue))


COMPONENTS = {}


def components(uid, train, items, model, cross_w=0.10):
    """按用户 + 跨类目系数缓存各路分量，网格搜索时只重算加权和。"""
    key = (uid, round(cross_w, 4))
    cached = COMPONENTS.get(key)
    if cached is not None:
        return cached
    seen = set(train.get(uid, {}))
    cf = defaultdict(float)
    for src, s in train.get(uid, {}).items():
        for cand, sim in model["similar"].get(src, []):
            if cand in seen:
                continue
            same_cat = items[src]["cat"] == items[cand]["cat"]
            cf[cand] += s * sim * (1.0 if same_cat else cross_w)
    cats = model["user_cat"].get(uid, {})
    avg = model["user_price"].get(uid, 0.0)
    content, pop = {}, {}
    for spu, it in items.items():
        if spu in seen:
            continue
        content[spu] = 0.8 * cats.get(it["cat"], 0.0) + 0.2 * price_affinity(it["price"], avg)
        pop[spu] = math.log1p(model["popularity"].get(spu, 0.0))

    intent = model["intent"].get(uid, {})
    intent_n = norm_scores({k: v for k, v in intent.items() if k not in seen})
    fatigue = model["fatigue"].get(uid, {})
    out = dict(
        seen=seen,
        cf_n=norm_scores(cf),
        content_n=norm_scores(content),
        pop_n=norm_scores(pop),
        intent_n=intent_n,
        has_intent=bool(intent_n),
        fatigue={k: v for k, v in fatigue.items() if k not in seen},
        cats=cats,
    )
    COMPONENTS[key] = out
    return out


def seed_similarity(seed, items, model, exclude):
    """与线上 buildSeedSimilarity 一致：只用同类目近邻并归一化。"""
    if seed is None or seed not in items:
        return {}
    out = {}
    for cand, sim in model["similar"].get(seed, []):
        if cand in exclude or cand not in items:
            continue
        if items[cand]["cat"] != items[seed]["cat"]:
            continue
        out[cand] = sim
    return norm_scores(out)


def rank_hybrid(c, k, items, weights, params, use_neg=True, use_intent=True,
                seed_sim=None):
    cf_w = weights["cf"]
    content_w = weights["content"]
    pop_w = weights["pop"]
    intent_w = weights["intent"] if (use_intent and c["has_intent"]) else 0.0
    base_sum = cf_w + content_w + pop_w + intent_w
    cf_n, content_n, pop_n, intent_n = c["cf_n"], c["content_n"], c["pop_n"], c["intent_n"]
    candidate_ids = set(cf_n) | set(content_n) | set(pop_n) | set(intent_n)
    if seed_sim:
        candidate_ids |= set(seed_sim)
    ranked = []
    for spu in candidate_ids:
        it = items[spu]
        base = (cf_w * cf_n.get(spu, 0.0) + content_w * content_n.get(spu, 0.0)
                + pop_w * pop_n.get(spu, 0.0) + intent_w * intent_n.get(spu, 0.0))
        if seed_sim:
            seed_w = params["seed_weight"]
            rest = max(0.0, 1.0 - seed_w) / base_sum if base_sum > 0 else 0.0
            score = seed_w * seed_sim.get(spu, 0.0) + rest * base
        else:
            score = base / base_sum if base_sum > 0 else 0.0
        score *= params["cat_base"] + params["cat_weight"] * c["cats"].get(it["cat"], 0.0)
        if use_neg:
            score *= fatigue_factor(c["fatigue"].get(spu, 0.0))
        ranked.append((spu, score))
    ranked.sort(key=lambda x: x[1], reverse=True)
    return [spu for spu, _ in diversify(ranked, items, k)]


DEFAULT_PARAMS = dict(cat_base=0.80, cat_weight=0.40, seed_weight=0.45, cross_w=0.10)


def make_hybrid(weights, params=None, use_neg=True, use_intent=True):
    params = params or DEFAULT_PARAMS

    def fn(uid, k, train, items, model):
        c = components(uid, train, items, model, params["cross_w"])
        return rank_hybrid(c, k, items, weights, params, use_neg, use_intent)
    return fn


# ===================== 基线算法（不含新信号，用于对比） =====================

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


# ===================== 评估 =====================

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


def evaluate_seed(name, train, tests, seeds, items, model, k, weights, params,
                  use_neg=True, use_intent=True):
    """详情页场景：用用户倒数第二个交互商品作 seed，预测最后一个交互。
    seed 相似度只取同类目近邻，与线上 buildSeedSimilarity 一致。"""
    recall, ndcg, recommended = [], [], set()
    for uid, test_item in tests.items():
        seed = seeds.get(uid)
        if seed is None or seed not in items:
            continue
        c = components(uid, train, items, model, params["cross_w"])
        sim = seed_similarity(seed, items, model, {seed})
        recs = rank_hybrid(c, k, items, weights, params, use_neg, use_intent, seed_sim=sim)
        recommended.update(recs)
        if test_item in recs:
            recall.append(1.0)
            ndcg.append(1.0 / math.log2(recs.index(test_item) + 2))
        else:
            recall.append(0.0)
            ndcg.append(0.0)
    return dict(
        strategy=name,
        k=k,
        users=len(recall),
        recall=sum(recall) / max(1, len(recall)),
        ndcg=sum(ndcg) / max(1, len(ndcg)),
        coverage=len(recommended) / max(1, len(items)),
    )


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--k", default="5,10,20")
    ap.add_argument("--out", default="recommend_eval_result.json")
    ap.add_argument("--sweep", action="store_true", help="网格搜索融合权重")
    ap.add_argument("--sweep-k", type=int, default=10, help="网格搜索用的 K")
    ap.add_argument("--sweep-top", type=int, default=10, help="输出前 N 组权重")
    ap.add_argument("--weights", default="", help="自定义权重 cf,content,pop,intent（覆盖线上默认）")
    ap.add_argument("--no-neg", action="store_true", help="关闭曝光负反馈")
    ap.add_argument("--no-intent", action="store_true", help="关闭搜索意图")
    ap.add_argument("--sweep-params", action="store_true",
                    help="扫描 category-prior / cross-category / seed-weight 三个参数")
    ap.add_argument("--cross", type=float, default=None, help="覆盖跨类目共现系数")
    ap.add_argument("--cat", default="", help="覆盖类目先验 base,weight")
    ap.add_argument("--seed-w", type=float, default=None, help="覆盖详情页 seed 权重")
    args = ap.parse_args()
    ks = [int(x) for x in args.k.split(",") if x.strip()]

    weights = dict(cf=W_CF, content=W_CONTENT, pop=W_POPULAR, intent=W_INTENT)
    if args.weights:
        parts = [float(x) for x in args.weights.split(",")]
        if len(parts) != 4:
            raise SystemExit("--weights 需要 4 个值：cf,content,pop,intent")
        weights = dict(zip(("cf", "content", "pop", "intent"), parts))

    base_params = dict(DEFAULT_PARAMS)
    if args.cross is not None:
        base_params["cross_w"] = args.cross
    if args.cat:
        parts = [float(x) for x in args.cat.split(",")]
        if len(parts) != 2:
            raise SystemExit("--cat 需要 2 个值：base,weight")
        base_params["cat_base"], base_params["cat_weight"] = parts
    if args.seed_w is not None:
        base_params["seed_weight"] = args.seed_w

    items, rows, cats = load_data()
    user_items, last_time = build_user_items(rows, items)
    train, tests, seeds = split_last_item(user_items, last_time)
    model = build_model(train, items)
    search_text = build_search_text(items)
    exclude = {uid: {spu} for uid, spu in tests.items()}
    intent, intent_kw = build_intent(rows, items, search_text, exclude)
    model["intent"] = intent
    model["intent_kw"] = intent_kw
    model["fatigue"] = build_fatigue(rows, items, train, exclude)

    print(f"items={len(items)} users={len(user_items)} eval_users={len(tests)} "
          f"train_pairs={sum(len(v) for v in train.values())} "
          f"search_profiles={sum(1 for v in intent.values() if v)} "
          f"fatigue_profiles={sum(1 for v in model['fatigue'].values() if v)}")

    default_weights = weights
    algos = [
        ("popular", rec_popular),
        ("itemcf", rec_itemcf),
        ("content", rec_content),
        ("hybrid", make_hybrid(weights, base_params, use_neg=False, use_intent=False)),
        ("+search", make_hybrid(weights, base_params, use_neg=False, use_intent=not args.no_intent)),
        ("+neg", make_hybrid(weights, base_params, use_neg=not args.no_neg, use_intent=not args.no_intent)),
    ]
    results = []
    for k in ks:
        for name, fn in algos:
            results.append(evaluate(name, fn, train, tests, items, model, k))

    header = (f"{'策略':<10}{'K':>4}{'Recall':>9}{'NDCG':>9}{'覆盖率':>9}"
              f"{'新品占比':>10}{'稀疏类目占比':>14}{'平均类目数':>12}")
    print(header)
    print("-" * len(header))
    for r in results:
        print(f"{r['strategy']:<10}{r['k']:>4}{r['recall']:>9.3f}{r['ndcg']:>9.3f}"
              f"{r['coverage']:>9.3f}{r['new_share']:>10.3f}{r['sparse_share']:>14.3f}"
              f"{r['avg_categories']:>12.2f}")

    sweep_rows = []
    if args.sweep:
        print(f"\n网格搜索（K={args.sweep_k}，按 NDCG 降序，其次 Recall）：")
        for cf_w, content_w, pop_w, intent_w in itertools.product(
                [0.30, 0.40, 0.50, 0.60, 0.70],
                [0.20, 0.30, 0.40, 0.50],
                [0.05, 0.10, 0.15, 0.20],
                [0.00, 0.20, 0.35, 0.50]):
            w = dict(cf=cf_w, content=content_w, pop=pop_w, intent=intent_w)
            r = evaluate("sweep", make_hybrid(w, use_neg=True), train, tests, items, model, args.sweep_k)
            sweep_rows.append(dict(weights=w, recall=r["recall"], ndcg=r["ndcg"],
                                   coverage=r["coverage"], avg_categories=r["avg_categories"]))
        sweep_rows.sort(key=lambda x: (-x["ndcg"], -x["recall"]))
        base = next((x for x in sweep_rows
                     if x["weights"] == weights), None)
        if base:
            rank = sweep_rows.index(base) + 1
            print(f"  当前配置 cf={weights['cf']} content={weights['content']} "
                  f"pop={weights['pop']} intent={weights['intent']} "
                  f"→ NDCG={base['ndcg']:.3f} Recall={base['recall']:.3f}（第 {rank}/{len(sweep_rows)} 名）")
        print(f"{'CF':>6}{'内容':>7}{'热度':>7}{'意图':>7}{'Recall':>9}{'NDCG':>9}{'覆盖率':>9}{'类目数':>8}")
        for x in sweep_rows[:args.sweep_top]:
            w = x["weights"]
            print(f"{w['cf']:>6.2f}{w['content']:>7.2f}{w['pop']:>7.2f}{w['intent']:>7.2f}"
                  f"{x['recall']:>9.3f}{x['ndcg']:>9.3f}{x['coverage']:>9.3f}{x['avg_categories']:>8.2f}")
        print("  提示：评估用户很少，NDCG 差距小于 0.02 视为噪声，优先选靠近当前配置的组合。")

    param_sweep = {}
    if args.sweep_params:
        k = args.sweep_k
        print(f"\n参数扫描（K={k}，基准 cf={weights['cf']} content={weights['content']} "
              f"pop={weights['pop']} intent={weights['intent']}）")

        cat_rows = []
        for base in (0.70, 0.80, 0.90, 1.00):
            for cw in (0.20, 0.40, 0.60):
                p = dict(base_params, cat_base=base, cat_weight=cw)
                r = evaluate("cat", make_hybrid(weights, p), train, tests, items, model, k)
                cat_rows.append(dict(base=base, weight=cw, recall=r["recall"],
                                     ndcg=r["ndcg"], coverage=r["coverage"]))
        cat_rows.sort(key=lambda x: (-x["ndcg"], -x["recall"]))
        print(f"\n  [类目先验] score ×= base + weight × 类目偏好占比（当前 0.80 / 0.40）")
        print(f"{'base':>6}{'weight':>8}{'Recall':>9}{'NDCG':>9}{'覆盖率':>9}")
        for x in cat_rows[:6]:
            print(f"{x['base']:>6.2f}{x['weight']:>8.2f}{x['recall']:>9.3f}"
                  f"{x['ndcg']:>9.3f}{x['coverage']:>9.3f}")
        param_sweep["category_prior"] = cat_rows

        cross_rows = []
        for cw in (0.20, 0.35, 0.50, 0.70):
            p = dict(base_params, cross_w=cw)
            r = evaluate("cross", make_hybrid(weights, p), train, tests, items, model, k)
            cross_rows.append(dict(cross=cw, recall=r["recall"], ndcg=r["ndcg"],
                                   coverage=r["coverage"]))
        cross_rows.sort(key=lambda x: (-x["ndcg"], -x["recall"]))
        print(f"\n  [跨类目共现降权] 非同类目 CF 贡献系数（当前 0.35，越小越保守）")
        print(f"{'系数':>6}{'Recall':>9}{'NDCG':>9}{'覆盖率':>9}")
        for x in cross_rows:
            print(f"{x['cross']:>6.2f}{x['recall']:>9.3f}{x['ndcg']:>9.3f}{x['coverage']:>9.3f}")
        param_sweep["cross_category"] = cross_rows

        seed_rows = []
        for sw in (0.00, 0.20, 0.30, 0.45, 0.60, 0.75):
            p = dict(base_params, seed_weight=sw)
            r = evaluate_seed("seed", train, tests, seeds, items, model, k, weights, p)
            seed_rows.append(dict(seed_weight=sw, recall=r["recall"], ndcg=r["ndcg"],
                                  coverage=r["coverage"]))
        seed_rows.sort(key=lambda x: (-x["ndcg"], -x["recall"]))
        print(f"\n  [详情页 seed 权重] 用倒数第二个交互商品作 seed，预测最后一个（当前 0.45）")
        print(f"{'seed':>6}{'Recall':>9}{'NDCG':>9}{'覆盖率':>9}")
        for x in seed_rows:
            print(f"{x['seed_weight']:>6.2f}{x['recall']:>9.3f}{x['ndcg']:>9.3f}{x['coverage']:>9.3f}")
        param_sweep["seed_weight"] = seed_rows
        print("  提示：seed 场景用户数与其他场景相同，但只衡量“看了又看”这一路的能力。")

    details = {}
    best_fn = make_hybrid(default_weights, base_params, use_neg=True, use_intent=True)
    for uid, test_item in tests.items():
        recs = best_fn(uid, 10, train, items, model)
        details[str(uid)] = dict(
            test_item=test_item,
            test_name=items[test_item]["name"] if test_item in items else "",
            recommended=[dict(id=s, name=items[s]["name"], category=cats.get(items[s]["cat"], ""))
                         for s in recs],
        )

    payload = dict(
        generated_at=datetime.now().isoformat(timespec="seconds"),
        sample_note=f"仅 {len(tests)} 个用户有 >=3 个不同商品行为，指标用于相对对比，不代表线上效果",
        weights=default_weights,
        k_list=ks,
        results=results,
        sweep=sweep_rows[:50],
        param_sweep=param_sweep,
        per_user=details,
    )
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    print("saved", os.path.abspath(args.out))


if __name__ == "__main__":
    main()
