package com.stellar.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理端删除分类「预校验」返回体。
 * 在管理员进入删除确认框之前调用，明确告知是否允许删除 + 关联商品/子分类数量 + 禁止原因，
 * 避免先点「确定删除」才被后端驳回的糟糕体验。
 *
 * Service 层的 deleteById 仍会做最终一致的拦截（防止直接调 API 绕过前端检查）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("分类删除前预校验结果")
public class CategoryDeletableVO implements Serializable {

    @ApiModelProperty("是否允许删除：true=可以删，false=禁止删除")
    private Boolean deletable;

    @ApiModelProperty("作用域（当前分类 + 子分类）下关联的 SPU 数量")
    private Integer linkedSpuCount;

    @ApiModelProperty("作用域内的子分类数量（仅删除 L1 时可能 > 0）")
    private Integer childCount;

    @ApiModelProperty("禁止删除时的原因文案；可删除时通常为 null/空串")
    private String reason;
}
