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

    /**
     * 统计用户是否购买过指定 SPU（关联订单主表，仅统计已支付/已发货/已完成/已退款的订单）。
     * 返回 &gt; 0 表示购买过，用于评价提交前的资格校验。
     */
    int countBoughtByUser(@Param("userId") Long userId, @Param("spuId") Long spuId);
}
