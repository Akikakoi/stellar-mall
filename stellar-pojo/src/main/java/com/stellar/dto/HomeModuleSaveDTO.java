package com.stellar.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 首页模块保存/更新 DTO
 */
@Data
public class HomeModuleSaveDTO {

    private Long id;

    @NotBlank(message = "模块类型不能为空")
    private String type;

    private String title;

    /** 模块配置 JSON */
    private String config;

    @NotNull(message = "排序值不能为空")
    private Integer sortOrder;

    private Integer status;
}
