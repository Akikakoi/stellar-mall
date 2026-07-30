package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 积分商城商品实体，映射 stellar_points_product 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointsProduct implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 商品名称 */
    private String name;
    /** 类型: COUPON / PHYSICAL */
    private String productType;
    /** 所需积分数 */
    private Integer pointsPrice;
    /** 库存 */
    private Integer stock;
    /** 商品图片 */
    private String imageUrl;
    /** 商品描述 */
    private String description;
    /** 关联优惠券ID (COUPON类型) */
    private Long couponId;
    /** 状态: 1上架 0下架 */
    private Integer status;
    /** 排序 */
    private Integer sortOrder;
    private LocalDateTime createTime;
    private Long createUser;
    private LocalDateTime updateTime;
    private Long updateUser;
}
