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
 * 浏览历史列表项 VO（含 SPU 展示快照）。
 * <p>已下架/已删除商品的 spuName / spuImage / minPrice 可能缺失，由前端兜底展示；
 * spuStatus 用于前端提示"该商品已下架"（1 上架 / 0 下架 / null 已删除）。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "浏览历史列表项")
public class BrowseHistoryVO implements Serializable {

    @ApiModelProperty("浏览记录 ID")
    private Long id;

    @ApiModelProperty("SPU ID")
    private Long spuId;

    @ApiModelProperty("SKU ID（可选）")
    private Long skuId;

    @ApiModelProperty("SPU 名称（已删除时为 null）")
    private String spuName;

    @ApiModelProperty("SPU 主图")
    private String spuImage;

    @ApiModelProperty("SPU 最低价格")
    private BigDecimal minPrice;

    @ApiModelProperty("SPU 状态：1 上架 / 0 下架 / null 已删除")
    private Integer spuStatus;

    @ApiModelProperty("最近浏览时间")
    private LocalDateTime browseTime;
}