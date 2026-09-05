package com.stellar.mapper;

import com.stellar.entity.StockLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 库存变动流水 Mapper。
 */
@Mapper
public interface StockLogMapper {

    int insert(StockLog log);

    /** 按 SKU ID 分页查询流水，按时间倒序。 */
    List<StockLog> pageBySkuId(@Param("skuId") Long skuId,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    /** 统计指定 SKU 的流水总数。 */
    long countBySkuId(@Param("skuId") Long skuId);

    /** 查询所有 SKU 的流水（管理端总览），按时间倒序。 */
    List<StockLog> pageAll(@Param("offset") int offset,
                           @Param("limit") int limit);

    /** 统计流水总数。 */
    long countAll();
}