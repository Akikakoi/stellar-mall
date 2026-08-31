package com.stellar.userflow;

import com.stellar.entity.PointsPayment;
import com.stellar.entity.UserPoints;
import com.stellar.mapper.*;
import com.stellar.service.UserMessageService;
import com.stellar.service.impl.PointsServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 积分原子 SQL（RR 隔离级别下规避"读 version → UPDATE WHERE version 重试"快照读陷阱）的聚焦测试。
 * <p>2026-08-31 修复：PointsServiceImpl 的冻结/消费/解冻从「乐观锁 + 重试」改为
 * 单条原子条件 SQL（freezePointsAtomic / consumeFrozenPointsAtomic / unfreezePointsAtomic），
 * 本测试验证原子方法被调用、余额条件由 SQL 兜底、不再出现快照读重试路径。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("积分原子 SQL — 冻结/消费/解冻")
class PointsAtomicTest {

    @Mock private UserPointsMapper userPointsMapper;
    @Mock private PointsRecordMapper pointsRecordMapper;
    @Mock private PointsRuleMapper pointsRuleMapper;
    @Mock private PointsProductMapper pointsProductMapper;
    @Mock private PointsRedemptionMapper pointsRedemptionMapper;
    @Mock private CheckinRecordMapper checkinRecordMapper;
    @Mock private CouponMapper couponMapper;
    @Mock private PointsPaymentMapper pointsPaymentMapper;
    @Mock private MallOrderMapper mallOrderMapper;
    @Mock private UserMessageService userMessageService;

    @InjectMocks
    private PointsServiceImpl pointsService;

    private static final Long USER_ID = 8888L;
    private static final Long ORDER_ID = 1000L;

    private UserPoints userPoints(int available, int frozen, int version) {
        return UserPoints.builder().id(1L).userId(USER_ID)
                .totalPoints(available + frozen)
                .availablePoints(available)
                .frozenPoints(frozen)
                .totalEarned(0).totalSpent(0)
                .version(version)
                .build();
    }

    private PointsPayment frozenPayment(int points, BigDecimal amount) {
        return PointsPayment.builder().id(1L).orderId(ORDER_ID).userId(USER_ID)
                .points(points).amount(amount).type(1) // 冻结
                .build();
    }

    // ========== 冻结 ==========

    @Test @DisplayName("冻结成功 → 调用 freezePointsAtomic（无 version 参数），返回冻结积分数")
    void freeze_success_callsAtomic() {
        when(userPointsMapper.getByUserId(USER_ID)).thenReturn(userPoints(1000, 0, 0));
        when(userPointsMapper.freezePointsAtomic(USER_ID, 500)).thenReturn(1);

        int frozen = pointsService.freezePointsForOrder(USER_ID, ORDER_ID, BigDecimal.valueOf(5), BigDecimal.valueOf(100));

        assertEquals(500, frozen);
        // 核心断言：走原子方法，绝不走带 version 的旧方法 + 重试
        verify(userPointsMapper, times(1)).freezePointsAtomic(USER_ID, 500);
        verify(userPointsMapper, never()).freezePoints(anyLong(), anyInt(), anyInt());
        verify(pointsPaymentMapper).insert(any(PointsPayment.class));
    }

    @Test @DisplayName("冻结时可用积分不足 → 直接用剩余可用冻结（不先试请求值）")
    void freeze_insufficient_usesAvailable() {
        when(userPointsMapper.getByUserId(USER_ID)).thenReturn(userPoints(300, 0, 0));
        when(userPointsMapper.freezePointsAtomic(USER_ID, 300)).thenReturn(1);

        int frozen = pointsService.freezePointsForOrder(USER_ID, ORDER_ID, BigDecimal.valueOf(5), BigDecimal.valueOf(100));

        assertEquals(300, frozen);
        // 预判不足 → 只按可用 300 冻结一次，不出现"先试 500 失败再降级"的路径
        verify(userPointsMapper, times(1)).freezePointsAtomic(USER_ID, 300);
        verify(userPointsMapper, never()).freezePointsAtomic(USER_ID, 500);
        verify(userPointsMapper, never()).freezePoints(anyLong(), anyInt(), anyInt());
    }

    @Test @DisplayName("冻结超过应付金额 → 按应付金额封顶冻结")
    void freeze_cappedByPayAmount() {
        when(userPointsMapper.getByUserId(USER_ID)).thenReturn(userPoints(5000, 0, 0));
        // 请求 100 元 → 应冻结 10000 积分，但应付 50 元封顶 → 5000 积分
        when(userPointsMapper.freezePointsAtomic(USER_ID, 5000)).thenReturn(1);

        int frozen = pointsService.freezePointsForOrder(USER_ID, ORDER_ID, BigDecimal.valueOf(100), BigDecimal.valueOf(50));

        assertEquals(5000, frozen);
        verify(userPointsMapper).freezePointsAtomic(USER_ID, 5000);
    }

