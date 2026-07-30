package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单子项（快照化：SPU 名 / SKU 规格 / 单价 都在下单时定格，商品后续改名不影响历史订单）。
 * 表：stellar_mall_order_item。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MallOrderItem implements Serializable {

    private Long id;

    private Long orderId;

    private Long spuId;

    private Long skuId;

    /** SPU 名称快照 */
    private String spuName;

    /** SKU 规格文本快照 */
    private String skuSpecs;

    /** 下单时单价 */
    private BigDecimal price;

    /** 数量 */
    private Integer qty;

    /** 小计 = price * qty */
    private BigDecimal subtotal;

    /** 额外费用（保障服务等），单位元 */
    private BigDecimal extraAmount;
}
