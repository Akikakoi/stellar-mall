package com.stellar.service.impl;

import com.stellar.entity.Spu;
import com.stellar.entity.UserBehavior;
import com.stellar.mapper.SpuMapper;
import com.stellar.mapper.UserBehaviorMapper;
import com.stellar.properties.RecommendProperties;
import com.stellar.service.RecommendService;
import com.stellar.vo.RecommendItemVO;
import com.stellar.vo.RecommendResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 轻量混合推荐：
 *   0) 搜索意图：search 事件的关键词与商品名称/副标题做 bigram 覆盖率匹配，
 *      命中商品作为显式意图信号并入内容分，且不计入“已交互”集合；
 *   0.5) 曝光负反馈：view_item_list 里出现过但从未交互的商品适度降权（推荐位曝光权重高于普通列表）；
 *   1) ItemCF：view=1 / cart=3 / favorite=4 / order=5，30 天半衰，item-item 余弦相似度；
 *   2) 内容偏好：用户/设备历史类目分布 + 价格带亲和度；
 *   3) 热度兜底：行为热度 + 销量/评论/新品/热卖标记；
 *   4) 详情页 seed 商品：优先使用 seed 的 item-to-item 相似度，缺失时退回类目/价格兜底；
 *   5) 游客：有 device_id 行为时按设备画像个性化，否则走热度。
 *
 * 模型在内存中惰性构建，刷新间隔与融合权重见 {@link RecommendProperties}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendServiceImpl implements RecommendService {

    private static final String USER_PREFIX = "u:";
    private static final String DEVICE_PREFIX = "d:";
    private static final double ITEM_SCORE_CAP = 15.0;

    /** 搜索意图分（归一化后）达到该值才把来源标注为 search */
    private static final double SEARCH_SOURCE_MIN = 0.30;
    /** 曝光商品列表：兼容推荐位的 itemIds 与首页模块的 spuIds 两种埋点格式 */
    private static final Pattern EXPOSURE_IDS = Pattern.compile("\"(?:itemIds|spuIds)\"\\s*:\\s*\\[([^\\]]*)]");
    /** 单条曝光最多解析的商品数，防止异常 extra 撑爆内存 */
    private static final int EXPOSURE_MAX_ITEMS = 60;
    /** 曝光疲劳的半衰天数：比行为衰减快，用户口味变化更快 */
    private static final double EXPOSURE_HALF_LIFE_DAYS = 14.0;
    /** 由停用字组成的 bigram 不参与匹配（“用的”“给的”这类无信息片段） */
    private static final Set<Character> KEYWORD_STOP_CHARS = Set.of(
            '的', '了', '用', '给', '想', '要', '买', '个', '台', '款', '我', '你', '他', '她',
            '是', '和', '与', '有', '没', '吗', '呢', '啊', '把', '被', '在', '就', '还', '也',
            '都', '这', '那', '哪', '怎', '么', '样', '些', '帮', '找', '下', '一', '能', '可');

    private final UserBehaviorMapper behaviorMapper;
    private final SpuMapper spuMapper;
    private final RecommendProperties props;

    private volatile Model model;
    private volatile long modelAt;
    private final Object modelLock = new Object();

    @Override
    public RecommendResultVO recommend(Long userId, String deviceId, int limit,
                                       Long seedSpuId, BigDecimal maxPrice, boolean includeInteracted) {
        int n = Math.max(1, Math.min(limit, props.getMaxLimit()));
        Model m = getModel();
        long now = System.currentTimeMillis();
        Spu seed = seedSpuId == null ? null : m.spuById.get(seedSpuId);
        String profileKey = resolveProfileKey(m, userId, deviceId);
        Bucket bucket = resolveBucket(userId, deviceId);

        if (profileKey != null) {
            return hybrid(m, profileKey, userId, n, now, seed, maxPrice, includeInteracted,
                    bucket.variant, bucket.name);
        }
        return coldStart(m, userId, deviceId, n, now, seed, maxPrice, bucket.name);
    }

    /** A/B 分桶结果：name 用于埋点（A/B/null），variant 决定本次打分权重 */
    private static final class Bucket {
        final String name;
        final RecommendProperties.Variant variant;

        Bucket(String name, RecommendProperties.Variant variant) {
            this.name = name;
            this.variant = variant;
        }
    }

    /**
     * 按 user_id 稳定分桶，游客退回 device_id。
     * 实验关闭或无法分桶（无用户无设备）时走全局权重，而不是对照组——
     * 否则「关掉实验」会意外把线上切回旧配置。
     */
    private Bucket resolveBucket(Long userId, String deviceId) {
        RecommendProperties.Ab ab = props.getAb();
        if (!ab.isEnabled()) return new Bucket(null, globalVariant());
        String key = userId != null ? "u" + userId
                : (deviceId == null || deviceId.isBlank() ? null : "d" + deviceId);
        if (key == null) return new Bucket(null, globalVariant());
        int pct = bucketPercent(key, ab.getSalt());
        if (pct < ab.getControlPercent()) return new Bucket("A", ab.getControl());
        return new Bucket("B", ab.getExperiment());
    }

    private RecommendProperties.Variant globalVariant() {
        return new RecommendProperties.Variant(
                props.getCfWeight(), props.getContentWeight(), props.getPopularWeight(),
                props.getSearchIntentWeight(), props.getSeedWeight(), props.getCrossCategoryCfWeight());
    }

    /**
     * 0-99 的稳定分桶值，独立出来便于单测验证分布。
     * 用 SHA-256 而不是 String.hashCode：后者对“salt-1/salt-2”这类后缀差异几乎不敏感
     * （实测换盐值只有 6/200 用户换组），盐值就失去了重新洗桶的作用。
     */
    static int bucketPercent(String key, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((salt + "|" + key).getBytes(StandardCharsets.UTF_8));
            int value = ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16)
                    | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
            return Math.floorMod(value, 100);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private String resolveProfileKey(Model m, Long userId, String deviceId) {
        if (userId != null) {
            String key = USER_PREFIX + userId;
            if (hasProfile(m, key)) return key;
        }
        if (deviceId != null && !deviceId.isBlank()) {
            String key = DEVICE_PREFIX + deviceId;
            if (hasProfile(m, key)) return key;
        }
        return null;
    }

    /** 行为画像、搜索意图、曝光疲劳任一非空，就足以走个性化（只逛不点的用户也能避开疲劳商品） */
    private static boolean hasProfile(Model m, String key) {
        Map<Long, Double> scores = m.profileItem.get(key);
        if (scores != null && !scores.isEmpty()) return true;
        Map<Long, Double> intent = m.profileSearch.get(key);
        if (intent != null && !intent.isEmpty()) return true;
        Map<Long, Double> fatigue = m.profileFatigue.get(key);
        return fatigue != null && !fatigue.isEmpty();
    }

    // ===================== 个性化：item-to-item + CF + 内容 + 热度 =====================

    private RecommendResultVO hybrid(Model m, String profileKey, Long userId, int n,
                                     long now, Spu seed, BigDecimal maxPrice, boolean includeInteracted,
                                     RecommendProperties.Variant variant, String bucket) {
        Map<Long, Double> profileItems = m.profileItem.getOrDefault(profileKey, Map.of());
        // 凑单场景允许“看过但没买”的商品重新出现；普通推荐仍严格排除已交互
        Set<Long> interacted = includeInteracted ? Set.of() : profileItems.keySet();
        Long seedId = seed == null ? null : seed.getId();

        Map<Long, Double> cf = new HashMap<>();
        Map<Long, Long> cfSource = new HashMap<>();
        Map<Long, Double> cfSourceWeight = new HashMap<>();
        for (Map.Entry<Long, Double> ue : profileItems.entrySet()) {
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
                Spu sourceSpu = m.spuById.get(sourceId);
                boolean sameCategory = sourceSpu != null && candidateSpu != null
                        && Objects.equals(sourceSpu.getCategoryId(), candidateSpu.getCategoryId());
                double contribution = sourceScore * se.getValue()
                        * (sameCategory ? 1.0 : variant.getCrossCategoryWeight());
                cf.merge(candidate, contribution, Double::sum);
                if (contribution > cfSourceWeight.getOrDefault(candidate, 0.0)) {
                    cfSourceWeight.put(candidate, contribution);
                    cfSource.put(candidate, sourceId);
                }
            }
        }

        // 搜索意图独立成一路，不并入 profileItem：搜过但没点开的商品不应被视为“已交互”
        Map<Long, Double> intent = new HashMap<>(m.profileSearch.getOrDefault(profileKey, Map.of()));
        Map<Long, String> intentKeyword = m.profileSearchKeyword.getOrDefault(profileKey, Map.of());
        normalize(intent);
        // 凑单场景以凑金额为先，不施加曝光疲劳惩罚
        Map<Long, Double> fatigue = includeInteracted
                ? Map.of() : m.profileFatigue.getOrDefault(profileKey, Map.of());

        Map<Long, Double> seedSim = buildSeedSimilarity(m, seed, interacted, maxPrice);
        Map<Long, Double> profileCats = m.profileCategory.getOrDefault(profileKey, Map.of());
        double avgPrice = m.profileAvgPrice.getOrDefault(profileKey, 0.0);
        Map<Long, Double> content = new HashMap<>();
        Map<Long, Double> pop = new HashMap<>();
        for (Spu s : m.activeItems) {
            Long id = s.getId();
            if (interacted.contains(id) || Objects.equals(id, seedId) || priceAbove(s, maxPrice)) continue;
            double catScore = profileCats.getOrDefault(s.getCategoryId(), 0.0);
            double priceAffinity = priceAffinity(s.getMinPrice(), avgPrice);
            content.put(id, 0.8 * catScore + 0.2 * priceAffinity);
            pop.put(id, Math.log1p(m.popularity.getOrDefault(id, 0.0)));
        }
        normalize(cf);
        normalize(seedSim);
        normalize(content);
        normalize(pop);

        Set<Long> candidates = new HashSet<>();
        candidates.addAll(cf.keySet());
        candidates.addAll(seedSim.keySet());
        candidates.addAll(content.keySet());
        // 搜索命中的商品可能是“搜过的词里有、但从未点开”的，仍需遵守已交互排除与在售校验
        for (Long id : intent.keySet()) {
            if (!interacted.contains(id) && m.activeIds.contains(id)) candidates.add(id);
        }
        candidates.addAll(pop.keySet());

        boolean hasSeedSim = !seedSim.isEmpty();
        // 只有候选池里真的存在“未被交互”的意图商品时才启用意图路：
        // 搜索后点进去看过的商品已被排除，此时再分配权重只会压低所有分数而不改变排序
        boolean hasIntent = false;
        for (Long id : intent.keySet()) {
            Spu is = m.spuById.get(id);
            if (is == null || interacted.contains(id) || !m.activeIds.contains(id)) continue;
            if (priceAbove(is, maxPrice)) continue;
            hasIntent = true;
            break;
        }
        double cfW = variant.getCfWeight();
        double contentW = variant.getContentWeight();
        double popW = variant.getPopularWeight();
        double seedW = variant.getSeedWeight();
        // 搜索意图作为独立的第四路参与融合：直接加进 content 再做全局归一化会把类目偏好整体压扁
        double intentW = hasIntent ? variant.getSearchIntentWeight() : 0.0;
        double baseSum = cfW + contentW + popW + intentW;
        double restScale = baseSum > 0 ? Math.max(0.0, 1.0 - seedW) / baseSum : 0.0;
        double plainScale = baseSum > 0 ? 1.0 / baseSum : 0.0;

        List<Map.Entry<Long, Double>> ranked = new ArrayList<>();
        for (Long id : candidates) {
            Spu s = m.spuById.get(id);
            double catPref = s == null ? 0.0 : profileCats.getOrDefault(s.getCategoryId(), 0.0);
            double base = cfW * cf.getOrDefault(id, 0.0)
                    + contentW * content.getOrDefault(id, 0.0)
                    + popW * pop.getOrDefault(id, 0.0)
                    + intentW * intent.getOrDefault(id, 0.0);
            double score;
            if (hasSeedSim) {
                score = seedW * seedSim.getOrDefault(id, 0.0) + restScale * base;
            } else {
                score = plainScale * base;
                if (seed != null) score *= seedMultiplier(seed, s);
            }
            score *= props.getCategoryPriorBase() + props.getCategoryPriorWeight() * catPref;
            if (!fatigue.isEmpty()) {
                double f = fatigue.getOrDefault(id, 0.0);
                if (f > 0) score *= fatigueFactor(f);
            }
            ranked.add(Map.entry(id, score));
        }
        ranked.sort(Map.Entry.<Long, Double>comparingByValue().reversed());

        List<RecommendItemVO> items = pickDiverse(m, ranked, n, cf, content, pop, seedSim, cfSource,
                intent, intentKeyword, seed);
        String note = profileKey.startsWith(DEVICE_PREFIX)
                ? "基于本设备的浏览/加购行为的混合推荐"
                : "基于近期浏览/加购/收藏/下单行为的混合推荐";
        return RecommendResultVO.builder()
                .items(items)
                .strategy(hasSeedSim ? "item" : "hybrid")
                .personalized(true)
                .userId(userId)
                .generatedAt(now)
                .note(note)
                .abBucket(bucket)
                .build();
    }

    // ===================== 冷启动：seed 相似度 / 热度 + 类目多样性 =====================

    private RecommendResultVO coldStart(Model m, Long userId, String deviceId, int n,
                                        long now, Spu seed, BigDecimal maxPrice, String bucket) {
        Long seedId = seed == null ? null : seed.getId();
        Map<Long, Double> seedSim = buildSeedSimilarity(m, seed, Set.of(), maxPrice);
        Map<Long, Double> pop = new HashMap<>();
        List<Map.Entry<Long, Double>> ranked = new ArrayList<>();

        if (!seedSim.isEmpty()) {
            normalize(seedSim);
            for (Spu s : m.activeItems) {
                if (Objects.equals(s.getId(), seedId) || priceAbove(s, maxPrice)) continue;
                pop.put(s.getId(), Math.log1p(m.popularity.getOrDefault(s.getId(), 0.0)));
            }
            normalize(pop);
            for (Spu s : m.activeItems) {
                if (Objects.equals(s.getId(), seedId) || priceAbove(s, maxPrice)) continue;
                double score = 0.70 * seedSim.getOrDefault(s.getId(), 0.0)
                        + 0.30 * pop.getOrDefault(s.getId(), 0.0);
                ranked.add(Map.entry(s.getId(), score));
            }
        } else {
            for (Spu s : m.activeItems) {
                if (Objects.equals(s.getId(), seedId) || priceAbove(s, maxPrice)) continue;
                double score = Math.log1p(m.popularity.getOrDefault(s.getId(), 0.0));
                if (seed != null) score *= seedMultiplier(seed, s);
                pop.put(s.getId(), score);
                ranked.add(Map.entry(s.getId(), score));
            }
        }
        ranked.sort(Map.Entry.<Long, Double>comparingByValue().reversed());

        Map<Long, Double> empty = Map.of();
        Map<Long, Long> emptySource = Map.of();
        List<RecommendItemVO> items = pickDiverse(m, ranked, n, empty, empty, pop, seedSim, emptySource,
                empty, Map.of(), seed);
        String note = userId == null && (deviceId == null || deviceId.isBlank())
                ? "游客模式：按热度推荐" : "暂无行为数据：先按热度推荐";
        return RecommendResultVO.builder()
                .items(items)
                .strategy(seedSim.isEmpty() ? "popular" : "item")
                .personalized(false)
                .userId(userId)
                .generatedAt(now)
                .note(note)
                .abBucket(bucket)
                .build();
    }

    // ===================== 排序、多样性、理由 =====================

    private List<RecommendItemVO> pickDiverse(Model m,
                                             List<Map.Entry<Long, Double>> ranked,
                                             int n,
                                             Map<Long, Double> cf,
                                             Map<Long, Double> content,
                                             Map<Long, Double> pop,
                                             Map<Long, Double> seedSim,
                                             Map<Long, Long> cfSource,
                                             Map<Long, Double> intent,
                                             Map<Long, String> intentKeyword,
                                             Spu seed) {
        List<RecommendItemVO> out = new ArrayList<>(n);
        Set<Long> picked = new HashSet<>();
        Map<Long, Integer> catCount = new HashMap<>();

        for (Map.Entry<Long, Double> e : ranked) {
            if (out.size() >= n) break;
            Spu s = m.spuById.get(e.getKey());
            if (s == null) continue;
            int used = catCount.getOrDefault(s.getCategoryId(), 0);
            if (used >= props.getMaxPerCategory()) continue;
            out.add(toVO(m, s, e.getValue(), cf, content, pop, seedSim, cfSource, intent, intentKeyword, seed));
            picked.add(s.getId());
            catCount.put(s.getCategoryId(), used + 1);
        }
        if (out.size() < n) {
            for (Map.Entry<Long, Double> e : ranked) {
                if (out.size() >= n) break;
                if (picked.contains(e.getKey())) continue;
                Spu s = m.spuById.get(e.getKey());
                if (s == null) continue;
                out.add(toVO(m, s, e.getValue(), cf, content, pop, seedSim, cfSource, intent, intentKeyword, seed));
                picked.add(s.getId());
            }
        }
        return out;
    }

    private RecommendItemVO toVO(Model m, Spu s, double score,
                                 Map<Long, Double> cf,
                                 Map<Long, Double> content,
                                 Map<Long, Double> pop,
                                 Map<Long, Double> seedSim,
                                 Map<Long, Long> cfSource,
                                 Map<Long, Double> intent,
                                 Map<Long, String> intentKeyword,
                                 Spu seed) {
        double seedN = seedSim.getOrDefault(s.getId(), 0.0);
        double cfN = cf.getOrDefault(s.getId(), 0.0);
        double contentN = content.getOrDefault(s.getId(), 0.0);
        double intentN = intent.getOrDefault(s.getId(), 0.0);
        Spu src = cfSource.containsKey(s.getId()) ? m.spuById.get(cfSource.get(s.getId())) : null;
        boolean sameCategory = src != null && Objects.equals(src.getCategoryId(), s.getCategoryId());

        String source;
        String reason;
        if (seedN > 0.05 && seed != null) {
            source = "item";
            reason = "和你在看的「" + seed.getName() + "」相似";
        } else if (intentN >= SEARCH_SOURCE_MIN && intentN >= cfN) {
            source = "search";
            String kw = intentKeyword.getOrDefault(s.getId(), "");
            reason = kw.isEmpty() ? "和你的搜索意图相关" : "因为你搜索过「" + kw + "」";
        } else if (cfN > 0.10 && sameCategory) {
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
        if (m != null && System.currentTimeMillis() - modelAt <= props.getModelTtlMs()) {
            return m;
        }
        synchronized (modelLock) {
            if (model == null || System.currentTimeMillis() - modelAt > props.getModelTtlMs()) {
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
            m.searchText.put(s.getId(), buildSearchText(s));
            if (Integer.valueOf(1).equals(s.getStatus())
                    && s.getMainImage() != null && !s.getMainImage().isEmpty()) {
                m.activeItems.add(s);
                m.activeIds.add(s.getId());
            }
        }

        List<UserBehavior> behaviors = behaviorMapper.listRecentForRecommendation(props.getBehaviorDays());
        int used = 0;
        int searchHits = 0;
        for (UserBehavior b : behaviors) {
            if (b.getEventTime() == null) continue;
            String key = profileKey(b.getUserId(), b.getDeviceId());
            if (key == null) continue;
            if ("search".equals(b.getEventType())) {
                searchHits += applySearchIntent(m, key, b);
                continue;
            }
            if (b.getSpuId() == null) continue;
            Spu s = m.spuById.get(b.getSpuId());
            if (s == null) continue;
            double w = eventWeight(b.getEventType());
            if (w <= 0) continue;
            long days = Math.max(0, ChronoUnit.DAYS.between(b.getEventTime().toLocalDate(), LocalDate.now()));
            double decay = Math.exp(-days / 30.0);
            m.profileItem.computeIfAbsent(key, k -> new HashMap<>())
                    .merge(b.getSpuId(), w * decay, Double::sum);
            used++;
        }
        for (Map<Long, Double> scores : m.profileItem.values()) {
            scores.replaceAll((k, v) -> Math.min(v, ITEM_SCORE_CAP));
        }

        int exposureHits = buildExposureFatigue(m, behaviors);
        buildSimilarities(m);
        buildPopularity(m);
        buildProfiles(m);

        log.info("[recommend] model built: profiles={}, searchProfiles={}, fatigueProfiles={}, "
                        + "activeItems={}, behaviors={}, searchHits={}, exposureHits={}, similarItems={}",
                m.profileItem.size(), m.profileSearch.size(), m.profileFatigue.size(), m.activeItems.size(),
                used, searchHits, exposureHits, m.itemSimilar.size());
        return m;
    }

    /**
     * 曝光未点击负反馈：view_item_list 里出现过、但该画像从未与之交互的商品累计疲劳分。
     * 已交互的商品本来就会被排除，所以用 profileItem 是否含该商品作为“看过且没点”的判据；
     * 推荐位曝光（recommend_*）权重 1.0，首页/类目/搜索列表曝光 0.5——列表位没点开更可能只是没往下滑。
     */
    private int buildExposureFatigue(Model m, List<UserBehavior> behaviors) {
        if (!props.isExposureNegativeEnabled()) return 0;
        int hits = 0;
        for (UserBehavior b : behaviors) {
            if (!"view_item_list".equals(b.getEventType()) || b.getEventTime() == null) continue;
            String key = profileKey(b.getUserId(), b.getDeviceId());
            if (key == null) continue;
            double sceneWeight = exposureSceneWeight(b.getScene());
            if (sceneWeight <= 0) continue;
            List<Long> ids = parseExposureSpuIds(b.getExtra());
            if (ids.isEmpty()) continue;

            Map<Long, Double> acted = m.profileItem.getOrDefault(key, Map.of());
            long days = Math.max(0, ChronoUnit.DAYS.between(b.getEventTime().toLocalDate(), LocalDate.now()));
            double decay = Math.exp(-days / EXPOSURE_HALF_LIFE_DAYS);
            Map<Long, Double> fatigue = m.profileFatigue.computeIfAbsent(key, k -> new HashMap<>());
            for (Long id : ids) {
                if (!m.activeIds.contains(id) || acted.containsKey(id)) continue;
                fatigue.merge(id, sceneWeight * decay, Double::sum);
                hits++;
            }
        }
        return hits;
    }

    /**
     * 把一条 search 行为落成“关键词 → 商品”的意图分（只进 profileSearch，不进 profileItem）。
     * 匹配只针对商品名称与副标题：描述文本里“游戏/电脑/手机”这类泛词太多，会把搜索意图扩散到无关类目。
     * 拼写纠错不在这里做——前端「您是不是要找」确认后会用正确词重新上报 search，正确词本身就在库里。
     * 返回命中商品数，用于构建日志观测搜索信号的实际覆盖。
     */
    private int applySearchIntent(Model m, String key, UserBehavior b) {
        String kw = normalizeText(b.getKeyword());
        if (kw.length() < 2 || kw.length() > 40) return 0;
        String display = b.getKeyword().trim();
        if (display.length() > 20) display = display.substring(0, 20);

        Map<Long, Double> matched = new LinkedHashMap<>();
        for (Map.Entry<Long, String> e : m.searchText.entrySet()) {
            double s = keywordScore(kw, e.getValue());
            if (s >= props.getSearchIntentMinScore()) matched.put(e.getKey(), s);
        }
        if (matched.isEmpty()) return 0;

        long days = Math.max(0, ChronoUnit.DAYS.between(b.getEventTime().toLocalDate(), LocalDate.now()));
        double base = props.getSearchEventWeight() * Math.exp(-days / 30.0);
        Map<Long, Double> scores = m.profileSearch.computeIfAbsent(key, k -> new HashMap<>());
        Map<Long, String> keywords = m.profileSearchKeyword.computeIfAbsent(key, k -> new HashMap<>());
        for (Map.Entry<Long, Double> e : matched.entrySet()) {
            scores.merge(e.getKey(), e.getValue() * base, Double::sum);
            keywords.putIfAbsent(e.getKey(), display);
        }
        return matched.size();
    }

    private static String profileKey(Long userId, String deviceId) {
        if (userId != null) return USER_PREFIX + userId;
        if (deviceId != null && !deviceId.isBlank()) return DEVICE_PREFIX + deviceId;
        return null;
    }

    private void buildSimilarities(Model m) {
        Map<Long, Map<Long, Double>> dot = new HashMap<>();
        Map<Long, Double> norm = new HashMap<>();

        for (Map<Long, Double> scores : m.profileItem.values()) {
            List<Map.Entry<Long, Double>> items = new ArrayList<>(scores.entrySet());
            items.sort(Map.Entry.<Long, Double>comparingByValue().reversed());
            if (items.size() > props.getMaxItemsPerUser()) {
                items = items.subList(0, props.getMaxItemsPerUser());
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
            if (sims.size() > props.getMaxSimilarPerItem()) {
                sims = sims.subList(0, props.getMaxSimilarPerItem());
            }
            Map<Long, Double> similar = new LinkedHashMap<>();
            for (Map.Entry<Long, Double> se : sims) similar.put(se.getKey(), se.getValue());
            if (!similar.isEmpty()) m.itemSimilar.put(a, similar);
        }
    }

    private void buildPopularity(Model m) {
        for (Map<Long, Double> scores : m.profileItem.values()) {
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

    private void buildProfiles(Model m) {
        Set<String> keys = new HashSet<>(m.profileItem.keySet());
        keys.addAll(m.profileSearch.keySet());
        for (String key : keys) {
            Map<Long, Double> cat = new HashMap<>();
            double total = 0.0;
            double weightedPrice = 0.0;
            for (Map.Entry<Long, Double> ie : m.profileItem.getOrDefault(key, Map.of()).entrySet()) {
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
            // 搜索意图折半计入类目/价格画像：一条搜索词不应压过多次真实浏览行为
            for (Map.Entry<Long, Double> ie : m.profileSearch.getOrDefault(key, Map.of()).entrySet()) {
                Spu s = m.spuById.get(ie.getKey());
                if (s == null) continue;
                double score = ie.getValue() * 0.5;
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
                m.profileAvgPrice.put(key, weightedPrice / total);
            }
            m.profileCategory.put(key, cat);
        }
    }

    // ===================== 工具 =====================

    private static Map<Long, Double> buildSeedSimilarity(Model m, Spu seed,
                                                         Set<Long> interacted,
                                                         BigDecimal maxPrice) {
        if (seed == null) return Map.of();
        Map<Long, Double> similar = m.itemSimilar.get(seed.getId());
        if (similar == null || similar.isEmpty()) return Map.of();
        Map<Long, Double> out = new HashMap<>();
        for (Map.Entry<Long, Double> e : similar.entrySet()) {
            Long candidate = e.getKey();
            if (interacted.contains(candidate) || !m.activeIds.contains(candidate)) continue;
            Spu s = m.spuById.get(candidate);
            if (s == null || !Objects.equals(seed.getCategoryId(), s.getCategoryId())) continue;
            if (priceAbove(s, maxPrice)) continue;
            out.put(candidate, e.getValue());
        }
        return out;
    }

    private static boolean priceAbove(Spu spu, BigDecimal maxPrice) {
        return spu != null && maxPrice != null && spu.getMinPrice() != null
                && spu.getMinPrice().compareTo(maxPrice) > 0;
    }

    private double seedMultiplier(Spu seed, Spu candidate) {
        if (seed == null || candidate == null) return 1.0;
        double catBoost = Objects.equals(seed.getCategoryId(), candidate.getCategoryId()) ? 1.0 : 0.0;
        double seedPrice = seed.getMinPrice() == null ? 0.0 : seed.getMinPrice().doubleValue();
        double priceBoost = priceAffinity(candidate.getMinPrice(), seedPrice);
        return 1.0 + props.getSeedCategoryBoost() * catBoost + props.getSeedPriceBoost() * priceBoost;
    }

    // ===================== 曝光负反馈 =====================

    /** 从 extra 里提取曝光商品 ID：兼容 {"itemIds":[...]} 与 {"spuIds":[...]} */
    private static List<Long> parseExposureSpuIds(String extra) {
        if (extra == null || extra.isEmpty()) return List.of();
        Matcher matcher = EXPOSURE_IDS.matcher(extra);
        if (!matcher.find()) return List.of();
        List<Long> ids = new ArrayList<>();
        for (String token : matcher.group(1).split(",")) {
            String s = token.trim();
            if (s.isEmpty()) continue;
            try {
                ids.add(Long.parseLong(s));
            } catch (NumberFormatException e) {
                // extra 被截断时尾部可能是半个数字，保留已解析部分
                return ids;
            }
            if (ids.size() >= EXPOSURE_MAX_ITEMS) break;
        }
        return ids;
    }

    /** 曝光场景强度：推荐位是“给他看但没点”，普通列表更可能是没滑到 */
    private static double exposureSceneWeight(String scene) {
        if (scene == null) return 0.5;
        if (scene.startsWith("recommend")) return 1.0;
        return switch (scene) {
            case "home", "category", "list", "search", "favorites" -> 0.5;
            default -> 0.0;
        };
    }

    private double fatigueFactor(double fatigue) {
        double penalty = Math.min(props.getExposureNegativeMaxPenalty(),
                props.getExposureNegativeWeight() * fatigue);
        return Math.max(0.0, 1.0 - penalty);
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

    // ===================== 搜索词匹配 =====================

    /** 只保留汉字/字母/数字并转小写，抹平空格、引号、标点带来的同词不同串 */
    private static String normalizeText(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = Character.toLowerCase(raw.charAt(i));
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 可检索文本 = 商品名称 + 副标题 + 类目名。
     * 描述文本不参与：里面“游戏/电脑/手机”这类泛词会把关键词意图扩散到无关类目。
     * 类目名参与是为了覆盖品类词（“智能穿戴”“生活家电”），这类词在商品标题里往往不出现。
     */
    private static String buildSearchText(Spu s) {
        String name = s.getName() == null ? "" : s.getName();
        String subTitle = s.getSubTitle() == null ? "" : s.getSubTitle();
        String category = s.getCategoryName() == null ? "" : s.getCategoryName();
        return normalizeText(name + " " + subTitle + " " + category);
    }

    /** 关键词与商品文本的匹配分：整词包含记 1.0，否则按有效 bigram 覆盖率（0~1） */
    private static double keywordScore(String kw, String text) {
        if (text.isEmpty()) return 0.0;
        if (text.contains(kw)) return 1.0;
        List<String> grams = keywordBigrams(kw);
        if (grams.isEmpty()) return 0.0;
        int hit = 0;
        for (String g : grams) {
            if (text.contains(g)) hit++;
        }
        return (double) hit / grams.size();
    }

    /** 中文无空格分词，用 2-gram 近似；含停用字的组合直接丢弃（“用的”“给的”） */
    private static List<String> keywordBigrams(String kw) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i + 1 < kw.length(); i++) {
            char a = kw.charAt(i);
            char b = kw.charAt(i + 1);
            if (KEYWORD_STOP_CHARS.contains(a) || KEYWORD_STOP_CHARS.contains(b)) continue;
            String gram = kw.substring(i, i + 2);
            if (seen.add(gram)) out.add(gram);
        }
        return out;
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
        final Map<String, Map<Long, Double>> profileItem = new HashMap<>();
        /** 搜索意图：画像键 → 商品 → 意图分（与 profileItem 分离，避免被当成已交互） */
        final Map<String, Map<Long, Double>> profileSearch = new HashMap<>();
        /** 搜索意图来源词：画像键 → 商品 → 触发匹配的关键词（用于推荐理由） */
        final Map<String, Map<Long, String>> profileSearchKeyword = new HashMap<>();
        /** 曝光疲劳：画像键 → 商品 → 加权“曝光未点击”次数 */
        final Map<String, Map<Long, Double>> profileFatigue = new HashMap<>();
        /** 商品可检索文本（名称 + 副标题 + 简介前 200 字，已归一化） */
        final Map<Long, String> searchText = new HashMap<>();
        final Map<Long, Map<Long, Double>> itemSimilar = new HashMap<>();
        final Map<Long, Double> popularity = new HashMap<>();
        final Map<String, Map<Long, Double>> profileCategory = new HashMap<>();
        final Map<String, Double> profileAvgPrice = new HashMap<>();
    }
}