package com.stellar.mapper;

import com.stellar.entity.MallOrderItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单明细 Mapper。表：stellar_mall_order_item。
 */
@Mapper
public interface MallOrderItemMapper {

    int insert(MallOrderItem item);

    /** 批量插入订单明细（单条多 VALUES SQL，比循环 insert 快得多）。 */
    int insertBatch(@Param("list") List<MallOrderItem> items);

    int deleteByOrderId(@Param("orderId") Long orderId);

    List<MallOrderItem> listByOrderId(@Param("orderId") Long orderId);

    /** 批量按订单 id 列表查询明细。 */
    List<MallOrderItem> listByOrderIds(@Param("orderIds") List<Long> orderIds);
}
