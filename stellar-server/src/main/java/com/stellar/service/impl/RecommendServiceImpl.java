package com.stellar.service.impl;

import com.stellar.entity.Spu;
import com.stellar.entity.UserBehavior;
import com.stellar.mapper.SpuMapper;
import com.stellar.mapper.UserBehaviorMapper;
import com.stellar.service.RecommendService;
import com.stellar.vo.RecommendItemVO;
import com.stellar.vo.RecommendResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 轻量混合推荐：
 *   1) ItemCF：把用户行为按 view=1 / cart=3 / favorite=4 / order=5 加权，30 天半衰，
 *      计算 item-item 余弦相似度；
 *   2) 内容偏好：用户历史类目分布 + 价格带亲和度；
 *   3) 热度兜底：行为热度 + 销量/评论/新品/热卖标记；
 *   4) 融合排序后做类目多样性截断（每类最多 4 条）。
 *
 * 模型在内存中惰性构建，默认 60 秒刷新一次；当前数据量（千级行为、百级商品）足够。
 * 游客或没有行为的用户直接走热度冷启动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendServiceImpl implements RecommendService {

    private static final int BEHAVIOR_DAYS = 90;
    private static final long MODEL_TTL_MS = 60_000L;
    private static final int MAX_ITEMS_PER_USER = 40;
    private static final int MAX_SIMILAR_PER_ITEM = 60;
    private static final int MAX_PER_CATEGORY = 4;
    private static final int MAX_LIMIT = 30;
    private static final double ITEM_SCORE_CAP = 15.0;

    private static final double W_CF = 0.50;
    private static final double W_CONTENT = 0.40;
    private static final double W_POPULAR = 0.10;

    private final UserBehaviorMapper behaviorMapper;
    private final SpuMapper spuMapper;

    private volatile Model model;
    private volatile long modelAt;
    private final Object modelLock = new Object();

    @Override
    public RecommendResultVO recommend(Long userId, int limit, Long seedSpuId, BigDecimal maxPrice) {
        int n = Math.max(1, Math.min(limit, MAX_LIMIT));
        Model m = getModel();
        long now = System.currentTimeMillis();
        Spu seed = seedSpuId == null ? null : m.spuById.get(seedSpuId);

        Map<Long, Double> userItems = userId == null ? null : m.userItem.get(userId);
        if (userItems != null && !userItems.isEmpty()) {
            return hybrid(m, userId, userItems, n, now, seed, maxPrice);
        }
        return coldStart(m, userId, n, now, seed, maxPrice);
    }

    // ===================== 个性化：CF + 内容 + 热度 =====================

    private RecommendResultVO hybrid(Model m, Long userId, Map<Long, Double> userItems, int n,
                                     long now, Spu seed, BigDecimal maxPrice) {
        Set<Long> interacted = userItems.keySet();
        Long seedId = seed == null ? null : seed.getId();

        Map<Long, Double> cf = new HashMap<>();
        Map<Long, Long> cfSource = new HashMap<>();
        Map<Long, Double> cfSourceWeight = new HashMap<>();
        for (Map.Entry<Long, Double> ue : userItems.entrySet()) {
            Long sourceId = ue.getKey();
            double sourceScore = ue.getValue();
            Map<Long, Double> similar = m.itemSimilar.get(sourceId);
            if (similar == null) continue;
            for (Map.Entry<Long, Double> se : similar.entrySet()) {
                Long candidate = se.getKey();
                if (interacted.contains(candidate) || !m.activeIds.contains(candidate)
                        || Objects.equals(candidate, seedId)) continue;
                Spu candidateSpu = m.spuById.get(candidate);
                if (priceAbove(candidateSpu, maxPrice)) continue;
                // 跨类目共现保留但降权：18 个用户的小样本里随机跨类目噪声容易压过真实偏好
                Spu sourceSpu = m.spuById.get(sourceId);
                boolean sameCategory = sourceSpu != null && candidateSpu != null
                        && Objects.equals(sourceSpu.getCategoryId(), candidateSpu.getCategoryId());
                double contribution = sourceScore * se.getValue() * (sameCategory ? 1.0 : 0.35);
                cf.merge(candidate, contribution, Double::sum);
                if (contribution > cfSourceWeight.getOrDefault(candidate, 0.0)) {
                    cfSourceWeight.put(candidate, contribution);
                    cfSource.put(candidate, sourceId);
                }
            }
        }

        Map<Long, Double> userCats = m.userCategory.getOrDefault(userId, Map.of());
        double avgPrice = m.userAvgPrice.getOrDefault(userId, 0.0);
        Map<Long, Double> content = new HashMap<>();
        Map<Long, Double> pop = new HashMap<>();
        for (Spu s : m.activeItems) {
            Long id = s.getId();
            if (interacted.contains(id) || Objects.equals(id, seedId) || priceAbove(s, maxPrice)) continue;
            double catScore = userCats.getOrDefault(s.getCategoryId(), 0.0);
            double priceAffinity = priceAffinity(s.getMinPrice(), avgPrice);
            content.put(id, 0.8 * catScore + 0.2 * priceAffinity);
            pop.put(id, Math.log1p(m.popularity.getOrDefault(id, 0.0)));
        }
        normalize(cf);
        normalize(content);
        normalize(pop);

        Set<Long> candidates = new HashSet<>();
        candidates.addAll(cf.keySet());
        candidates.addAll(content.keySet());
        candidates.addAll(pop.keySet());

        List<Map.Entry<Long, Double>> ranked = new ArrayList<>();
        for (Long id : candidates) {
            Spu s = m.spuById.get(id);
            double catPref = s == null ? 0.0 : userCats.getOrDefault(s.getCategoryId(), 0.0);
            double score = W_CF * cf.getOrDefault(id, 0.0)
                    + W_CONTENT * content.getOrDefault(id, 0.0)
                    + W_POPULAR * pop.getOrDefault(id, 0.0);
            // 类目先验：用户常看的类目小幅加权，避免跨类目共现把结果带偏
            score *= 0.80 + 0.40 * catPref;
            if (seed != null) score *= seedMultiplier(seed, s);
            ranked.add(Map.entry(id, score));
        }
        ranked.sort(Map.Entry.<Long, Double>comparingByValue().reversed());

        List<RecommendItemVO> items = pickDiverse(m, ranked, n, cf, content, pop, cfSource);
        return RecommendResultVO.builder()
                .items(items)
                .strategy("hybrid")
                .personalized(true)
                .userId(userId)
                .generatedAt(now)
                .note("基于近期浏览/加购/收藏/下单行为的混合推荐")
                .build();
    }

    // ===================== 冷启动：热度 + 类目多样性 =====================

    private RecommendResultVO coldStart(Model m, Long userId, int n, long now, Spu seed, BigDecimal maxPrice) {
        Long seedId = seed == null ? null : seed.getId();
        List<Map.Entry<Long, Double>> ranked = new ArrayList<>();
        for (Spu s : m.activeItems) {
            if (Objects.equals(s.getId(), seedId) || priceAbove(s, maxPrice)) continue;
            double score = Math.log1p(m.popularity.getOrDefault(s.getId(), 0.0));
            if (seed != null) score *= seedMultiplier(seed, s);
            ranked.add(Map.entry(s.getId(), score));
        }
        ranked.sort(Map.Entry.<Long, Double>comparingByValue().reversed());

        Map<Long, Double> empty = Map.of();
        Map<Long, Long> emptySource = Map.of();
        List<RecommendItemVO> items = pickDiverse(m, ranked, n, empty, empty, empty, emptySource);
        for (RecommendItemVO item : items) {
            Spu s = m.spuById.get(item.getId());
            item.setSource("popular");
            item.setReason(s != null && Integer.valueOf(1).equals(s.getIsHot()) ? "近期热卖"
                    : s != null && Integer.valueOf(1).equals(s.getIsNew()) ? "新品上架"
                    : "大家都在看");
        }
        return RecommendResultVO.builder()
                .items(items)
                .strategy("popular")
                .personalized(false)
                .userId(userId)
                .generatedAt(now)
                .note(userId == null ? "游客模式：按热度推荐" : "暂无行为数据：先按热度推荐")
                .build();
    }

    // ===================== 排序、多样性、理由 =====================

    private List<RecommendItemVO> pickDiverse(Model m,
                                             List<Map.Entry<Long, Double>> ranked,
                                             int n,
                                             Map<Long, Double> cf,
                                             Map<Long, Double> content,
                                             Map<Long, Double> pop,
                                             Map<Long, Long> cfSource) {
        List<RecommendItemVO> out = new ArrayList<>(n);
        Set<Long> picked = new HashSet<>();
        Map<Long, Integer> catCount = new HashMap<>();

        for (Map.Entry<Long, Double> e : ranked) {
            if (out.size() >= n) break;
            Spu s = m.spuById.get(e.getKey());
            if (s == null) continue;
            int used = catCount.getOrDefault(s.getCategoryId(), 0);
            if (used >= MAX_PER_CATEGORY) continue;
            out.add(toVO(m, s, e.getValue(), cf, content, pop, cfSource));
            picked.add(s.getId());
            catCount.put(s.getCategoryId(), used + 1);
        }
        // 类目截断后不足 n 条，放宽多样性补齐
        if (out.size() < n) {
            for (Map.Entry<Long, Double> e : ranked) {
                if (out.size() >= n) break;
                if (picked.contains(e.getKey())) continue;
                Spu s = m.spuById.get(e.getKey());
                if (s == null) continue;
                out.add(toVO(m, s, e.getValue(), cf, content, pop, cfSource));
                picked.add(s.getId());
            }
        }
        return out;
    }

    private RecommendItemVO toVO(Model m, Spu s, double score,
                                 Map<Long, Double> cf,
                                 Map<Long, Double> content,
                                 Map<Long, Double> pop,
                                 Map<Long, Long> cfSource) {
        double cfN = cf.getOrDefault(s.getId(), 0.0);
        double contentN = content.getOrDefault(s.getId(), 0.0);
        double popN = pop.getOrDefault(s.getId(), 0.0);
        String source;
        String reason;
        Spu src = cfSource.containsKey(s.getId()) ? m.spuById.get(cfSource.get(s.getId())) : null;
        boolean sameCategory = src != null && Objects.equals(src.getCategoryId(), s.getCategoryId());
        if (cfN > 0.10 && sameCategory) {
            source = "cf";
            reason = "因为你浏览过「" + src.getName() + "」";
        } else if (contentN > 0.10) {
            source = "content";
            reason = "根据你常看的「" + (s.getCategoryName() == null ? "同类商品" : s.getCategoryName()) + "」推荐";
        } else {
            source = "popular";
            reason = Integer.valueOf(1).equals(s.getIsHot()) ? "近期热卖"
                    : Integer.valueOf(1).equals(s.getIsNew()) ? "新品上架" : "大家都在看";
        }
        return RecommendItemVO.builder()
                .id(s.getId())
                .name(s.getName())
                .subTitle(s.getSubTitle())
                .mainImage(s.getMainImage())
                .minPrice(s.getMinPrice())
                .maxPrice(s.getMaxPrice())
                .saleCount(s.getSaleCount())
                .commentCount(s.getCommentCount())
                .categoryId(s.getCategoryId())
                .categoryName(s.getCategoryName())
                .reason(reason)
                .source(source)
                .score(Math.round(score * 10000.0) / 10000.0)
                .build();
    }

    // ===================== 模型构建 =====================

    private Model getModel() {
        Model m = model;
        if (m != null && System.currentTimeMillis() - modelAt <= MODEL_TTL_MS) {
            return m;
        }
        synchronized (modelLock) {
            if (model == null || System.currentTimeMillis() - modelAt > MODEL_TTL_MS) {
                model = buildModel();
                modelAt = System.currentTimeMillis();
            }
            return model;
        }
    }

    private Model buildModel() {
        Model m = new Model();
        for (Spu s : spuMapper.listAll()) {
            if (s == null || s.getId() == null) continue;
            m.spuById.put(s.getId(), s);
            if (Integer.valueOf(1).equals(s.getStatus())
                    && s.getMainImage() != null && !s.getMainImage().isEmpty()) {
                m.activeItems.add(s);
                m.activeIds.add(s.getId());
            }
        }

        List<UserBehavior> behaviors = behaviorMapper.listRecentForRecommendation(BEHAVIOR_DAYS);
        LocalDateTime now = LocalDateTime.now();
        int used = 0;
        for (UserBehavior b : behaviors) {
            if (b.getUserId() == null || b.getSpuId() == null || b.getEventTime() == null) continue;
            Spu s = m.spuById.get(b.getSpuId());
            if (s == null) continue;
            double w = eventWeight(b.getEventType());
            if (w <= 0) continue;
            long days = Math.max(0, ChronoUnit.DAYS.between(b.getEventTime().toLocalDate(), LocalDate.now()));
            double decay = Math.exp(-days / 30.0);
            m.userItem.computeIfAbsent(b.getUserId(), k -> new HashMap<>())
                    .merge(b.getSpuId(), w * decay, Double::sum);
            used++;
        }
        // 单商品对单用户的权重封顶，避免重复曝光压过真实偏好
        for (Map<Long, Double> scores : m.userItem.values()) {
            scores.replaceAll((k, v) -> Math.min(v, ITEM_SCORE_CAP));
        }

        buildSimilarities(m);
        buildPopularity(m);
        buildUserProfiles(m);

        log.info("[recommend] model built: users={}, activeItems={}, behaviors={}, similarItems={}",
                m.userItem.size(), m.activeItems.size(), used, m.itemSimilar.size());
        return m;
    }

    private void buildSimilarities(Model m) {
        Map<Long, Map<Long, Double>> dot = new HashMap<>();
        Map<Long, Double> norm = new HashMap<>();

        for (Map<Long, Double> scores : m.userItem.values()) {
            List<Map.Entry<Long, Double>> items = new ArrayList<>(scores.entrySet());
            items.sort(Map.Entry.<Long, Double>comparingByValue().reversed());
            if (items.size() > MAX_ITEMS_PER_USER) {
                items = items.subList(0, MAX_ITEMS_PER_USER);
            }
            for (Map.Entry<Long, Double> e : items) {
                norm.merge(e.getKey(), e.getValue() * e.getValue(), Double::sum);
            }
            for (int i = 0; i < items.size(); i++) {
                for (int j = i + 1; j < items.size(); j++) {
                    long a = items.get(i).getKey();
                    long b = items.get(j).getKey();
                    double v = items.get(i).getValue() * items.get(j).getValue();
                    dot.computeIfAbsent(a, k -> new HashMap<>()).merge(b, v, Double::sum);
                    dot.computeIfAbsent(b, k -> new HashMap<>()).merge(a, v, Double::sum);
                }
            }
        }

        for (Map.Entry<Long, Map<Long, Double>> e : dot.entrySet()) {
            long a = e.getKey();
            double na = norm.getOrDefault(a, 0.0);
            if (na <= 0) continue;
            List<Map.Entry<Long, Double>> sims = new ArrayList<>();
            for (Map.Entry<Long, Double> se : e.getValue().entrySet()) {
                long b = se.getKey();
                double nb = norm.getOrDefault(b, 0.0);
                if (nb <= 0) continue;
                double sim = se.getValue() / Math.sqrt(na * nb);
                if (sim > 0.01) sims.add(Map.entry(b, sim));
            }
            sims.sort(Map.Entry.<Long, Double>comparingByValue().reversed());
            if (sims.size() > MAX_SIMILAR_PER_ITEM) {
                sims = sims.subList(0, MAX_SIMILAR_PER_ITEM);
            }
            Map<Long, Double> similar = new LinkedHashMap<>();
            for (Map.Entry<Long, Double> se : sims) similar.put(se.getKey(), se.getValue());
            if (!similar.isEmpty()) m.itemSimilar.put(a, similar);
        }
    }

    private void buildPopularity(Model m) {
        for (Map<Long, Double> scores : m.userItem.values()) {
            for (Map.Entry<Long, Double> e : scores.entrySet()) {
                m.popularity.merge(e.getKey(), e.getValue(), Double::sum);
            }
        }
        for (Spu s : m.activeItems) {
            double base = 0.0;
            if (s.getSaleCount() != null && s.getSaleCount() > 0) {
                base += Math.log1p(s.getSaleCount()) * 0.5;
            }
            if (s.getCommentCount() != null && s.getCommentCount() > 0) {
                base += Math.log1p(s.getCommentCount()) * 0.2;
            }
            if (Integer.valueOf(1).equals(s.getIsHot())) base += 0.8;
            if (Integer.valueOf(1).equals(s.getIsNew())) base += 0.4;
            if (base > 0) m.popularity.merge(s.getId(), base, Double::sum);
        }
    }

    private void buildUserProfiles(Model m) {
        for (Map.Entry<Long, Map<Long, Double>> ue : m.userItem.entrySet()) {
            Map<Long, Double> cat = new HashMap<>();
            double total = 0.0;
            double weightedPrice = 0.0;
            for (Map.Entry<Long, Double> ie : ue.getValue().entrySet()) {
                Spu s = m.spuById.get(ie.getKey());
                if (s == null) continue;
                double score = ie.getValue();
                total += score;
                if (s.getCategoryId() != null) {
                    cat.merge(s.getCategoryId(), score, Double::sum);
                }
                if (s.getMinPrice() != null) {
                    weightedPrice += s.getMinPrice().doubleValue() * score;
                }
            }
            if (total > 0) {
                final double catTotal = total;
                cat.replaceAll((k, v) -> v / catTotal);
                m.userAvgPrice.put(ue.getKey(), weightedPrice / total);
            }
            m.userCategory.put(ue.getKey(), cat);
        }
    }

    // ===================== 工具 =====================

    /** 详情页种子商品加权：同类目 + 同价位带越高，越容易被“看了又看”选中。 */
    private static boolean priceAbove(Spu spu, BigDecimal maxPrice) {
        return spu != null && maxPrice != null && spu.getMinPrice() != null
                && spu.getMinPrice().compareTo(maxPrice) > 0;
    }

    private static double seedMultiplier(Spu seed, Spu candidate) {
        if (seed == null || candidate == null) return 1.0;
        double catBoost = Objects.equals(seed.getCategoryId(), candidate.getCategoryId()) ? 1.0 : 0.0;
        double seedPrice = seed.getMinPrice() == null ? 0.0 : seed.getMinPrice().doubleValue();
        double priceBoost = priceAffinity(candidate.getMinPrice(), seedPrice);
        return 1.0 + 0.80 * catBoost + 0.20 * priceBoost;
    }

    private static double eventWeight(String eventType) {
        if (eventType == null) return 0.0;
        return switch (eventType) {
            case "view_item" -> 1.0;
            case "add_to_cart" -> 3.0;
            case "favorite" -> 4.0;
            case "order_placed" -> 5.0;
            default -> 0.0;
        };
    }

    private static double priceAffinity(BigDecimal price, double avgPrice) {
        if (price == null) return 0.5;
        if (avgPrice <= 0) return 0.5;
        double p = price.doubleValue();
        double ratio = Math.abs(p - avgPrice) / Math.max(avgPrice, 100.0);
        return Math.max(0.0, 1.0 - ratio);
    }

    private static void normalize(Map<Long, Double> scores) {
        double max = 0.0;
        for (double v : scores.values()) max = Math.max(max, v);
        if (max <= 0) return;
        final double maxValue = max;
        scores.replaceAll((k, v) -> v / maxValue);
    }

    private static final class Model {
        final Map<Long, Spu> spuById = new HashMap<>();
        final List<Spu> activeItems = new ArrayList<>();
        final Set<Long> activeIds = new HashSet<>();
        final Map<Long, Map<Long, Double>> userItem = new HashMap<>();
        final Map<Long, Map<Long, Double>> itemSimilar = new HashMap<>();
        final Map<Long, Double> popularity = new HashMap<>();
        final Map<Long, Map<Long, Double>> userCategory = new HashMap<>();
        final Map<Long, Double> userAvgPrice = new HashMap<>();
    }
}