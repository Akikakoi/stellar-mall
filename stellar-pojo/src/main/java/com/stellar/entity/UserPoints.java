package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户积分汇总实体，映射 stellar_user_points 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPoints implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    /** 总积分 */
    private Integer totalPoints;
    /** 可用积分 */
    private Integer availablePoints;
    /** 冻结积分 */
    private Integer frozenPoints;
    /** 累计获得 */
    private Integer totalEarned;
    /** 累计消费 */
    private Integer totalSpent;
    /** 乐观锁版本 */
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
