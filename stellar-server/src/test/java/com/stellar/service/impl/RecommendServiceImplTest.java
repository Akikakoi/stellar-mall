package com.stellar.service.impl;

import com.stellar.entity.Spu;
import com.stellar.entity.UserBehavior;
import com.stellar.mapper.SpuMapper;
import com.stellar.mapper.UserBehaviorMapper;
import com.stellar.properties.RecommendProperties;
import com.stellar.vo.RecommendItemVO;
import com.stellar.vo.RecommendResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * 推荐服务单测：冷启动、混合推荐、device 兜底、seed item-to-item、价格过滤与 limit 截断。
 * 数据规模很小，重点验证行为分支和过滤语义，不校验具体排序分数。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("混合推荐 RecommendService")
class RecommendServiceImplTest {

    @Mock private UserBehaviorMapper behaviorMapper;
    @Mock private SpuMapper spuMapper;

    private RecommendServiceImpl service;
    private List<Spu> spus;

    @BeforeEach
    void setUp() {
        RecommendProperties props = new RecommendProperties();
        // 既有用例固定走对照组权重：避免断言依赖 userId 的哈希分桶结果
        props.getAb().setEnabled(false);
        service = new RecommendServiceImpl(behaviorMapper, spuMapper, props);
        spus = List.of(
                spu(1, 1, 100, "商品A", 1, 0),
                spu(2, 1, 120, "商品B", 0, 1),
                spu(3, 1, 150, "商品C", 0, 0),
                spu(4, 2, 200, "商品D", 1, 0),
                spu(5, 2, 250, "商品E", 0, 0),
                spu(6, 3, 50, "商品F", 0, 1)
        );
        when(spuMapper.listAll()).thenReturn(spus);
    }

    private static Spu spu(long id, long cat, double price, String name, int isNew, int isHot) {
        return Spu.builder()
                .id(id).name(name).categoryId(cat).categoryName("分类" + cat)
                .minPrice(BigDecimal.valueOf(price)).maxPrice(BigDecimal.valueOf(price))
                .mainImage("img").status(1).saleCount(10).commentCount(1)
                .isNew(isNew).isHot(isHot).build();
    }

    private static UserBehavior bh(Long userId, String deviceId, long spuId, String type, int daysAgo) {
        return UserBehavior.builder()
                .userId(userId).deviceId(deviceId == null ? "" : deviceId)
                .spuId(spuId).eventType(type)
                .eventTime(LocalDateTime.now().minusDays(daysAgo))
                .build();
    }

