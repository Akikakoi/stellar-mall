package com.stellar.mapper;

import com.stellar.entity.Coupon;
import com.stellar.entity.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 优惠券 Mapper，管理优惠券模板和用户领取记录。
 * 表：stellar_coupon（优惠券模板）、stellar_user_coupon（用户优惠券）。
 */
@Mapper
public interface CouponMapper {
    // Coupon CRUD
    int insertCoupon(Coupon coupon);
    int updateCoupon(Coupon coupon);
    int deleteCoupon(@Param("id") Long id);
    Coupon getCouponById(@Param("id") Long id);
    List<Coupon> pageCoupon(@Param("name") String name, @Param("status") Integer status, @Param("offset") int offset, @Param("pageSize") int pageSize);
    long countCoupon(@Param("name") String name, @Param("status") Integer status);
    List<Coupon> listAvailableCoupons(@Param("userId") Long userId);
    int incrReceivedCount(@Param("id") Long id);
    int incrUsedCount(@Param("id") Long id);

    // UserCoupon
    int insertUserCoupon(UserCoupon userCoupon);
    int updateUserCouponStatus(@Param("id") Long id, @Param("status") Integer status, @Param("orderId") Long orderId, @Param("usedTime") java.time.LocalDateTime usedTime);
    List<UserCoupon> listUserCoupons(@Param("userId") Long userId, @Param("status") Integer status);
    UserCoupon getUserCouponById(@Param("id") Long id);
    UserCoupon getUserCouponByOrderId(@Param("orderId") Long orderId);
    long countUserCouponByCouponId(@Param("userId") Long userId, @Param("couponId") Long couponId);

    /** 退还优惠券：重置为未使用状态，清除订单关联 */
    int returnUserCoupon(@Param("id") Long id);
    /** 优惠券模板已使用数 -1 */
    int decrUsedCount(@Param("id") Long couponId);

    /** 查询即将过期的用户优惠券 */
    List<UserCoupon> findExpiringSoon(@Param("now") java.time.LocalDateTime now,
                                      @Param("deadline") java.time.LocalDateTime deadline);
}