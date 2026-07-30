package com.stellar.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

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
}
