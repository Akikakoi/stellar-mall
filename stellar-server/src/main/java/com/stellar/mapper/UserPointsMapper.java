package com.stellar.mapper;

import com.stellar.entity.UserPoints;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户积分汇总 Mapper。
 */
@Mapper
public interface UserPointsMapper {

    int insert(UserPoints up);

    UserPoints getByUserId(@Param("userId") Long userId);

    /** 带乐观锁的积分增加 */
    int addPoints(@Param("userId") Long userId,
                  @Param("points") int points,
                  @Param("oldVersion") int oldVersion);

    /** 带乐观锁的积分扣减 */
    int deductPoints(@Param("userId") Long userId,
                     @Param("points") int points,
                     @Param("oldVersion") int oldVersion);

    /**
     * 原子积分增加（RR 下规避"读 version → UPDATE WHERE version 重试"的快照读陷阱）。
     * 加积分无余额条件，rows==0 表示账户不存在。
     */
    int addPointsAtomic(@Param("userId") Long userId, @Param("points") int points);

    /** 原子积分扣减（可用余额条件内置）。rows==0 表示余额不足或账户不存在。 */
    int deductPointsAtomic(@Param("userId") Long userId, @Param("points") int points);

    /** 原子冻结积分（可用余额条件内置）。rows==0 表示可用积分不足。 */
    int freezePointsAtomic(@Param("userId") Long userId, @Param("points") int points);

    /** 原子解冻积分（冻结余额条件内置）。rows==0 表示冻结积分不足。 */
    int unfreezePointsAtomic(@Param("userId") Long userId, @Param("points") int points);

    /** 原子过期扣除（可用余额条件内置）。rows==0 表示可用积分不足。 */
    int expirePointsAtomic(@Param("userId") Long userId, @Param("points") int points);

    /** 原子消费冻结积分（frozen → 实际扣除，冻结余额条件内置）。rows==0 表示冻结积分不足。 */
    int consumeFrozenPointsAtomic(@Param("userId") Long userId, @Param("points") int points);

    /** 冻结积分 */
    int freezePoints(@Param("userId") Long userId,
                     @Param("points") int points,
                     @Param("oldVersion") int oldVersion);

    /** 解冻积分 */
    int unfreezePoints(@Param("userId") Long userId,
                       @Param("points") int points,
                       @Param("oldVersion") int oldVersion);

    /** 增加累计获得 */
    int addTotalEarned(@Param("userId") Long userId, @Param("points") int points);

    /** 增加累计消费 */
    int addTotalSpent(@Param("userId") Long userId, @Param("points") int points);

    /** 积分过期扣除 */
    int expirePoints(@Param("userId") Long userId,
                     @Param("points") int points,
                     @Param("oldVersion") int oldVersion);

    /** 消费冻结积分（frozen → 实际扣除）：一次原子操作完成解冻+扣减 */
    int consumeFrozenPoints(@Param("userId") Long userId,
                            @Param("points") int points,
                            @Param("oldVersion") int oldVersion);
}