    private static Set<Long> ids(RecommendResultVO res) {
        return res.getItems().stream().map(RecommendItemVO::getId).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("无行为：热度冷启动，personalized=false")
    void coldStartWithoutBehavior() {
        when(behaviorMapper.listRecentForRecommendation(anyInt())).thenReturn(List.of());

        RecommendResultVO res = service.recommend(null, null, 10, null, null, false);

        assertFalse(res.getPersonalized());
        assertEquals("popular", res.getStrategy());
        assertFalse(res.getItems().isEmpty());
        assertTrue(res.getNote().contains("游客"));
    }

    @Test
    @DisplayName("登录用户有行为：走混合推荐，排除已交互商品")
    void hybridExcludesInteracted() {
        when(behaviorMapper.listRecentForRecommendation(anyInt())).thenReturn(List.of(
                bh(1L, null, 1, "view_item", 1),
                bh(1L, null, 2, "add_to_cart", 2),
                bh(1L, null, 4, "view_item", 3)
        ));

        RecommendResultVO res = service.recommend(1L, null, 10, null, null, false);

        assertTrue(res.getPersonalized());
        assertEquals("hybrid", res.getStrategy());
        Set<Long> got = ids(res);
        assertFalse(got.contains(1L));
        assertFalse(got.contains(2L));
        assertFalse(got.contains(4L));
        assertFalse(res.getItems().isEmpty());
    }

    @Test
    @DisplayName("游客 device_id 有行为：按设备画像个性化")
    void anonymousDeviceUsesHistory() {
        when(behaviorMapper.listRecentForRecommendation(anyInt())).thenReturn(List.of(
                bh(null, "dev-1", 1, "view_item", 1),
                bh(null, "dev-1", 2, "favorite", 2),
                bh(null, "dev-1", 4, "view_item", 3)
        ));

        RecommendResultVO res = service.recommend(null, "dev-1", 10, null, null, false);

        assertTrue(res.getPersonalized());
        assertEquals("hybrid", res.getStrategy());
        assertTrue(res.getNote().contains("设备"));
        Set<Long> got = ids(res);
        assertFalse(got.contains(1L));
        assertFalse(got.contains(2L));
        assertFalse(got.contains(4L));
    }

    @Test
    @DisplayName("maxPrice 过滤：只返回不高于上限的商品")
    void maxPriceFilters() {
        when(behaviorMapper.listRecentForRecommendation(anyInt())).thenReturn(List.of(
                bh(1L, null, 1, "view_item", 1)
        ));

        RecommendResultVO res = service.recommend(1L, null, 10, null, new BigDecimal("100"), false);

        assertFalse(res.getItems().isEmpty());
        for (RecommendItemVO item : res.getItems()) {
            assertTrue(item.getMinPrice().compareTo(new BigDecimal("100")) <= 0,
                    item.getName() + " 超过价格上限");
        }
    }

    @Test
    @DisplayName("seed 商品有 item-to-item 相似度：使用 item 来源与相似理由")
    void seedSimilarityUsed() {
        when(behaviorMapper.listRecentForRecommendation(anyInt())).thenReturn(List.of(
                bh(1L, null, 1, "view_item", 1),
                bh(1L, null, 3, "view_item", 2),
                bh(2L, null, 1, "view_item", 1),
                bh(2L, null, 2, "view_item", 2),
                bh(2L, null, 3, "view_item", 2)
        ));

        RecommendResultVO res = service.recommend(1L, null, 10, 3L, null, false);

        assertEquals("item", res.getStrategy());
        assertTrue(res.getItems().stream().anyMatch(i -> "item".equals(i.getSource())
                && i.getReason().contains("和你在看的")));
        assertFalse(ids(res).contains(3L), "seed 商品本身不应出现在结果里");
    }

    @Test
    @DisplayName("limit 截断：0 至少 1 条，超过上限按 maxLimit 处理")
    void limitClamped() {
        when(behaviorMapper.listRecentForRecommendation(anyInt())).thenReturn(List.of());

        RecommendResultVO zero = service.recommend(null, null, 0, null, null, false);
        assertFalse(zero.getItems().isEmpty());

        RecommendResultVO huge = service.recommend(null, null, 100, null, null, false);
        assertTrue(huge.getItems().size() <= spus.size());
        assertTrue(huge.getItems().size() <= new RecommendProperties().getMaxLimit());
    }

    @Test
    @DisplayName("includeInteracted=true 允许已浏览商品重新出现（凑单场景）")
    void includeInteractedAllowsSeenItems() {
        when(behaviorMapper.listRecentForRecommendation(anyInt())).thenReturn(List.of(
                bh(1L, null, 1, "view_item", 1),
                bh(1L, null, 2, "view_item", 2)
        ));

        RecommendResultVO strict = service.recommend(1L, null, 10, null, null, false);
        RecommendResultVO relaxed = service.recommend(1L, null, 10, null, null, true);

        assertFalse(ids(strict).contains(1L));
        assertTrue(ids(relaxed).contains(1L) || ids(relaxed).contains(2L));
    }

    @Test
    @DisplayName("配置生效：maxPerCategory=1 时每个类目最多 1 条")
    void configurableMaxPerCategory() {
        RecommendProperties custom = new RecommendProperties();
        custom.setMaxPerCategory(1);
        custom.getAb().setEnabled(false);
        RecommendServiceImpl customService = new RecommendServiceImpl(behaviorMapper, spuMapper, custom);
        when(behaviorMapper.listRecentForRecommendation(anyInt())).thenReturn(List.of());

        RecommendResultVO res = customService.recommend(null, null, 3, null, null, false);

        assertEquals(3, res.getItems().size(), "3 个类目各 1 条");
        long distinctCategories = res.getItems().stream()
                .map(RecommendItemVO::getCategoryId).distinct().count();
        assertEquals(res.getItems().size(), distinctCategories);
    }

    @Test
    @DisplayName("已下架商品不进入推荐")
    void offShelfExcluded() {
        List<Spu> withOffShelf = new ArrayList<>(spus);
        withOffShelf.add(Spu.builder().id(99L).name("下架商品").categoryId(1L)
                .categoryName("分类1").minPrice(BigDecimal.TEN).maxPrice(BigDecimal.TEN)
                .mainImage("img").status(0).saleCount(999).commentCount(999).build());
        when(spuMapper.listAll()).thenReturn(withOffShelf);
        when(behaviorMapper.listRecentForRecommendation(anyInt())).thenReturn(List.of());

        RecommendResultVO res = service.recommend(null, null, 10, null, null, false);

        assertFalse(ids(res).contains(99L));
    }

    private static UserBehavior search(Long userId, String deviceId, String keyword, int daysAgo) {
        return UserBehavior.builder()
                .userId(userId).deviceId(deviceId == null ? "" : deviceId)
                .eventType("search").keyword(keyword)
                .eventTime(LocalDateTime.now().minusDays(daysAgo))
                .build();
    }

    @Test
    @DisplayName("搜索词意图：只有搜索行为的用户也能个性化，命中商品标为 search 并带关键词理由")
    void searchIntentDrivesRecommendation() {
        when(spuMapper.listAll()).thenReturn(List.of(
                spu(11, 1, 3000, "星耀游戏本 15.6 英寸", 0, 0),
                spu(12, 1, 3200, "星耀轻薄本 14 英寸", 0, 0),
                spu(13, 2, 200, "逸彩降噪耳机", 0, 0),
                spu(14, 2, 260, "逸彩蓝牙耳机", 0, 0)
        ));
        when(behaviorMapper.listRecentForRecommendation(anyInt())).thenReturn(List.of(
                search(1L, null, "打游戏用的电脑", 1)
        ));

        RecommendResultVO res = service.recommend(1L, null, 1, null, null, false);

        assertTrue(res.getPersonalized(), "只有搜索行为也应走个性化");
        assertTrue(ids(res).contains(11L), "命中「游戏」的游戏本应被推荐");
        RecommendItemVO hit = res.getItems().stream()
                .filter(i -> Long.valueOf(11L).equals(i.getId())).findFirst().orElseThrow();
        assertEquals("search", hit.getSource());
        assertTrue(hit.getReason().contains("打游戏用的电脑"));
    }

    @Test
    @DisplayName("搜索词命中：品类词能把冷门商品顶到更热门商品之前")
    void searchKeywordMatchesSpuName() {
        when(spuMapper.listAll()).thenReturn(List.of(
                // 冰箱：搜索命中，销量略低
                Spu.builder().id(21L).name("星耀 520L 冰箱").categoryId(3L).categoryName("家用电冰箱")
                        .minPrice(BigDecimal.valueOf(2999)).maxPrice(BigDecimal.valueOf(2999))
                        .mainImage("img").status(1).saleCount(10).commentCount(0)
                        .isNew(0).isHot(0).build(),
                // 洗衣机：搜索不命中，销量稍高
                Spu.builder().id(22L).name("星耀洗衣机").categoryId(3L).categoryName("生活电器")
                        .minPrice(BigDecimal.valueOf(1999)).maxPrice(BigDecimal.valueOf(1999))
                        .mainImage("img").status(1).saleCount(20).commentCount(0)
                        .isNew(0).isHot(0).build()
        ));
        when(behaviorMapper.listRecentForRecommendation(anyInt())).thenReturn(List.of(
                search(2L, null, "冰箱", 1)
        ));

        RecommendResultVO res = service.recommend(2L, null, 1, null, null, false);

        assertEquals(1, res.getItems().size());
        assertEquals(21L, res.getItems().get(0).getId(), "命中搜索词的冰箱应排第一");
        assertEquals("search", res.getItems().get(0).getSource());
    }

    @Test
    @DisplayName("搜索命中的下架商品不进入推荐")
    void searchHitExcludesOffShelf() {
        when(spuMapper.listAll()).thenReturn(List.of(
                Spu.builder().id(31L).name("星耀洗衣机").categoryId(3L).categoryName("生活电器")
                        .minPrice(BigDecimal.valueOf(1999)).maxPrice(BigDecimal.valueOf(1999))
                        .mainImage("img").status(1).saleCount(999).commentCount(80)
                        .isNew(0).isHot(1).build(),
                Spu.builder().id(32L).name("星耀 610L 冰箱").categoryId(4L).categoryName("家用电冰箱")
                        .minPrice(BigDecimal.valueOf(4999)).maxPrice(BigDecimal.valueOf(4999))
                        .mainImage("img").status(0).saleCount(5).commentCount(2).build()
        ));
        when(behaviorMapper.listRecentForRecommendation(anyInt())).thenReturn(List.of(
                search(3L, null, "冰箱", 1)
        ));

        RecommendResultVO res = service.recommend(3L, null, 2, null, null, false);

        assertFalse(ids(res).contains(32L), "下架商品不应因搜索命中被推荐");
        assertTrue(ids(res).contains(31L), "在售商品应正常返回");
    }

    @Test
    @DisplayName("搜索词与商品名无关：不产生意图分，行为画像为空时回落冷启动")
    void unmatchedSearchFallsBackToColdStart() {
        when(spuMapper.listAll()).thenReturn(List.of(
                spu(21, 3, 2999, "星耀 520L 冰箱", 0, 0)
        ));
        when(behaviorMapper.listRecentForRecommendation(anyInt())).thenReturn(List.of(
                search(2L, null, "完全无关的词", 1)
        ));

        RecommendResultVO res = service.recommend(2L, null, 1, null, null, false);

        assertFalse(res.getPersonalized());
        assertEquals("popular", res.getStrategy());
    }

    @Test
    @DisplayName("搜索命中的商品若已被浏览，仍按已交互排除")
    void searchIntentRespectsInteracted() {
        when(spuMapper.listAll()).thenReturn(List.of(
                spu(11, 1, 3000, "星耀游戏本 15.6 英寸", 0, 0),
                spu(12, 1, 3200, "星耀轻薄本 14 英寸", 0, 0)
        ));
        when(behaviorMapper.listRecentForRecommendation(anyInt())).thenReturn(List.of(
                search(1L, null, "游戏本", 1),
                bh(1L, null, 11, "view_item", 1)
        ));

        RecommendResultVO res = service.recommend(1L, null, 2, null, null, false);

        assertFalse(ids(res).contains(11L), "已浏览商品不应因搜索命中重新出现");
    }

    @Test
    @DisplayName("A/B 分桶：同一用户多次请求落同一桶并随结果返回，无身份时不进实验")
    void abBucketExposedAndStable() {
        RecommendProperties props = new RecommendProperties();
        props.getAb().setEnabled(true);
        RecommendServiceImpl svc = new RecommendServiceImpl(behaviorMapper, spuMapper, props);
        when(behaviorMapper.listRecentForRecommendation(anyInt())).thenReturn(List.of(
                bh(7L, null, 1, "view_item", 1)));

        String first = svc.recommend(7L, null, 5, null, null, false).getAbBucket();
        String second = svc.recommend(7L, null, 5, null, null, false).getAbBucket();

        assertNotNull(first);
        assertTrue("A".equals(first) || "B".equals(first));
        assertEquals(first, second, "同一用户应稳定落在同一桶");
        assertNull(svc.recommend(null, null, 5, null, null, false).getAbBucket(),
                "无用户无设备的请求不参与实验");
    }

    private static UserBehavior exposure(Long userId, String deviceId, String scene, String ids, int daysAgo) {
        return UserBehavior.builder()
                .userId(userId).deviceId(deviceId == null ? "" : deviceId)
                .eventType("view_item_list").scene(scene)
                .extra("{\"itemIds\":[" + ids + "]}")
                .eventTime(LocalDateTime.now().minusDays(daysAgo))
                .build();
    }

    private static Spu hotSpu(long id, long cat) {
        return Spu.builder().id(id).name("热销商品" + id).categoryId(cat).categoryName("分类" + cat)
                .minPrice(BigDecimal.valueOf(199)).maxPrice(BigDecimal.valueOf(199))
                .mainImage("img").status(1).saleCount(999).commentCount(80)
                .isNew(0).isHot(1).build();
    }

    private static Spu plainSpu(long id, long cat) {
        return Spu.builder().id(id).name("普通商品" + id).categoryId(cat).categoryName("分类" + cat)
                .minPrice(BigDecimal.valueOf(199)).maxPrice(BigDecimal.valueOf(199))
                .mainImage("img").status(1).saleCount(0).commentCount(0)
                .isNew(0).isHot(0).build();
    }

    @Test
    @DisplayName("曝光未点击：推荐位反复曝光但从未点击的商品让位给未曝光商品")
    void exposureFatigueDemotesIgnoredItem() {
        when(spuMapper.listAll()).thenReturn(List.of(hotSpu(41, 1), plainSpu(42, 1)));
        when(behaviorMapper.listRecentForRecommendation(anyInt())).thenReturn(List.of(
                exposure(1L, null, "recommend_home", "41", 1),
                exposure(1L, null, "recommend_home", "41", 1),
                exposure(1L, null, "recommend_home", "41", 1),
                exposure(1L, null, "recommend_home", "41", 1)
        ));

        RecommendResultVO res = service.recommend(1L, null, 1, null, null, false);

        assertTrue(res.getPersonalized(), "只有曝光行为也应走个性化");
        assertEquals(42L, res.getItems().get(0).getId(), "反复曝光未点击的商品应被降权");
    }

    @Test
    @DisplayName("凑单场景 includeInteracted=true 时不施加曝光惩罚")
    void exposureFatigueSkippedForCartScene() {
        when(spuMapper.listAll()).thenReturn(List.of(hotSpu(41, 1), plainSpu(42, 1)));
        when(behaviorMapper.listRecentForRecommendation(anyInt())).thenReturn(List.of(
                exposure(1L, null, "recommend_cart", "41", 1),
                exposure(1L, null, "recommend_cart", "41", 1),
                bh(1L, null, 41, "view_item", 1)
        ));

        RecommendResultVO res = service.recommend(1L, null, 1, null, null, true);

        assertEquals(41L, res.getItems().get(0).getId(), "凑单场景不应对已交互商品施加曝光疲劳");
    }

    @Test
    @DisplayName("曝光 extra 不含商品 ID 时忽略，不产生疲劳画像")
    void unparsableExposureIgnored() {
        when(spuMapper.listAll()).thenReturn(List.of(hotSpu(41, 1), plainSpu(42, 1)));
        when(behaviorMapper.listRecentForRecommendation(anyInt())).thenReturn(List.of(
                UserBehavior.builder().userId(1L).deviceId("")
                        .eventType("view_item_list").scene("home")
                        .extra("{\"sortBy\":\"default\",\"page\":1}")
                        .eventTime(LocalDateTime.now().minusDays(1)).build()
        ));

        RecommendResultVO res = service.recommend(1L, null, 1, null, null, false);

        assertFalse(res.getPersonalized(), "无商品 ID 的曝光不构成画像");
        assertEquals("popular", res.getStrategy());
        assertEquals(41L, res.getItems().get(0).getId(), "热销商品仍按热度排第一");
    }
}