    // ========== 消费冻结积分（支付） ==========

    @Test @DisplayName("消费冻结积分成功 → 调用 consumeFrozenPointsAtomic")
    void consume_success_callsAtomic() {
        when(pointsPaymentMapper.listByOrderAndUser(ORDER_ID, USER_ID))
                .thenReturn(Collections.singletonList(frozenPayment(500, BigDecimal.valueOf(5))));
        when(userPointsMapper.getByUserId(USER_ID)).thenReturn(userPoints(0, 500, 1));
        when(userPointsMapper.consumeFrozenPointsAtomic(USER_ID, 500)).thenReturn(1);
        when(pointsRecordMapper.insert(any())).thenReturn(1);
        when(pointsPaymentMapper.insert(any())).thenReturn(1);

        pointsService.consumeFrozenPointsForOrder(USER_ID, ORDER_ID);

        verify(userPointsMapper, times(1)).consumeFrozenPointsAtomic(USER_ID, 500);
        verify(userPointsMapper, never()).consumeFrozenPoints(anyLong(), anyInt(), anyInt());
    }

    @Test @DisplayName("消费冻结积分时冻结余额不足 → 原子 SQL rows=0，静默跳过不抛异常")
    void consume_frozenInsufficient_skips() {
        when(pointsPaymentMapper.listByOrderAndUser(ORDER_ID, USER_ID))
                .thenReturn(Collections.singletonList(frozenPayment(500, BigDecimal.valueOf(5))));
        when(userPointsMapper.getByUserId(USER_ID)).thenReturn(userPoints(0, 500, 1));
        when(userPointsMapper.consumeFrozenPointsAtomic(USER_ID, 500)).thenReturn(0);

        // 不抛异常，静默跳过（幂等保护：可能已被其他请求消费）
        pointsService.consumeFrozenPointsForOrder(USER_ID, ORDER_ID);
        verify(pointsPaymentMapper, never()).insert(any());
    }

    // ========== 解冻（取消订单） ==========

    @Test @DisplayName("解冻成功 → 调用 unfreezePointsAtomic")
    void unfreeze_success_callsAtomic() {
        when(pointsPaymentMapper.listByOrderAndUser(ORDER_ID, USER_ID))
                .thenReturn(Collections.singletonList(frozenPayment(300, BigDecimal.valueOf(3))));
        when(userPointsMapper.getByUserId(USER_ID)).thenReturn(userPoints(0, 300, 1));
        when(userPointsMapper.unfreezePointsAtomic(USER_ID, 300)).thenReturn(1);
        when(pointsPaymentMapper.insert(any())).thenReturn(1);

        pointsService.unfreezePointsForOrder(USER_ID, ORDER_ID);

        verify(userPointsMapper, times(1)).unfreezePointsAtomic(USER_ID, 300);
        verify(userPointsMapper, never()).unfreezePoints(anyLong(), anyInt(), anyInt());
    }

    @Test @DisplayName("解冻时冻结余额不足 → 原子 SQL rows=0，静默跳过")
    void unfreeze_frozenInsufficient_skips() {
        when(pointsPaymentMapper.listByOrderAndUser(ORDER_ID, USER_ID))
                .thenReturn(Collections.singletonList(frozenPayment(300, BigDecimal.valueOf(3))));
        when(userPointsMapper.getByUserId(USER_ID)).thenReturn(userPoints(0, 300, 1));
        when(userPointsMapper.unfreezePointsAtomic(USER_ID, 300)).thenReturn(0);

        pointsService.unfreezePointsForOrder(USER_ID, ORDER_ID);
        verify(pointsPaymentMapper, never()).insert(any());
    }

    // ========== 过期扣除（processExpiredPoints 内部使用 expirePointsAtomic） ==========

    @Test @DisplayName("过期扣除走 expirePointsAtomic（无 version 参数）")
    void expire_usesAtomic() {
        com.stellar.entity.PointsRecord record = com.stellar.entity.PointsRecord.builder()
                .id(1L).userId(USER_ID).type(1).points(200)
                .expiredTime(java.time.LocalDate.now()).build();
        when(pointsRecordMapper.findExpiringPoints(any(), any()))
                .thenReturn(Collections.singletonList(record));
        when(userPointsMapper.getByUserId(USER_ID)).thenReturn(userPoints(500, 0, 0));
        when(userPointsMapper.expirePointsAtomic(USER_ID, 200)).thenReturn(1);
        when(pointsRecordMapper.insert(any())).thenReturn(1);

        int total = pointsService.processExpiredPoints();

        assertEquals(200, total);
        verify(userPointsMapper, times(1)).expirePointsAtomic(USER_ID, 200);
        verify(userPointsMapper, never()).expirePoints(anyLong(), anyInt(), anyInt());
    }
}
