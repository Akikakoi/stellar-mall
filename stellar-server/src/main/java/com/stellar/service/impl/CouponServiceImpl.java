package com.stellar.service.impl;

import com.stellar.context.BaseContext;
import com.stellar.entity.Coupon;
import com.stellar.entity.UserCoupon;
import com.stellar.mapper.CouponMapper;
import com.stellar.result.PageResult;
import com.stellar.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponMapper couponMapper;

    // ===== Admin =====
    @Override
    @Transactional
    public Long create(Coupon coupon) {
        Long userId = BaseContext.getCurrentId();
        coupon.setReceivedCount(0);
        coupon.setUsedCount(0);
        coupon.setCreateTime(LocalDateTime.now());
        coupon.setCreateUser(userId);
        coupon.setUpdateTime(LocalDateTime.now());
        coupon.setUpdateUser(userId);
        couponMapper.insertCoupon(coupon);
        return coupon.getId();
    }

    @Override
    @Transactional
    public void update(Coupon coupon) {
        Long userId = BaseContext.getCurrentId();
        coupon.setUpdateTime(LocalDateTime.now());
        coupon.setUpdateUser(userId);
        couponMapper.updateCoupon(coupon);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        couponMapper.deleteCoupon(id);
    }

    @Override
    public PageResult pageCoupon(String name, Integer status, Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int ps = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        List<Coupon> list = couponMapper.pageCoupon(name, status, (p - 1) * ps, ps);
        long total = couponMapper.countCoupon(name, status);
        return new PageResult(total, list == null ? new ArrayList<>() : list);
    }

    // ===== User =====
    @Override
    public List<Coupon> listAvailable(Long userId) {
        return couponMapper.listAvailableCoupons(userId);
    }

    @Override
    @Transactional
    public Long claim(Long couponId, Long userId) {
        Coupon coupon = couponMapper.getCouponById(couponId);
        if (coupon == null) throw new RuntimeException("优惠券不存在");
        if (coupon.getStatus() != 1) throw new RuntimeException("优惠券已禁用");
        if (coupon.getReceivedCount() >= coupon.getTotalCount()) throw new RuntimeException("优惠券已领完");
        long count = couponMapper.countUserCouponByCouponId(userId, couponId);
        if (coupon.getPerUserLimit() != null && coupon.getPerUserLimit() > 0 && count >= coupon.getPerUserLimit()) {
            throw new RuntimeException("已达领取上限");
        }
        couponMapper.incrReceivedCount(couponId);
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        uc.setStatus(1);
        uc.setCreateTime(LocalDateTime.now());
        uc.setCreateUser(userId);
        uc.setUpdateTime(LocalDateTime.now());
        uc.setUpdateUser(userId);
        couponMapper.insertUserCoupon(uc);
        return uc.getId();
    }

    @Override
    public List<UserCoupon> listUserCoupons(Long userId, Integer status) {
        return couponMapper.listUserCoupons(userId, status);
    }

    @Override
    public UserCoupon getUserCoupon(Long id) {
        return couponMapper.getUserCouponById(id);
    }

    @Override
    @Transactional
    public void useCoupon(Long userCouponId, Long orderId) {
        couponMapper.updateUserCouponStatus(userCouponId, 2, orderId, LocalDateTime.now());
        UserCoupon uc = couponMapper.getUserCouponById(userCouponId);
        if (uc != null && uc.getCouponId() != null) {
            couponMapper.incrUsedCount(uc.getCouponId());
        }
    }

    @Override
    @Transactional
    public void returnCouponByOrderId(Long orderId) {
        UserCoupon uc = couponMapper.getUserCouponByOrderId(orderId);
        if (uc == null) {
            // 该订单未使用优惠券，无需处理
            return;
        }
        if (uc.getStatus() == null || uc.getStatus() != 2) {
            // 优惠券状态不是已使用，无需退还（可能已退还过）
            return;
        }
        int rows = couponMapper.returnUserCoupon(uc.getId());
        if (rows > 0 && uc.getCouponId() != null) {
            couponMapper.decrUsedCount(uc.getCouponId());
        }
    }
}