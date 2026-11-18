package com.stellar.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * C 端推荐商品条目。字段与商品卡片对齐，额外带推荐理由和来源。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendItemVO implements Serializable {

    private Long id;
    private String name;
    private String subTitle;
    private String mainImage;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer saleCount;
    private Integer commentCount;
    private Long categoryId;
    private String categoryName;

    /** 推荐理由，可直接展示在卡片下方 */
    private String reason;

    /** 推荐来源：cf（行为共现）/ content（类目价格偏好）/ popular（热度兜底） */
    private String source;

    /** 最终融合分数，便于调试与离线对比 */
    private Double score;
}
