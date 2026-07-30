package com.stellar.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 售后单视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleVO {

    /** 主键 */
    private Long id;
    /** 订单 ID */
    private Long orderId;
    /** 订单编号 */
    private String orderNo;
    /** 申请售后的 SKU ID */
    private Long skuId;
    /** SKU 规格 */
    private String skuSpecs;
    /** SPU ID */
    private Long spuId;
    /** SPU 名称 */
    private String spuName;
    /** SPU 主图 */
    private String spuImage;
    /** 购买数量 */
    private Integer qty;
    /** 申请人用户 ID */
    private Long userId;
    /** 售后类型：1 仅退款，2 退货退款，3 换货 */
    private Integer type;
    /** 售后类型文案 */
    private String typeText;
    /** 售后状态：1-7 */
    private Integer status;
    /** 售后状态文案 */
    private String statusText;
    /** 申请原因 */
    private String reason;
    /** 详细描述 */
    private String detail;
    /** 申请退款金额 */
    private BigDecimal amount;
    /** 凭证图片 JSON */
    private String images;
    /** 审核备注 */
    private String auditRemark;
    /** 审核时间 */
    private String auditTime;
    /** 退货快递单号 */
    private String returnTracking;
    /** 退款完成时间 */
    private String refundTime;
    /** 创建时间 */
    private String createTime;
}
