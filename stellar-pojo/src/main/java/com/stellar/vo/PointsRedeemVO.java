package com.stellar.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 积分兑换结果 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointsRedeemVO {

    /** 兑换记录ID */
    private Long redemptionId;
    /** 消耗积分 */
    private Integer pointsCost;
    /** 剩余可用积分 */
    private Integer remainingPoints;
    /** 发放的优惠券ID (COUPON类型时有值) */
    private Long userCouponId;
}
