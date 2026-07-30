package com.stellar.mapper;

import com.stellar.annotation.AutoFill;
import com.stellar.entity.Sku;
import com.stellar.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * SKU Mapper。注意扣库存走乐观锁 SQL，不走带 @AutoFill 的 update，因为扣库存不希望改 update_user 之类的业务字段。
 */
@Mapper
public interface SkuMapper {

    @AutoFill(OperationType.INSERT)
    int insert(Sku sku);

    @AutoFill(OperationType.UPDATE)
    int update(Sku sku);

    int deleteById(@Param("id") Long id);

    int deleteBySpuId(@Param("spuId") Long spuId);

    Sku getById(@Param("id") Long id);

    /** 批量按 id 列表查询 SKU。 */
    List<Sku> listByIds(@Param("ids") List<Long> ids);

    List<Sku> listBySpuId(@Param("spuId") Long spuId);

    /**
     * 乐观锁扣库存：
     *   UPDATE stellar_sku SET stock = stock - #{qty}, version = version + 1
     *   WHERE id = #{id} AND version = #{version} AND stock >= #{qty}
     * 返回受影响行数：0 表示版本冲突或库存不足，调用方判定失败。
     */
    int deductStockWithVersion(@Param("id") Long id,
                               @Param("version") Integer version,
                               @Param("qty") Integer qty);

    /**
     * 乐观锁回滚库存（取消订单/关闭订单等场景）：
     *   UPDATE stellar_sku SET stock = stock + #{qty}, version = version + 1
     *   WHERE id = #{id} AND version = #{version}
     */
    int rollbackStockWithVersion(@Param("id") Long id,
                                 @Param("version") Integer version,
                                 @Param("qty") Integer qty);

    /**
     * Redis 分布式锁模式下直接扣库存（不依赖 version）：
     *   UPDATE stellar_sku SET stock = stock - #{qty}
     *   WHERE id = #{id} AND stock >= #{qty}
     */
    int deductStock(@Param("id") Long id, @Param("qty") Integer qty);

    /**
     * Redis 分布式锁模式下直接回滚库存（不依赖 version）：
     *   UPDATE stellar_sku SET stock = stock + #{qty}
     *   WHERE id = #{id}
     */
    int rollbackStock(@Param("id") Long id, @Param("qty") Integer qty);
}
