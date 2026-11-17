package com.stellar.service;

import com.stellar.vo.RecommendResultVO;

import java.math.BigDecimal;

/**
 * C 端商品推荐。当前实现为轻量混合推荐：
 * ItemCF 行为共现 + 类目/价格偏好 + 热度兜底，适合本地演示和算法对比。
 */
public interface RecommendService {

    /**
     * @param userId    登录用户 ID；null 表示游客或未登录
     * @param limit     返回条数，内部会限制在 1~30
     * @param seedSpuId 当前商品 ID（详情页“看了又看”用），同类目/同价位加权；可空
     */
    RecommendResultVO recommend(Long userId, int limit, Long seedSpuId, BigDecimal maxPrice);
}
