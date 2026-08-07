package com.stellar.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@ApiModel("SPU 分页查询 DTO")
public class SpuPageQueryDTO implements Serializable {

    @ApiModelProperty("页码")
    private Integer page;

    @ApiModelProperty("每页条数")
    private Integer pageSize;

    @ApiModelProperty("SPU 名模糊")
    private String name;

    @ApiModelProperty("分类 id")
    private Long categoryId;

    @ApiModelProperty("1 上架 0 下架")
    private Integer status;

    @ApiModelProperty("是否新品")
    private Integer isNew;

    @ApiModelProperty("是否热卖")
    private Integer isHot;

    @ApiModelProperty("最低价过滤（≥）")
    private BigDecimal priceFrom;

    @ApiModelProperty("最高价过滤（≤）")
    private BigDecimal priceTo;

    /**
     * 排序字段白名单（后端 Mapper 会二次校验，避免注入）：
     *   "createTime" → 按创建时间排序
     *   "name"       → 按商品名称排序
     *   null         → 走默认排序（sort DESC, create_time DESC）
     */
    @ApiModelProperty("排序字段：createTime / name；留空走默认排序（sort DESC, create_time DESC）")
    private String sortBy;

    /**
     * 排序方向白名单：
     *   "asc"  → 升序（从小到大）
     *   "desc" → 降序（从大到小）
     *   null   → 默认方向（一般 desc）
     */
    @ApiModelProperty("排序方向：asc / desc；留空走默认方向")
    private String sortOrder;
}
