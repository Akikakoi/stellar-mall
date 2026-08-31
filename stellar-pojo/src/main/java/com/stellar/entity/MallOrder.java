package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * C 端订单（主单）。表：stellar_mall_order。
 * status: PENDING / PAID / CANCELLED
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MallOrder implements Serializable {

    private Long id;

    /** 业务订单号（唯一，便于前端展示/对账） */
    private String orderNo;

    private Long userId;

    /** 订单总金额（原价） */
    private BigDecimal totalAmount;

    /** 实付金额（优惠后，简化版 = totalAmount） */
    private BigDecimal payAmount;

    /** PENDING / PAID / CANCELLED */
    private String status;

    /** 是否已退款：1 是 0 否 */
    private Integer isRefunded;

    /** 收货地址（冗余快照文本） */
    private String address;

    /** 收货人姓名（下单时快照，地址簿变动不影响历史订单） */
    private String consignee;

    /** 收货人联系电话（下单时快照） */
    private String phone;

    /** 1 微信 2 支付宝（简化版模拟支付用） */
    private Integer payMethod;

    private String remark;

    /** 快递单号 */
    private String trackingNo;

    /** 快递公司 */
    private String deliveryCompany;

    /** 发货时间 */
    private LocalDateTime deliveryTime;

    /** 积分抵扣：使用的积分数 */
    private Integer pointsDeducted;

    /** 积分抵扣：对应的金额（元），100积分=1元 */
    private BigDecimal pointsAmount;

    private LocalDateTime createTime;
    private Long          createUser;
    private LocalDateTime updateTime;
    private Long          updateUser;

    /** 非 DB 字段：用户手机号（导出时关联查询） */
    private String userPhone;
}
