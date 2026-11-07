package com.stellar.service;

import com.stellar.entity.Review;
import com.stellar.result.PageResult;

import java.math.BigDecimal;

public interface ReviewService {
    Long submit(Review review);
    void reply(Long id, String reply);
    void hide(Long id);
    void show(Long id);
    PageResult pageBySpuId(Long spuId, Integer page, Integer pageSize);
    BigDecimal avgRating(Long spuId);
    /**
     * 管理端评价分页。
     *
     * @param spuId     SPU ID（可选）
     * @param status    显示状态（可选，1 显示 / 0 隐藏）
     * @param spuName   商品名称模糊匹配（可选）
     * @param hasReply  是否已回复（可选，true 已回复 / false 未回复）
     * @param maxRating 评分上限，用于「差评」筛选（可选，如传 2 表示 1-2 星）
     */
    PageResult pageAll(Long spuId, Integer status, String spuName,
                       Boolean hasReply, Integer maxRating,
                       Integer page, Integer pageSize);
}
