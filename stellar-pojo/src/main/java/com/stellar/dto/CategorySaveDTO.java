package com.stellar.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Data
@ApiModel("新增分类 DTO")
public class CategorySaveDTO implements Serializable {

    @NotBlank(message = "分类名不能为空")
    @Size(max = 32, message = "分类名最多 32 字符")
    @ApiModelProperty(value = "分类名", required = true)
    private String name;

    @ApiModelProperty("父分类 id，0 或不传表示顶级")
    private Long parentId = 0L;

    @ApiModelProperty("层级：1=顶级 2=二级；不传会根据 parentId 自动推算")
    private Integer level;

    @ApiModelProperty("类型：1 商品分类 2 售后分类，默认 1")
    @NotNull(message = "类型不能为空")
    private Integer type = 1;

    @ApiModelProperty("排序值，越大越靠前，默认 0")
    private Integer sort = 0;

    @ApiModelProperty("1 启用 0 禁用，默认 1")
    private Integer status = 1;
}
