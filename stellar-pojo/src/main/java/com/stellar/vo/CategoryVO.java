package com.stellar.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("分类 VO")
public class CategoryVO implements Serializable {

    @ApiModelProperty("主键")
    private Long id;

    @ApiModelProperty("分类名")
    private String name;

    @ApiModelProperty("1 商品 2 售后")
    private Integer type;

    @ApiModelProperty("排序值")
    private Integer sort;

    @ApiModelProperty("1 启用 0 禁用")
    private Integer status;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty("最后更新时间")
    private LocalDateTime updateTime;

    @ApiModelProperty("该分类下的关联商品数，0 表示无商品。")
    private Integer spuCount;

    @ApiModelProperty("子分类列表（树形结构）")
    private java.util.List<CategoryVO> children;
}
