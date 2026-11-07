package com.stellar.userflow;

import com.stellar.entity.AfterSale;
import com.stellar.entity.MallOrder;
import com.stellar.enumeration.AfterSaleStatus;
import com.stellar.enumeration.AfterSaleType;
import com.stellar.enumeration.OrderStatus;
import com.stellar.exception.BaseException;
import com.stellar.mapper.AfterSaleMapper;
import com.stellar.mapper.MallOrderItemMapper;
import com.stellar.mapper.MallOrderMapper;
import com.stellar.mapper.SkuMapper;
import com.stellar.mapper.SpuMapper;
import com.stellar.service.CouponService;
import com.stellar.service.OrderService;
import com.stellar.service.PointsFacade;
import com.stellar.service.UserMessageService;
import com.stellar.service.WalletService;
import com.stellar.service.impl.AfterSaleServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 售后确认退款：CAS 状态流转 + 重复请求幂等。
 * <p>
 * 关键约束：同一售后单只能有一次请求获得 REFUNDING → COMPLETED 的状态推进，
 * 只有胜出的请求执行钱包退款、库存回滚、退券和积分处理；重复请求返回成功或明确报错，
 * 不能再次产生资金/库存副作用。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AfterSaleService — 确认退款并发与幂等")
class AfterSaleRefundIdempotencyTest {

    private static final Long AFTER_SALE_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final Long USER_ID = 8888L;
    private static final Long SKU_ID = 10L;
    private static final Long EMP_ID = 9L;
    private static final BigDecimal AMOUNT = new BigDecimal("99.00");

    @Mock private AfterSaleMapper afterSaleMapper;
    @Mock private MallOrderMapper mallOrderMapper;
    @Mock private MallOrderItemMapper mallOrderItemMapper;
    @Mock private SkuMapper skuMapper;
    @Mock private SpuMapper spuMapper;
    @Mock private OrderService orderService;
    @Mock private CouponService couponService;
    @Mock private UserMessageService userMessageService;
    @Mock private WalletService walletService;
    @Mock private PointsFacade pointsFacade;

    @InjectMocks
    private AfterSaleServiceImpl afterSaleService;

    private AfterSale afterSale(Integer status) {
        return afterSale(status, AfterSaleType.RETURN_REFUND.getCode());
    }

    private AfterSale afterSale(Integer status, Integer type) {
        return AfterSale.builder()
                .id(AFTER_SALE_ID)
                .orderId(ORDER_ID)
                .skuId(SKU_ID)
                .userId(USER_ID)
                .type(type)
                .status(status)
                .amount(AMOUNT)
                .build();
    }

    private MallOrder order() {
        return MallOrder.builder().id(ORDER_ID).orderNo("SO1").userId(USER_ID)
                .status(OrderStatus.REFUNDED.getBackendValue()).build();
    }

    private void stubCas(int rows) {
        when(afterSaleMapper.casConfirmRefund(
                eq(AFTER_SALE_ID),
                eq(AfterSaleStatus.REFUNDING.getCode()),
                eq(AfterSaleStatus.COMPLETED.getCode()),
                any(LocalDateTime.class),
                eq(EMP_ID))).thenReturn(rows);
    }

    @Test
    @DisplayName("CAS 抢到状态 → 退款副作用只执行一次")
    void casWins_executesSideEffectsOnce() {
        when(afterSaleMapper.getById(AFTER_SALE_ID))
                .thenReturn(afterSale(AfterSaleStatus.REFUNDING.getCode()));
        stubCas(1);
        when(mallOrderMapper.getById(ORDER_ID)).thenReturn(order());

        afterSaleService.confirmRefund(EMP_ID, AFTER_SALE_ID);

        verify(orderService).completeRefund(ORDER_ID, SKU_ID, true);
        verify(walletService).refundToWallet(USER_ID, ORDER_ID, AMOUNT);
        verify(couponService).returnCouponByOrderId(ORDER_ID);
        verify(pointsFacade).reclaimEarnForOrderQuietly(USER_ID, ORDER_ID);
        verify(pointsFacade).refundForOrderQuietly(eq(USER_ID), eq(ORDER_ID), any(BigDecimal.class));
        verify(userMessageService).createMessage(
                eq(USER_ID), eq("AFTER_SALE_COMPLETED"), any(), any(), eq(ORDER_ID));
    }

