package com.stellar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

/**
 * 管理员调整积分 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointsAdjustDTO {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotNull(message = "积分数不能为空")
    private Integer points;

    /** 调整说明 */
    private String description;
}
