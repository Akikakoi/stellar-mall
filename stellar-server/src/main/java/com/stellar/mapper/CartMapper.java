package com.stellar.mapper;

import com.stellar.annotation.AutoFill;
import com.stellar.entity.Cart;
import com.stellar.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 购物车 Mapper。表：stellar_cart。
 */
@Mapper
public interface CartMapper {

    @AutoFill(OperationType.INSERT)
    int insert(Cart cart);

    @AutoFill(OperationType.UPDATE)
    int update(Cart cart);

    int deleteById(@Param("id") Long id);

    int deleteByIds(@Param("ids") List<Long> ids);

    int deleteByUserId(@Param("userId") Long userId);

    Cart getById(@Param("id") Long id);

    Cart getByUserIdAndSkuId(@Param("userId") Long userId, @Param("skuId") Long skuId);

    List<Cart> listByUserId(@Param("userId") Long userId);

    /** 下单专用：只查 checked=1 的购物车项。 */
    List<Cart> listCheckedByUserId(@Param("userId") Long userId);
}
