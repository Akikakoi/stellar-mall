package com.stellar.mapper;

import com.stellar.entity.PointsProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 积分商城商品 Mapper。
 */
@Mapper
public interface PointsProductMapper {

    int insert(PointsProduct product);

    int update(PointsProduct product);

    int deleteById(@Param("id") Long id);

    PointsProduct getById(@Param("id") Long id);

    /** 管理端分页列表 */
    List<PointsProduct> page(@Param("name") String name,
                             @Param("status") Integer status,
                             @Param("offset") int offset,
                             @Param("limit") int limit);

    int count(@Param("name") String name, @Param("status") Integer status);

    /** C端已上架列表 */
    List<PointsProduct> listOnSale();

    /** 乐观锁扣库存 */
    int deductStock(@Param("id") Long id);
}
