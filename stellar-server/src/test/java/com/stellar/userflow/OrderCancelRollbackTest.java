package com.stellar.userflow;

import com.stellar.entity.*;
import com.stellar.enumeration.OrderStatus;
import com.stellar.exception.BaseException;
import com.stellar.mapper.*;
import com.stellar.service.CouponService;
import com.stellar.service.SkuStockService;
import com.stellar.service.UserMessageService;
import com.stellar.service.WalletService;
import com.stellar.service.NotificationService;
import com.stellar.service.PointsService;
import com.stellar.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * M2-J-G4 RED(c): 纯 Mockito 测试 OrderService.cancel 流程。
 * - cancel 时逐条 rollback SKU 库存
 * - 订单状态更新为 CANCELLED
 * - 非 PENDING 状态不允许 cancel
 */
@ExtendWith(MockitoExtension.class)
class OrderCancelRollbackTest {

    @Mock
    private CartMapper cartMapper;
    @Mock
    private SkuMapper skuMapper;
    @Mock
    private SpuMapper spuMapper;
    @Mock
    private SkuStockService skuStockService;
    @Mock
    private MallOrderMapper mallOrderMapper;
    @Mock
    private MallOrderItemMapper mallOrderItemMapper;

    @Mock
    private CouponService couponService;
    @Mock
    private UserMessageService userMessageService;
    @Mock
    private WalletService walletService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private PointsService pointsService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private static final Long USER_ID = 7777L;
    private static final Long ORDER_ID = 5000L;

    private MallOrder buildOrder(Long id, String status) {
        MallOrder o = new MallOrder();
        o.setId(id);
        o.setOrderNo("NO" + id);
        o.setUserId(USER_ID);
        o.setTotalAmount(BigDecimal.valueOf(500));
        o.setPayAmount(BigDecimal.valueOf(500));
        o.setStatus(status);
        o.setAddress("测试地址");
        o.setPayMethod(1);
        o.setRemark("");
        o.setCreateTime(LocalDateTime.now());
        o.setCreateUser(USER_ID);
        o.setUpdateTime(LocalDateTime.now());
        o.setUpdateUser(USER_ID);
        return o;
    }

    private MallOrderItem buildItem(Long orderId, Long skuId, Long spuId,
                                     String spuName, String skuSpecs,
                                     BigDecimal price, int qty) {
        MallOrderItem it = new MallOrderItem();
        it.setId(skuId * 13 + 7);
        it.setOrderId(orderId);
        it.setSpuId(spuId);
        it.setSkuId(skuId);
        it.setSpuName(spuName);
        it.setSkuSpecs(skuSpecs);
        it.setPrice(price);
        it.setQty(qty);
        it.setSubtotal(price.multiply(BigDecimal.valueOf(qty)));
        return it;
    }

    // ========== cancel 成功场景 ==========

    @Test
    void cancelOrder_whenPending_rollbacksEveryItem_andSetsStatusCancelled() {
        MallOrder order = buildOrder(ORDER_ID, OrderStatus.PENDING.getBackendValue());
        when(mallOrderMapper.getById(ORDER_ID)).thenReturn(order);

        MallOrderItem it1 = buildItem(ORDER_ID, 11L, 1L, "商品A", "红", BigDecimal.valueOf(100), 2);
        MallOrderItem it2 = buildItem(ORDER_ID, 22L, 2L, "商品B", "蓝", BigDecimal.valueOf(50), 6);
        when(mallOrderItemMapper.listByOrderId(ORDER_ID)).thenReturn(Arrays.asList(it1, it2));

        // rollback 都成功
        doNothing().when(skuStockService).rollback(11L, 2);
        doNothing().when(skuStockService).rollback(22L, 6);

        when(mallOrderMapper.casUpdateStatus(ORDER_ID,
                OrderStatus.PENDING.getBackendValue(), OrderStatus.CANCELLED.getBackendValue())).thenReturn(1);

        // 执行
        assertDoesNotThrow(() -> orderService.cancel(ORDER_ID, USER_ID));

        // 逐条 rollback 被调用
        verify(skuStockService, times(1)).rollback(11L, 2);
        verify(skuStockService, times(1)).rollback(22L, 6);

        // 状态更新为 CANCELLED
        verify(mallOrderMapper, times(1)).casUpdateStatus(ORDER_ID,
                OrderStatus.PENDING.getBackendValue(), OrderStatus.CANCELLED.getBackendValue());
    }

    // ========== 订单不属于该用户 ==========

