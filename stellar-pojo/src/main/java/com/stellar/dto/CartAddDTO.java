package com.stellar.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 添加购物车请求 DTO。
 */
@Data
@ApiModel(description = "添加购物车请求")
public class CartAddDTO implements Serializable {

    @ApiModelProperty(value = "SKU ID", required = true)
    private Long skuId;

    @ApiModelProperty("数量，默认 1")
    private Integer qty;

    @ApiModelProperty("保障服务费（单个商品口径，未乘数量）")
    private BigDecimal extraAmount;

    @ApiModelProperty("保障服务信息（JSON 数组），如 [{\"id\":\"screen_insurance\",\"title\":\"碎屏险 · 1年\",\"price\":99}]")
    private String serviceInfo;
}
