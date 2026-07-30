package com.stellar.mapper;

import com.stellar.entity.PointsRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 积分流水 Mapper (只追加)。
 */
@Mapper
public interface PointsRecordMapper {

    int insert(PointsRecord record);

    List<PointsRecord> listByUser(@Param("userId") Long userId,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    int countByUser(@Param("userId") Long userId);

    /** 查询某用户当日某业务类型的流水数量 (用于每日上限检查) */
    int countTodayByBizType(@Param("userId") Long userId,
                            @Param("bizType") String bizType,
                            @Param("today") String today);

    /** 查询即将过期的积分流水 (获得类型，且有过期时间) */
    List<PointsRecord> findExpiringPoints(@Param("now") java.time.LocalDate now,
                                          @Param("deadline") java.time.LocalDate deadline);

    /** 查询指定用户+业务类型+业务ID的获得类流水（用于退款时收回奖励积分） */
    List<PointsRecord> findByBiz(@Param("userId") Long userId,
                                  @Param("bizType") String bizType,
                                  @Param("bizId") String bizId);
}
