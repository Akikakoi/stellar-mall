package com.stellar.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 积分流水 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointsRecordVO {

    private Long id;
    /** 类型: 1获得 2消费 3过期 4管理员调整 */
    private Integer type;
    /** 类型文本 */
    private String typeText;
    /** 积分变动 */
    private Integer points;
    /** 变动后余额 */
    private Integer balanceAfter;
    /** 业务类型 */
    private String bizType;
    /** 描述 */
    private String description;
    /** 到期时间 */
    private String expiredTime;
    /** 创建时间 */
    private String createTime;
}
