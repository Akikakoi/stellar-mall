package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后申请表实体，映射 stellar_after_sale 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSale implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;
    /** 订单 ID */
    private Long orderId;
    /** 申请售后的 SKU */
    private Long skuId;
    /** 申请人用户 ID */
    private Long userId;
    /** 售后类型：1 仅退款，2 退货退款，3 换货 */
    private Integer type;
    /** 售后状态：1 申请，2 商家审核中，3 用户退货中，4 退款中，5 完成，6 已拒绝，7 已取消 */
    private Integer status;
    /** 申请原因 */
    private String reason;
    /** 详细描述 */
    private String detail;
    /** 申请退款金额 */
    private BigDecimal amount;
    /** 凭证图片 JSON */
    private String images;
    /** 审核人员工 ID */
    private Long auditUserId;
    /** 审核备注 */
    private String auditRemark;
    /** 审核时间 */
    private LocalDateTime auditTime;
    /** 退货快递单号 */
    private String returnTracking;
    /** 第三方退款流水号 */
    private String refundNo;
    /** 退款完成时间 */
    private LocalDateTime refundTime;
    /** 换货目标 SKU（换货专用） */
    private Long exchangeSkuId;
    /** 创建时间 */
    private LocalDateTime createTime;
    /** 创建人 */
    private Long createUser;
    /** 更新时间 */
    private LocalDateTime updateTime;
    /** 更新人 */
    private Long updateUser;
}
