package com.stellar.mapper;

import com.stellar.entity.StockLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库存变动流水 Mapper。
 */
@Mapper
public interface StockLogMapper {

    int insert(StockLog log);

    /**
     * 出入库日志分页查询（按时间倒序），支持组合筛选：
     * @param skuId   SKU ID（精确，可空）
     * @param keyword 商品名称模糊（联查 stellar_sku.name，可空）
     * @param type    操作类型：1 入库，2 出库（可空）
     * @param begin   操作时间起（含，可空）
     * @param end     操作时间止（含，可空）
     */
    List<StockLog> page(@Param("skuId") Long skuId,
                        @Param("keyword") String keyword,
                        @Param("type") Integer type,
                        @Param("begin") LocalDateTime begin,
                        @Param("end") LocalDateTime end,
                        @Param("offset") int offset,
                        @Param("limit") int limit);

    /** 出入库日志计数（筛选条件同 {@link #page}）。 */
    long count(@Param("skuId") Long skuId,
               @Param("keyword") String keyword,
               @Param("type") Integer type,
               @Param("begin") LocalDateTime begin,
               @Param("end") LocalDateTime end);
}
