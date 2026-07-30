package com.stellar.mapper;

import com.stellar.entity.PointsPayment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 积分抵扣支付记录 Mapper。
 */
@Mapper
public interface PointsPaymentMapper {

    int insert(PointsPayment record);

    /** 查询某订单的积分支付记录 */
    List<PointsPayment> listByOrderId(@Param("orderId") Long orderId);

    /** 查询某用户某订单的积分支付记录 */
    List<PointsPayment> listByOrderAndUser(@Param("orderId") Long orderId,
                                           @Param("userId") Long userId);
}
