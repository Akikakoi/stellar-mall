package com.stellar.mapper;

import com.stellar.entity.PointsRedemption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 积分兑换记录 Mapper。
 */
@Mapper
public interface PointsRedemptionMapper {

    int insert(PointsRedemption redemption);

    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status,
                     @Param("couponId") Long couponId);

    PointsRedemption getById(@Param("id") Long id);

    List<PointsRedemption> listByUser(@Param("userId") Long userId,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    int countByUser(@Param("userId") Long userId);
}
