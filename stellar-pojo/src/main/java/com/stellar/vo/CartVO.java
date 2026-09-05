package com.stellar.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 购物车列表项 VO（含 SPU/SKU 展示信息，前端直接展示）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "购物车列表项")
public class CartVO implements Serializable {

    @ApiModelProperty("购物车记录 ID")
    private Long id;

    private Long skuId;
    private Long spuId;

    @ApiModelProperty("数量")
    private Integer qty;

    @ApiModelProperty("是否勾选 1/0")
    private Integer checked;

    @ApiModelProperty("SPU 名称")
    private String spuName;

    @ApiModelProperty("SPU 主图")
    private String spuImage;

    @ApiModelProperty("SKU 名称（如：iPhone 15 Pro Max. 256GB. 深空黑）")
    private String skuName;

    @ApiModelProperty("SKU 规格文本")
    private String skuSpecs;

    @ApiModelProperty("SKU 单价")
    private BigDecimal skuPrice;

    @ApiModelProperty("SKU 图片")
    private String skuImage;

    @ApiModelProperty("保障服务费（单个商品口径，未乘数量）")
    private BigDecimal extraAmount;

    @ApiModelProperty("保障服务信息（JSON 数组）")
    private String serviceInfo;
}
