package com.stellar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 积分规则保存/更新 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointsRuleSaveDTO {

    private Long id;

    @NotBlank(message = "规则类型不能为空")
    private String ruleType;

    @NotBlank(message = "规则名称不能为空")
    private String ruleName;

    @NotNull(message = "获得积分数不能为空")
    private Integer earnPoints;

    private java.math.BigDecimal conditionValue;

    private Integer maxPerDay;

    private Integer maxPerOrder;

    private Integer status;

    private String description;
}
