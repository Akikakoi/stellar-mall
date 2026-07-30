package com.stellar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * 积分兑换请求 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointsRedeemDTO {

    @NotNull(message = "兑换商品ID不能为空")
    private Long productId;

    /** 实物兑换时需要收货地址 */
    private Long addressId;
}
