package com.stellar.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("分类 VO（tree 模式下 children 会递归填充）")
public class CategoryVO implements Serializable {

    @ApiModelProperty("主键")
    private Long id;

    @ApiModelProperty("分类名")
    private String name;

    @ApiModelProperty("父分类 id，0=顶级")
    private Long parentId;

    @ApiModelProperty("层级")
    private Integer level;

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

    @ApiModelProperty("子分类（tree 接口返回，列表接口为空）")
    private List<CategoryVO> children;

    @ApiModelProperty("该分类作用域下的关联商品数量；口径：L1 = 自身直接 + L2 下所有；L2 = 作用于 L2 的商品。0 表示无商品。")
    private Integer spuCount;
}