    @Test
    @DisplayName("仅退款 → 不回滚库存；整单退完时仍收回奖励积分")
    void refundOnly_skipsStockRollback() {
        when(afterSaleMapper.getById(AFTER_SALE_ID)).thenReturn(
                afterSale(AfterSaleStatus.REFUNDING.getCode(), AfterSaleType.REFUND_ONLY.getCode()));
        stubCas(1);
        when(mallOrderMapper.getById(ORDER_ID)).thenReturn(order());

        afterSaleService.confirmRefund(EMP_ID, AFTER_SALE_ID);

        verify(orderService).completeRefund(ORDER_ID, SKU_ID, false);
        verify(pointsFacade).reclaimEarnForOrderQuietly(USER_ID, ORDER_ID);
    }

    @Test
    @DisplayName("部分退款 → 不收回订单赠送积分，只按比例退还抵扣积分")
    void partialRefund_doesNotReclaimEarnedPoints() {
        when(afterSaleMapper.getById(AFTER_SALE_ID))
                .thenReturn(afterSale(AfterSaleStatus.REFUNDING.getCode(), AfterSaleType.RETURN_REFUND.getCode()));
        stubCas(1);
        MallOrder partial = MallOrder.builder().id(ORDER_ID).orderNo("SO1").userId(USER_ID)
                .status(OrderStatus.PARTIAL_REFUNDED.getBackendValue()).build();
        when(mallOrderMapper.getById(ORDER_ID)).thenReturn(partial);

        afterSaleService.confirmRefund(EMP_ID, AFTER_SALE_ID);

        verify(pointsFacade, never()).reclaimEarnForOrderQuietly(USER_ID, ORDER_ID);
        verify(couponService, never()).returnCouponByOrderId(ORDER_ID);
        verify(pointsFacade).refundForOrderQuietly(eq(USER_ID), eq(ORDER_ID), any(BigDecimal.class));
    }

    @Test
    @DisplayName("已是完成状态 → 幂等返回，不再执行任何退款副作用")
    void alreadyCompleted_isIdempotent() {
        when(afterSaleMapper.getById(AFTER_SALE_ID))
                .thenReturn(afterSale(AfterSaleStatus.COMPLETED.getCode()));

        assertDoesNotThrow(() -> afterSaleService.confirmRefund(EMP_ID, AFTER_SALE_ID));

        verify(afterSaleMapper, never()).casConfirmRefund(anyLong(), any(), any(), any(), anyLong());
        verifyNoInteractions(orderService, walletService, couponService, pointsFacade, userMessageService);
    }

    @Test
    @DisplayName("CAS 输掉但订单已被另一请求完成 → 幂等返回，不重复退款")
    void casLosesAfterCompletion_isIdempotent() {
        when(afterSaleMapper.getById(AFTER_SALE_ID)).thenReturn(
                afterSale(AfterSaleStatus.REFUNDING.getCode()),
                afterSale(AfterSaleStatus.COMPLETED.getCode()));
        stubCas(0);

        assertDoesNotThrow(() -> afterSaleService.confirmRefund(EMP_ID, AFTER_SALE_ID));

        verifyNoInteractions(orderService, walletService, couponService, pointsFacade, userMessageService);
    }

    @Test
    @DisplayName("CAS 输掉且状态仍是退款中 → 报状态错误，不执行退款")
    void casLosesStillRefunding_throws() {
        when(afterSaleMapper.getById(AFTER_SALE_ID))
                .thenReturn(afterSale(AfterSaleStatus.REFUNDING.getCode()));
        stubCas(0);

        assertThrows(BaseException.class,
                () -> afterSaleService.confirmRefund(EMP_ID, AFTER_SALE_ID));

        verifyNoInteractions(orderService, walletService, couponService, pointsFacade, userMessageService);
    }

    @Test
    @DisplayName("退款已完成但通知失败 → 不回滚退款主流程")
    void notificationFailure_doesNotBreakRefund() {
        when(afterSaleMapper.getById(AFTER_SALE_ID))
                .thenReturn(afterSale(AfterSaleStatus.REFUNDING.getCode()));
        stubCas(1);
        when(mallOrderMapper.getById(ORDER_ID)).thenReturn(order());
        doThrow(new RuntimeException("message service down"))
                .when(userMessageService).createMessage(anyLong(), any(), any(), any(), anyLong());

        assertDoesNotThrow(() -> afterSaleService.confirmRefund(EMP_ID, AFTER_SALE_ID));

        verify(walletService).refundToWallet(USER_ID, ORDER_ID, AMOUNT);
        verify(orderService).completeRefund(ORDER_ID, SKU_ID, true);
    }
}
