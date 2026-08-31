package com.stellar.userflow;

import com.stellar.context.BaseContext;
import com.stellar.dto.PointsRedeemDTO;
import com.stellar.entity.*;
import com.stellar.exception.BaseException;
import com.stellar.mapper.*;
import com.stellar.service.PointsService;
import com.stellar.service.UserMessageService;
import com.stellar.service.impl.PointsServiceImpl;
import com.stellar.service.impl.ReviewServiceImpl;
import com.stellar.service.impl.UserMessageServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * C 端横向越权修复的聚焦测试：
 * 1. 消息已读：markAsRead 按 userId + id 双条件，非本人消息 rows=0
 * 2. 评价提交：未购买过该 SPU 拒绝
 * 3. 积分兑换：addressId 非本人地址拒绝
 */
@DisplayName("C 端越权修复 — 消息/评价/积分兑换")
class HorizontalPrivilegeTest {

    private static final Long USER_ID = 8888L;
    private static final Long OTHER_ID = 9999L;

    @AfterEach
    void tearDown() {
        BaseContext.remove();
    }

    // ================= 1. 消息已读越权 =================

    @Test @DisplayName("markAsRead 按 userId+id 双条件更新，杜绝把他人消息标记已读")
    void markAsRead_requiresOwnership() {
        UserMessageMapper mapper = mock(UserMessageMapper.class);
        UserMessageServiceImpl service = new UserMessageServiceImpl(mapper);
        when(mapper.markAsRead(USER_ID, 100L)).thenReturn(1);

        service.markAsRead(USER_ID, 100L);

        // 核心断言：必须传 userId，SQL 带归属条件
        verify(mapper).markAsRead(USER_ID, 100L);
        verify(mapper, never()).markAsRead(anyLong(), eq(999L));
    }

    @Test @DisplayName("markAsRead 传入他人 userId → 双条件 SQL rows=0（不报错但无效果）")
    void markAsRead_otherUser_noEffect() {
        UserMessageMapper mapper = mock(UserMessageMapper.class);
        UserMessageServiceImpl service = new UserMessageServiceImpl(mapper);
        when(mapper.markAsRead(OTHER_ID, 100L)).thenReturn(0);

        service.markAsRead(OTHER_ID, 100L);

        verify(mapper).markAsRead(OTHER_ID, 100L);
    }

    // ================= 2. 评价提交资格校验 =================

    @Test @DisplayName("未购买过该 SPU 的评价 → 抛 BaseException，不写库不发积分")
    void reviewSubmit_withoutPurchase_rejected() {
        ReviewMapper reviewMapper = mock(ReviewMapper.class);
        SpuMapper spuMapper = mock(SpuMapper.class);
        PointsService pointsService = mock(PointsService.class);
        MallOrderItemMapper orderItemMapper = mock(MallOrderItemMapper.class);
        ReviewServiceImpl service = new ReviewServiceImpl(reviewMapper, spuMapper, pointsService, orderItemMapper);

        BaseContext.setCurrentId(USER_ID);
        when(orderItemMapper.countBoughtByUser(USER_ID, 1L)).thenReturn(0);
        Review review = Review.builder().spuId(1L).content("好评").build();

        BaseException ex = assertThrows(BaseException.class, () -> service.submit(review));

        assertTrue(ex.getMessage().contains("仅购买过该商品"));
        verify(reviewMapper, never()).insert(any());
        verify(pointsService, never()).earnByReview(anyLong(), anyLong());
    }

    @Test @DisplayName("购买过的用户可评价 → 正常写库")
    void reviewSubmit_withPurchase_success() {
        ReviewMapper reviewMapper = mock(ReviewMapper.class);
        SpuMapper spuMapper = mock(SpuMapper.class);
        PointsService pointsService = mock(PointsService.class);
        MallOrderItemMapper orderItemMapper = mock(MallOrderItemMapper.class);
        ReviewServiceImpl service = new ReviewServiceImpl(reviewMapper, spuMapper, pointsService, orderItemMapper);

        BaseContext.setCurrentId(USER_ID);
        when(orderItemMapper.countBoughtByUser(USER_ID, 1L)).thenReturn(1);
        when(reviewMapper.insert(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            r.setId(42L);
            return 1;
        });

        Review review = Review.builder().spuId(1L).content("好评").build();
        Long id = service.submit(review);

        assertEquals(42L, id);
        verify(reviewMapper).insert(any(Review.class));
    }

    // ================= 3. 积分兑换地址归属校验 =================

