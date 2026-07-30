package com.stellar.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Data
@ApiModel("更新分类 DTO（动态字段，null 不覆盖）")
public class CategoryUpdateDTO implements Serializable {

    @NotNull(message = "分类 id 不能为空")
    @ApiModelProperty(value = "主键", required = true)
    private Long id;

    @Size(max = 32, message = "分类名最多 32 字符")
    @ApiModelProperty("分类名（同父分类下同 type 唯一）")
    private String name;

    @ApiModelProperty("排序值")
    private Integer sort;

    @ApiModelProperty("1 启用 0 禁用")
    private Integer status;

    @ApiModelProperty("类型：一般不允许修改，仅管理员允许")
    private Integer type;
}
