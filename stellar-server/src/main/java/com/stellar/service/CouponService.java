package com.stellar.service;

import com.stellar.entity.Coupon;
import com.stellar.entity.UserCoupon;
import com.stellar.result.PageResult;

import java.util.List;

public interface CouponService {
    // Admin
    Long create(Coupon coupon);
    void update(Coupon coupon);
    void delete(Long id);
    PageResult pageCoupon(String name, Integer status, Integer page, Integer pageSize);

    // User
    List<Coupon> listAvailable(Long userId);
    Long claim(Long couponId, Long userId);
    List<UserCoupon> listUserCoupons(Long userId, Integer status);
    UserCoupon getUserCoupon(Long id);
    void useCoupon(Long userCouponId, Long orderId);

    /** 退款时退还优惠券：根据订单ID查找并恢复优惠券为未使用状态 */
    void returnCouponByOrderId(Long orderId);
}