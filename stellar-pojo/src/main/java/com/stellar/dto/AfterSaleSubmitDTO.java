package com.stellar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 售后申请提交 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleSubmitDTO {

    /** 订单 ID */
    private Long orderId;
    /** 申请售后的 SKU ID */
    private Long skuId;
    /** 售后类型：1 仅退款，2 退货退款，3 换货 */
    private Integer type;
    /** 申请原因 */
    private String reason;
    /** 详细描述 */
    private String detail;
    /** 退款金额 */
    private BigDecimal amount;
    /** 凭证图片 JSON */
    private String images;
}
