package com.stellar.coupon;

import com.stellar.entity.Coupon;
import com.stellar.mapper.CouponMapper;
import com.stellar.service.impl.CouponServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 优惠券领取（claim）有效期校验回归测试。
 * 全部依赖 Mock，不依赖数据库。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("优惠券领取 — 有效期校验")
class CouponClaimValidityTest {

    @Mock private CouponMapper couponMapper;

    @InjectMocks
    private CouponServiceImpl couponService;

    private Coupon coupon(LocalDateTime start, LocalDateTime end) {
        return Coupon.builder().id(1L).name("测试券").status(1)
                .totalCount(100).receivedCount(0).perUserLimit(5)
                .startTime(start).endTime(end).build();
    }

    @Test @DisplayName("领取已过期优惠券 → 抛异常，不写入领取记录")
    void claimExpiredCoupon_throws() {
        when(couponMapper.getCouponById(1L)).thenReturn(
                coupon(LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1)));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> couponService.claim(1L, 100L));
        assertTrue(ex.getMessage().contains("过期"));
        verify(couponMapper, never()).insertUserCoupon(any());
        verify(couponMapper, never()).incrReceivedCount(anyLong());
    }

    @Test @DisplayName("领取未开始发放的优惠券 → 抛异常")
    void claimNotStartedCoupon_throws() {
        when(couponMapper.getCouponById(1L)).thenReturn(
                coupon(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(10)));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> couponService.claim(1L, 100L));
        assertTrue(ex.getMessage().contains("未开始"));
        verify(couponMapper, never()).insertUserCoupon(any());
    }

    @Test @DisplayName("有效期内领取 → 正常成功")
    void claimWithinValidity_success() {
        when(couponMapper.getCouponById(1L)).thenReturn(
                coupon(LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1)));
        when(couponMapper.incrReceivedCount(1L)).thenReturn(1);
        when(couponMapper.countUserCouponByCouponId(100L, 1L)).thenReturn(0L);

        assertDoesNotThrow(() -> couponService.claim(1L, 100L));
        verify(couponMapper).insertUserCoupon(any());
    }

    @Test @DisplayName("无有效期限制（null）的优惠券 → 正常领取")
    void claimNoValidity_success() {
        when(couponMapper.getCouponById(1L)).thenReturn(coupon(null, null));
        when(couponMapper.incrReceivedCount(1L)).thenReturn(1);
        when(couponMapper.countUserCouponByCouponId(100L, 1L)).thenReturn(0L);

        assertDoesNotThrow(() -> couponService.claim(1L, 100L));
        verify(couponMapper).insertUserCoupon(any());
    }
}