    @Test @DisplayName("兑换实物商品用他人地址 → 抛 BaseException")
    void redeem_otherUserAddress_rejected() {
        // 11 个依赖全 mock（构造器顺序 = 字段声明顺序）
        UserPointsMapper userPointsMapper = mock(UserPointsMapper.class);
        PointsRecordMapper recordMapper = mock(PointsRecordMapper.class);
        PointsRuleMapper ruleMapper = mock(PointsRuleMapper.class);
        PointsProductMapper productMapper = mock(PointsProductMapper.class);
        PointsRedemptionMapper redemptionMapper = mock(PointsRedemptionMapper.class);
        CheckinRecordMapper checkinMapper = mock(CheckinRecordMapper.class);
        CouponMapper couponMapper = mock(CouponMapper.class);
        PointsPaymentMapper paymentMapper = mock(PointsPaymentMapper.class);
        MallOrderMapper orderMapper = mock(MallOrderMapper.class);
        UserMessageService messageService = mock(UserMessageService.class);
        AddressMapper addressMapper = mock(AddressMapper.class);

        PointsServiceImpl service = new PointsServiceImpl(userPointsMapper, recordMapper, ruleMapper,
                productMapper, redemptionMapper, checkinMapper, couponMapper,
                paymentMapper, orderMapper, messageService, addressMapper);

        PointsProduct product = PointsProduct.builder()
                .id(1L).name("实物杯").productType("PHYSICAL")
                .pointsPrice(500).stock(10).status(1).build();
        when(productMapper.getById(1L)).thenReturn(product);
        // 他人地址：getById(addressId, userId) 查不到
        when(addressMapper.getById(5L, USER_ID)).thenReturn(null);

        PointsRedeemDTO dto = new PointsRedeemDTO();
        dto.setProductId(1L);
        dto.setAddressId(5L);

        BaseException ex = assertThrows(BaseException.class, () -> service.redeem(USER_ID, dto));
        assertTrue(ex.getMessage().contains("收货地址不存在"));
        verify(addressMapper).getById(5L, USER_ID);
    }

    @Test @DisplayName("兑换优惠券类商品不需要地址校验 → 正常执行")
    void redeem_couponProduct_skipsAddressCheck() {
        UserPointsMapper userPointsMapper = mock(UserPointsMapper.class);
        PointsRecordMapper recordMapper = mock(PointsRecordMapper.class);
        PointsRuleMapper ruleMapper = mock(PointsRuleMapper.class);
        PointsProductMapper productMapper = mock(PointsProductMapper.class);
        PointsRedemptionMapper redemptionMapper = mock(PointsRedemptionMapper.class);
        CheckinRecordMapper checkinMapper = mock(CheckinRecordMapper.class);
        CouponMapper couponMapper = mock(CouponMapper.class);
        PointsPaymentMapper paymentMapper = mock(PointsPaymentMapper.class);
        MallOrderMapper orderMapper = mock(MallOrderMapper.class);
        UserMessageService messageService = mock(UserMessageService.class);
        AddressMapper addressMapper = mock(AddressMapper.class);

        PointsServiceImpl service = new PointsServiceImpl(userPointsMapper, recordMapper, ruleMapper,
                productMapper, redemptionMapper, checkinMapper, couponMapper,
                paymentMapper, orderMapper, messageService, addressMapper);

        PointsProduct product = PointsProduct.builder()
                .id(2L).name("优惠券").productType("COUPON")
                .pointsPrice(300).stock(10).status(1).couponId(7L).build();
        when(productMapper.getById(2L)).thenReturn(product);
        UserPoints up = UserPoints.builder().userId(USER_ID).availablePoints(1000).frozenPoints(0)
                .totalPoints(1000).totalEarned(0).totalSpent(0).version(0).build();
        when(userPointsMapper.getByUserId(USER_ID)).thenReturn(up);
        when(userPointsMapper.deductPointsAtomic(USER_ID, 300)).thenReturn(1);
        when(productMapper.deductStock(2L)).thenReturn(1);
        Coupon coupon = Coupon.builder().id(7L).status(1).receivedCount(0).totalCount(100).build();
        when(couponMapper.getCouponById(7L)).thenReturn(coupon);
        when(couponMapper.incrReceivedCount(7L)).thenReturn(1);
        when(couponMapper.insertUserCoupon(any())).thenReturn(1);

        PointsRedeemDTO dto = new PointsRedeemDTO();
        dto.setProductId(2L);
        dto.setAddressId(5L); // 传了地址但优惠券商品不校验

        // 优惠券发放等后续流程可能还有额外 mock 需求；这里只验证不因地址校验抛异常
        assertDoesNotThrow(() -> service.redeem(USER_ID, dto));
    }
}
