package com.stellar.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收藏夹列表项 VO（含 SPU 展示信息）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "收藏夹列表项")
public class FavoriteVO implements Serializable {

    @ApiModelProperty("收藏记录 ID")
    private Long id;

    @ApiModelProperty("SPU ID")
    private Long spuId;

    @ApiModelProperty("SPU 名称")
    private String spuName;

    @ApiModelProperty("SPU 主图")
    private String spuImage;

    @ApiModelProperty("SPU 最低价格")
    private BigDecimal minPrice;

    @ApiModelProperty("收藏时间")
    private LocalDateTime createTime;
}