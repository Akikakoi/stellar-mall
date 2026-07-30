package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 积分规则实体，映射 stellar_points_rule 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointsRule implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 规则类型: ORDER / CHECKIN / REVIEW */
    private String ruleType;
    /** 规则名称 */
    private String ruleName;
    /** 单次获得积分数 */
    private Integer earnPoints;
    /** 条件值 (ORDER类型表示每消费N元得1积分) */
    private java.math.BigDecimal conditionValue;
    /** 每日上限 */
    private Integer maxPerDay;
    /** 每单上限 */
    private Integer maxPerOrder;
    /** 状态: 1启用 0禁用 */
    private Integer status;
    /** 规则描述 */
    private String description;
    private LocalDateTime createTime;
    private Long createUser;
    private LocalDateTime updateTime;
    private Long updateUser;
}
