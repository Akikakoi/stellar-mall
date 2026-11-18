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
     * @param deviceId  匿名设备 ID；登录用户无行为时可用设备历史兜底
     * @param limit     返回条数，内部会限制在 1~30
     * @param seedSpuId 当前商品 ID（详情页“看了又看”用），优先 item-to-item 相似度
     * @param maxPrice  价格上限（购物车凑单用）
     */
    RecommendResultVO recommend(Long userId, String deviceId, int limit, Long seedSpuId, BigDecimal maxPrice, boolean includeInteracted);
}
