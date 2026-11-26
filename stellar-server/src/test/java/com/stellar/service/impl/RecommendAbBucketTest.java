package com.stellar.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A/B 分桶函数单测：稳定性与分布。不依赖 Spring / Mockito，避免被 mock 校验干扰。
 */
@DisplayName("推荐 A/B 分桶")
class RecommendAbBucketTest {

    @Test
    @DisplayName("同一 key 每次分桶结果一致")
    void stableForSameKey() {
        for (int i = 1; i <= 200; i++) {
            String key = "u" + i;
            int first = RecommendServiceImpl.bucketPercent(key, "salt-x");
            assertEquals(first, RecommendServiceImpl.bucketPercent(key, "salt-x"));
            assertTrue(first >= 0 && first < 100);
        }
    }

    @Test
    @DisplayName("1000 个用户的分桶分布接近 50/50")
    void balancedAcrossUsers() {
        int control = 0;
        for (int i = 1; i <= 1000; i++) {
            if (RecommendServiceImpl.bucketPercent("u" + i, "salt-x") < 50) control++;
        }
        assertTrue(control > 400 && control < 600, "对照组占比应接近 50%，实际 " + control);
    }

    @Test
    @DisplayName("换盐值会重新洗桶，避免同一批用户长期落在固定组")
    void saltReshuffles() {
        int moved = 0;
        for (int i = 1; i <= 200; i++) {
            boolean a = RecommendServiceImpl.bucketPercent("u" + i, "salt-1") < 50;
            boolean b = RecommendServiceImpl.bucketPercent("u" + i, "salt-2") < 50;
            if (a != b) moved++;
        }
        assertTrue(moved > 50, "换盐值后应有相当比例的用户换组，实际 " + moved);
    }
}
