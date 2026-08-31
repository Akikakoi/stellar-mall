package com.stellar.mapper;

import com.stellar.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品评价 Mapper，管理用户评价的增删改查、回复、审核及评分统计。
 * 表：stellar_review。
 */
@Mapper
public interface ReviewMapper {
    int insert(Review review);
    Review getById(@Param("id") Long id);
    int updateReply(@Param("id") Long id, @Param("reply") String reply);
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
    List<Review> pageBySpuId(@Param("spuId") Long spuId, @Param("offset") int offset, @Param("pageSize") int pageSize);
    long countBySpuId(@Param("spuId") Long spuId);
    BigDecimal avgRatingBySpuId(@Param("spuId") Long spuId);
    Review getByOrderIdAndUserId(@Param("orderId") Long orderId, @Param("userId") Long userId);
    List<Review> pageAll(@Param("spuId") Long spuId, @Param("status") Integer status,
                         @Param("spuName") String spuName,
                         @Param("offset") int offset, @Param("pageSize") int pageSize);
    long countAll(@Param("spuId") Long spuId, @Param("status") Integer status,
                  @Param("spuName") String spuName);
    long countByUserIdAndSpuId(@Param("userId") Long userId, @Param("spuId") Long spuId);
}