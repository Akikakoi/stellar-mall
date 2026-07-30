package com.stellar.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * C 端提交订单 DTO。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "提交订单请求")
public class OrderSubmitDTO implements Serializable {

    @ApiModelProperty(value = "收货地址（文本快照）", required = true, example = "上海市浦东新区XX路XX号")
    private String address;

    @ApiModelProperty("支付方式：1微信 2支付宝 3货到付款")
    private Integer payMethod;

    @ApiModelProperty("备注")
    private String remark;

    @ApiModelProperty("用户优惠券ID，使用优惠券时传入")
    private Long userCouponId;

    @ApiModelProperty("优惠券抵扣金额")
    private java.math.BigDecimal discountAmount;

    @ApiModelProperty("订单商品列表（前端直传模式，不传则从购物车查询）")
    private List<OrderItemDTO> items;

    @ApiModelProperty("是否使用积分抵扣")
    private Boolean usePoints;

    @ApiModelProperty("用户请求的积分抵扣金额（元），后端会校验并计算实际可抵扣积分数")
    private java.math.BigDecimal pointsAmount;

    @ApiModelProperty("是否同时清空购物车中已勾选的商品：true=清空（购物车下单），false=保留（立即购买），默认 true")
    private Boolean clearCart;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @ApiModel(description = "订单商品项")
    public static class OrderItemDTO implements Serializable {
        @ApiModelProperty("SKU ID")
        private Long skuId;

        @ApiModelProperty("数量")
        private Integer quantity;

        @ApiModelProperty("价格（用于校验）")
        private java.math.BigDecimal price;

        @ApiModelProperty("额外费用（保障服务等），单位元")
        private java.math.BigDecimal extraAmount;
    }
}
