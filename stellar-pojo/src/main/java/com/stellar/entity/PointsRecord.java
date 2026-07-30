package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 积分流水实体，映射 stellar_points_record 表。
 * 流水只追加，不修改不删除。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointsRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    /** 类型: 1获得 2消费 3过期扣除 4管理员调整 */
    private Integer type;
    /** 积分变动 (正数获得, 负数消费) */
    private Integer points;
    /** 变更后可用余额 */
    private Integer balanceAfter;
    /** 业务类型: ORDER / CHECKIN / REVIEW / REDEEM / EXPIRE / ADMIN */
    private String bizType;
    /** 关联业务ID */
    private String bizId;
    /** 描述 */
    private String description;
    /** 该笔积分到期时间 (获得类型时记录) */
    private LocalDate expiredTime;
    private LocalDateTime createTime;
}