    @Test
    void cancelOrder_whenWrongUserId_throwsBaseException() {
        MallOrder order = buildOrder(ORDER_ID, OrderStatus.PENDING.getBackendValue());
        order.setUserId(9999L); // 不是 USER_ID
        when(mallOrderMapper.getById(ORDER_ID)).thenReturn(order);

        assertThrows(BaseException.class,
                () -> orderService.cancel(ORDER_ID, USER_ID),
                "订单不属于当前用户必须抛异常");

        verify(skuStockService, never()).rollback(anyLong(), anyInt());
        verify(mallOrderMapper, never()).casUpdateStatus(anyLong(), anyString(), anyString());
    }

    // ========== 非 PENDING 状态不允许 cancel ==========

    @Test
    void cancelOrder_whenPaidStatus_throwsOrderStatusError() {
        MallOrder order = buildOrder(ORDER_ID, OrderStatus.PAID.getBackendValue());
        when(mallOrderMapper.getById(ORDER_ID)).thenReturn(order);

        BaseException ex = assertThrows(BaseException.class,
                () -> orderService.cancel(ORDER_ID, USER_ID));
        assertTrue(ex.getMessage().contains("状态") || ex.getMessage().contains(OrderStatus.PENDING.getDescription()),
                "非 PENDING 取消必须报订单状态错误，实际：" + ex.getMessage());

        verify(skuStockService, never()).rollback(anyLong(), anyInt());
        verify(mallOrderMapper, never()).casUpdateStatus(anyLong(), anyString(), anyString());
    }

    @Test
    void cancelOrder_whenAlreadyCancelled_throwsOrderStatusError() {
        MallOrder order = buildOrder(ORDER_ID, OrderStatus.CANCELLED.getBackendValue());
        when(mallOrderMapper.getById(ORDER_ID)).thenReturn(order);

        assertThrows(BaseException.class,
                () -> orderService.cancel(ORDER_ID, USER_ID));

        verify(mallOrderMapper, never()).casUpdateStatus(anyLong(), anyString(), anyString());
    }

    // ========== 订单不存在 ==========

    @Test
    void cancelOrder_whenOrderNotFound_throwsBaseException() {
        when(mallOrderMapper.getById(ORDER_ID)).thenReturn(null);
        assertThrows(BaseException.class,
                () -> orderService.cancel(ORDER_ID, USER_ID));
    }

    // ========== pay 模拟成功 ==========

    @Test
    void payOrder_whenPending_updatesStatusToPaid() {
        MallOrder order = buildOrder(ORDER_ID, OrderStatus.PENDING.getBackendValue());
        when(mallOrderMapper.getById(ORDER_ID)).thenReturn(order);
        when(mallOrderMapper.casUpdateStatus(ORDER_ID,
                OrderStatus.PENDING.getBackendValue(), OrderStatus.PAID.getBackendValue())).thenReturn(1);

        assertDoesNotThrow(() -> orderService.pay(ORDER_ID, USER_ID, 1));
        verify(mallOrderMapper, times(1)).casUpdateStatus(ORDER_ID,
                OrderStatus.PENDING.getBackendValue(), OrderStatus.PAID.getBackendValue());
    }

    @Test
    void payOrder_whenNotPending_throwsException() {
        MallOrder order = buildOrder(ORDER_ID, OrderStatus.CANCELLED.getBackendValue());
        when(mallOrderMapper.getById(ORDER_ID)).thenReturn(order);

        assertThrows(BaseException.class,
                () -> orderService.pay(ORDER_ID, USER_ID, 1));

        verify(mallOrderMapper, never()).casUpdateStatus(eq(ORDER_ID), anyString(), anyString());
    }

    @Test
    void payOrder_whenWrongUserId_throwsException() {
        MallOrder order = buildOrder(ORDER_ID, OrderStatus.PENDING.getBackendValue());
        order.setUserId(1L);
        when(mallOrderMapper.getById(ORDER_ID)).thenReturn(order);

        assertThrows(BaseException.class,
                () -> orderService.pay(ORDER_ID, USER_ID, 1));
    }

    // ========== 明细为空（极端情况）→ 仍允许 cancel ==========

    @Test
    void cancelOrder_whenNoItems_stillUpdatesStatus() {
        MallOrder order = buildOrder(ORDER_ID, OrderStatus.PENDING.getBackendValue());
        when(mallOrderMapper.getById(ORDER_ID)).thenReturn(order);
        when(mallOrderItemMapper.listByOrderId(ORDER_ID)).thenReturn(Collections.emptyList());
        when(mallOrderMapper.casUpdateStatus(ORDER_ID,
                OrderStatus.PENDING.getBackendValue(), OrderStatus.CANCELLED.getBackendValue())).thenReturn(1);

        assertDoesNotThrow(() -> orderService.cancel(ORDER_ID, USER_ID));
        verify(skuStockService, never()).rollback(anyLong(), anyInt());
        verify(mallOrderMapper, times(1)).casUpdateStatus(ORDER_ID,
                OrderStatus.PENDING.getBackendValue(), OrderStatus.CANCELLED.getBackendValue());
    }
}
