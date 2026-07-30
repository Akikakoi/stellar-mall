package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 积分兑换记录实体，映射 stellar_points_redemption 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointsRedemption implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    private Long productId;
    /** 商品名称快照 */
    private String productName;
    /** 消耗积分数 */
    private Integer pointsCost;
    /** 状态: 1已兑换 2已发放 3已取消 */
    private Integer status;
    /** 发放的用户优惠券ID (COUPON类型) */
    private Long couponId;
    /** 收货地址ID (PHYSICAL类型) */
    private Long addressId;
    /** 备注 */
    private String remark;
    private LocalDateTime createTime;
}
