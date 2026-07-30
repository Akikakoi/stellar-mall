package com.stellar.mapper;

import com.stellar.annotation.AutoFill;
import com.stellar.entity.Favorite;
import com.stellar.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 收藏夹 Mapper。表：stellar_favorite。
 */
@Mapper
public interface FavoriteMapper {

    @AutoFill(OperationType.INSERT)
    int insert(Favorite favorite);

    int deleteById(@Param("id") Long id);

    int deleteByUserIdAndSpuId(@Param("userId") Long userId, @Param("spuId") Long spuId);

    Favorite getByUserIdAndSpuId(@Param("userId") Long userId, @Param("spuId") Long spuId);

    List<Favorite> listByUserId(@Param("userId") Long userId);

    /** 批量查询用户是否收藏了某些 SPU */
    List<Long> listSpuIdsByUserId(@Param("userId") Long userId, @Param("spuIds") List<Long> spuIds);
}