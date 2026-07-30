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
 * 订单明细子项 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "订单明细项")
public class MallOrderItemVO implements Serializable {

    private Long id;
    private Long spuId;
    private Long skuId;
    private String spuName;
    private String skuSpecs;
    private BigDecimal price;
    private Integer qty;
    private BigDecimal subtotal;
    @ApiModelProperty("额外费用（保障服务等），单位元")
    private BigDecimal extraAmount;
    @ApiModelProperty("商品主图（SPU 主图，前端列表/详情页缩略图用）")
    private String pic;
}
