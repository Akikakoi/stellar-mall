package com.stellar.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
@ApiModel("分类分页查询 DTO")
public class CategoryPageQueryDTO implements Serializable {

    @ApiModelProperty("页码")
    private Integer page;

    @ApiModelProperty("每页条数")
    private Integer pageSize;

    @ApiModelProperty("分类名模糊")
    private String name;

    @ApiModelProperty("类型 1/2")
    private Integer type;

    @ApiModelProperty("1 启用 0 禁用")
    private Integer status;

    /**
     * 排序字段白名单（Mapper XML 二次校验，避免注入）：
     *   "createTime" → 按创建时间排序
     *   "name"       → 按分类名称排序
     *   null         → 走默认排序（sort DESC, create_time DESC）
     */
    @ApiModelProperty("排序字段：createTime / name；留空走默认排序（sort DESC, create_time DESC）")
    private String sortBy;

    /** 排序方向白名单："asc" / "desc"；null 走默认方向。 */
    @ApiModelProperty("排序方向：asc / desc；留空走默认方向")
    private String sortOrder;
}
