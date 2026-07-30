package com.stellar.mapper;

import com.stellar.annotation.AutoFill;
import com.stellar.entity.AfterSale;
import com.stellar.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 售后表 Mapper。
 */
@Mapper
public interface AfterSaleMapper {

    @AutoFill(OperationType.INSERT)
    int insert(AfterSale afterSale);

    @AutoFill(OperationType.UPDATE)
    int update(AfterSale afterSale);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    AfterSale getById(@Param("id") Long id);

    /** 按用户查询售后列表（分页） */
    List<AfterSale> listByUserId(@Param("userId") Long userId,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    /** 按用户统计售后数量 */
    int countByUserId(@Param("userId") Long userId);

    /** 管理端分页查询售后列表 */
    List<AfterSale> listAll(@Param("offset") int offset,
                            @Param("limit") int limit,
                            @Param("status") Integer status,
                            @Param("type") Integer type);

    /** 管理端统计售后数量 */
    int count(@Param("status") Integer status,
              @Param("type") Integer type);

    /**
     * 检查某订单 SKU 是否已有进行中的售后单。
     * 进行中状态：申请中(1)、审核中(2)、退货中(3)、退款中(4)
     */
    int countActiveByOrderAndSku(@Param("orderId") Long orderId,
                                 @Param("skuId") Long skuId);

    /** 根据订单ID和用户ID查询售后单 */
    AfterSale getByOrderIdAndUserId(@Param("orderId") Long orderId, @Param("userId") Long userId);
}
