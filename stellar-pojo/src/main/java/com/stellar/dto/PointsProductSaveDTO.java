package com.stellar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 积分商城商品保存/更新 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointsProductSaveDTO {

    private Long id;

    @NotBlank(message = "商品名称不能为空")
    private String name;

    @NotBlank(message = "商品类型不能为空")
    private String productType;

    @NotNull(message = "所需积分不能为空")
    private Integer pointsPrice;

    private Integer stock;

    private String imageUrl;

    private String description;

    private Long couponId;

    private Integer status;

    private Integer sortOrder;
}
