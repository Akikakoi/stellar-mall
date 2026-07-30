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
