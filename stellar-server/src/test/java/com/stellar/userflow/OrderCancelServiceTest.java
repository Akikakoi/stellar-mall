package com.stellar.userflow;

import com.stellar.entity.MallOrder;
import com.stellar.entity.MallOrderItem;
import com.stellar.enumeration.OrderStatus;
import com.stellar.mapper.MallOrderItemMapper;
import com.stellar.mapper.MallOrderMapper;
import com.stellar.service.CouponService;
import com.stellar.service.PointsService;
import com.stellar.service.SkuStockService;
import com.stellar.service.impl.OrderCancelServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 过期订单逐笔取消服务单测。
 * <p>
 * 重点验证：CAS 占位成功才回滚库存 / 失败直接跳过；回滚库存异常向上传播
 * （由 REQUIRES_NEW 事务代理回滚整笔，本单测不启容器，事务行为由注解保证）。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCancelService — 逐笔取消过期订单")
class OrderCancelServiceTest {

    @Mock private MallOrderMapper mallOrderMapper;
    @Mock private MallOrderItemMapper mallOrderItemMapper;
    @Mock private SkuStockService skuStockService;
    @Mock private CouponService couponService;
    @Mock private PointsService pointsService;

    @InjectMocks
    private OrderCancelServiceImpl orderCancelService;

    private static final Long ORDER_ID = 1000L;
    private static final Long USER_ID = 8888L;

    private MallOrder pendingOrder() {
        return MallOrder.builder().id(ORDER_ID).orderNo("SO1").userId(USER_ID)
                .status(OrderStatus.PENDING.getBackendValue())
                .totalAmount(BigDecimal.valueOf(100)).payAmount(BigDecimal.valueOf(100))
                .build();
    }

    @Test @DisplayName("CAS 成功 → 回滚库存+解冻积分+退券，返回 true")
    void casSuccess_rollsBackAll() {
        MallOrder order = pendingOrder();
        when(mallOrderMapper.casUpdateStatus(ORDER_ID,
                OrderStatus.PENDING.getBackendValue(), OrderStatus.CANCELLED.getBackendValue())).thenReturn(1);

        MallOrderItem it = MallOrderItem.builder().id(1L).orderId(ORDER_ID).skuId(10L)
                .qty(2).price(BigDecimal.valueOf(50))
                .subtotal(BigDecimal.valueOf(100)).build();
        when(mallOrderItemMapper.listByOrderId(ORDER_ID)).thenReturn(Collections.singletonList(it));

        boolean ok = orderCancelService.cancelExpiredOrder(order);

        assertTrue(ok);
        verify(skuStockService).rollback(10L, 2);
        verify(pointsService).unfreezePointsForOrder(USER_ID, ORDER_ID);
        verify(couponService).returnCouponByOrderId(ORDER_ID);
    }

    @Test @DisplayName("CAS 失败（订单刚被支付）→ 跳过，不动库存，返回 false")
    void casFail_skipped() {
        MallOrder order = pendingOrder();
        // casUpdateStatus 未 stub → 默认返回 0
        boolean ok = orderCancelService.cancelExpiredOrder(order);

        assertFalse(ok);
        verify(skuStockService, never()).rollback(anyLong(), anyInt());
        verify(mallOrderItemMapper, never()).listByOrderId(anyLong());
    }

    @Test @DisplayName("订单已非 PENDING → 直接跳过，不调 CAS")
    void nonPending_skipped() {
        MallOrder order = pendingOrder();
        order.setStatus(OrderStatus.PAID.getBackendValue());

        boolean ok = orderCancelService.cancelExpiredOrder(order);

        assertFalse(ok);
        verify(mallOrderMapper, never()).casUpdateStatus(anyLong(), anyString(), anyString());
    }

    @Test @DisplayName("回滚库存抛异常 → 异常向上传播（事务代理据此回滚整笔）")
    void rollbackThrows_propagates() {
        MallOrder order = pendingOrder();
        when(mallOrderMapper.casUpdateStatus(ORDER_ID,
                OrderStatus.PENDING.getBackendValue(), OrderStatus.CANCELLED.getBackendValue())).thenReturn(1);
        MallOrderItem it = MallOrderItem.builder().id(1L).orderId(ORDER_ID).skuId(10L)
                .qty(1).price(BigDecimal.valueOf(100))
                .subtotal(BigDecimal.valueOf(100)).build();
        when(mallOrderItemMapper.listByOrderId(ORDER_ID)).thenReturn(Collections.singletonList(it));
        doThrow(new RuntimeException("库存回滚失败")).when(skuStockService).rollback(10L, 1);

        assertThrows(RuntimeException.class, () -> orderCancelService.cancelExpiredOrder(order));
        // CAS 已执行但事务未提交，异常传播后由 REQUIRES_NEW 回滚 → 订单不会停留在 CANCELLED
        verify(mallOrderMapper).casUpdateStatus(ORDER_ID,
                OrderStatus.PENDING.getBackendValue(), OrderStatus.CANCELLED.getBackendValue());
        // 异常后解冻积分/退券不再执行
        verify(pointsService, never()).unfreezePointsForOrder(anyLong(), anyLong());
        verify(couponService, never()).returnCouponByOrderId(anyLong());
    }
}
