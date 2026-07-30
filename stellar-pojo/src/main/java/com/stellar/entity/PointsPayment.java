package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 积分抵扣支付记录（追溯用）。
 * 表: stellar_points_payment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointsPayment implements Serializable {

    private Long id;

    /** 订单ID */
    private Long orderId;

    /** 用户ID */
    private Long userId;

    /** 变动的积分数 */
    private Integer points;

    /** 对应的金额 */
    private BigDecimal amount;

    /** 类型: 1冻结 2实际扣除 3退还 4取消解冻 */
    private Integer type;

    /** 业务描述 */
    private String bizDesc;

    private LocalDateTime createTime;
}
