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
}