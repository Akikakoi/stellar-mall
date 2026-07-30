package com.stellar.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 更新购物车数量 / 是否勾选 DTO。
 */
@Data
@ApiModel(description = "更新购物车请求")
public class CartUpdateDTO implements Serializable {

    @ApiModelProperty(value = "购物车记录 ID", required = true)
    private Long id;

    @ApiModelProperty("新的数量（不传则不改）")
    private Integer qty;

    @ApiModelProperty("是否勾选：1 勾选 0 不勾选（不传则不改）")
    private Integer checked;
}
