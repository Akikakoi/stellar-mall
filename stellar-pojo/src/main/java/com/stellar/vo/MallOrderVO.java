package com.stellar.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * C 端订单 VO（含明细列表）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "订单详情（含明细）")
public class MallOrderVO implements Serializable {

    @ApiModelProperty("订单ID")
    private Long id;

    @ApiModelProperty("业务订单号")
    private String orderNo;

    @ApiModelProperty("PENDING / PAID / CANCELLED / COMPLETED")
    private String status;

    @ApiModelProperty("前端兼容用的数字状态：0已取消/1待付款/2待发货/3待收货/4待评价/5已完成/6退款中")
    private Integer statusCode;

    @ApiModelProperty("总金额")
    private BigDecimal totalAmount;

    @ApiModelProperty("实付金额")
    private BigDecimal payAmount;

    @ApiModelProperty("收货地址")
    private String address;

    @ApiModelProperty("支付方式")
    private Integer payMethod;

    @ApiModelProperty("备注")
    private String remark;

    @ApiModelProperty("积分抵扣数量")
    private Integer pointsDeducted;

    @ApiModelProperty("积分抵扣金额（元）")
    private BigDecimal pointsAmount;

    @ApiModelProperty("创建时间")
    private String createTime;

    @ApiModelProperty("订单明细")
    private List<MallOrderItemVO> items;
}
