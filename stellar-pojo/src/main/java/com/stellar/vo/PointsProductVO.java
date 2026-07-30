package com.stellar.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 积分商城商品 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointsProductVO {

    private Long id;
    private String name;
    private String productType;
    /** 类型文本 */
    private String productTypeText;
    private Integer pointsPrice;
    private Integer stock;
    private String imageUrl;
    private String description;
    private Long couponId;
    private Integer status;
    private Integer sortOrder;
    private String createTime;
}
