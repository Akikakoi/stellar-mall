package com.stellar.mapper;

import com.stellar.annotation.AutoFill;
import com.stellar.entity.MallOrder;
import com.stellar.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * C 端订单主单 Mapper。表：stellar_mall_order。
 */
@Mapper
public interface MallOrderMapper {

    @AutoFill(OperationType.INSERT)
    int insert(MallOrder order);

    @AutoFill(OperationType.UPDATE)
    int update(MallOrder order);

    /** 仅更新状态（pay/cancel 场景），避免 AutoFill 带 update_user 的副作用。 */
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * CAS 条件更新状态：仅当订单当前状态等于期望状态时才更新。
     * 用于支付/取消/确认收货等关键状态流转的并发防护，
     * 返回受影响行数（0 = 状态已被并发修改，调用方必须中止后续业务操作）。
     */
    int casUpdateStatus(@Param("id") Long id,
                        @Param("fromStatus") String fromStatus,
                        @Param("toStatus") String toStatus);

    /** 更新支付方式（二次支付时用户重新选择）。 */
    int updatePayMethod(@Param("id") Long id, @Param("payMethod") Integer payMethod);

    /** 发货：设置 SHIPPED 状态 + 物流信息（仅当当前状态为 PAID 时生效）。 */
    int ship(@Param("id") Long id,
             @Param("trackingNo") String trackingNo,
             @Param("deliveryCompany") String deliveryCompany);

    MallOrder getById(@Param("id") Long id);

    /** 批量按 id 列表查询订单，用于组装售后列表等场景，避免循环单条查询。 */
    List<MallOrder> listByIds(@Param("ids") List<Long> ids);

    MallOrder getByOrderNo(@Param("orderNo") String orderNo);

    List<MallOrder> listByUserId(@Param("userId") Long userId);

    /** 可选按后端字符串状态过滤；statusList 空或 null 表示全部。 */
    List<MallOrder> listByUserIdAndStatus(@Param("userId") Long userId,
                                          @Param("statusList") java.util.List<String> statusList);

    /** 管理端：分页查询所有订单（支持按状态、订单号和日期范围筛选）。 */
    List<MallOrder> listAll(@Param("offset") int offset,
                            @Param("limit") int limit,
                            @Param("status") String status,
                            @Param("orderNo") String orderNo,
                            @Param("startTime") String startTime,
                            @Param("endTime") String endTime);

    /** 管理端：统计订单总数（支持按状态、订单号和日期范围筛选）。 */
    int count(@Param("status") String status,
              @Param("orderNo") String orderNo,
              @Param("startTime") String startTime,
              @Param("endTime") String endTime);

    /** 管理端：删除订单（物理删除）。 */
    int deleteById(@Param("id") Long id);

    /** 退款完成：状态 → REFUNDED 并标记 is_refunded = 1。 */
    int markRefunded(@Param("id") Long id);

    /** 导出：查询全部订单（关联用户手机号），支持筛选。 */
    List<MallOrder> listAllForExport(@Param("status") String status,
                                     @Param("startTime") String startTime,
                                     @Param("endTime") String endTime);

    /** 财务报表：按月汇总。 */
    List<java.util.Map<String, Object>> financeMonthlySummary(@Param("year") String year);

    /** 查询状态为 PENDING 且创建时间早于 cutoffTime 的过期订单列表（用于自动取消）。 */
    List<MallOrder> listExpiredPending(@Param("cutoffTime") java.time.LocalDateTime cutoffTime);
}
