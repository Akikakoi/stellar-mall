package com.stellar.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户积分汇总 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPointsVO {

    private Long id;
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

    /** 积分汇率：每元需要的积分数（100 积分 = 1 元） */
    private Integer exchangeRate;
}
