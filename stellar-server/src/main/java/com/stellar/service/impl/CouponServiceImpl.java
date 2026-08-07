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

/**
 * 优惠券服务实现类。
 * 提供优惠券的创建、编辑、删除、分页查询，以及用户端优惠券的查看、领取、使用、退还等核心功能。
 */
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponMapper couponMapper;

    // ===== Admin =====

    /**
     * 创建优惠券。
     * 初始化领取/使用计数，设置创建与更新时间及操作用户，写入数据库后返回新优惠券ID。
     *
     * @param coupon 待创建的优惠券对象
     * @return 创建成功后返回的优惠券ID
     */
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

    /**
     * 更新优惠券信息。
     * 刷新更新时间与操作用户后，将变更持久化到数据库。
     *
     * @param coupon 包含更新字段的优惠券对象
     */
    @Override
    @Transactional
    public void update(Coupon coupon) {
        Long userId = BaseContext.getCurrentId();
        coupon.setUpdateTime(LocalDateTime.now());
        coupon.setUpdateUser(userId);
        couponMapper.updateCoupon(coupon);
    }

    /**
     * 删除指定ID的优惠券。
     *
     * @param id 要删除的优惠券ID
     */
    @Override
    @Transactional
    public void delete(Long id) {
        couponMapper.deleteCoupon(id);
    }

    /**
     * 分页查询优惠券列表。
     * 支持按名称和状态筛选，返回分页结果。
     *
     * @param name     优惠券名称（模糊匹配，可为空）
     * @param status   优惠券状态（可为空）
     * @param page     当前页码（默认1）
     * @param pageSize 每页条数（默认10）
     * @return 包含总条数和数据列表的分页结果
     */
    @Override
    public PageResult pageCoupon(String name, Integer status, Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int ps = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        List<Coupon> list = couponMapper.pageCoupon(name, status, (p - 1) * ps, ps);
        long total = couponMapper.countCoupon(name, status);
        return new PageResult(total, list == null ? new ArrayList<>() : list);
    }

    // ===== User =====

    /**
     * 查询用户当前可领取的优惠券列表。
     *
     * @param userId 用户ID
     * @return 可用优惠券列表
     */
    @Override
    public List<Coupon> listAvailable(Long userId) {
        return couponMapper.listAvailableCoupons(userId);
    }

    /**
     * 用户领取优惠券。
     * 校验优惠券是否存在、是否启用、库存是否充足、用户是否已达领取上限。
     * 领取成功后增加已领取计数，创建用户优惠券记录并返回记录ID。
     *
     * @param couponId 优惠券ID
     * @param userId   领取用户ID
     * @return 用户优惠券记录ID
     * @throws RuntimeException 优惠券不存在、已禁用、已领完或已达领取上限时抛出
     */
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

    /**
     * 查询用户持有的优惠券列表。
     * 可按状态筛选，返回用户优惠券记录。
     *
     * @param userId 用户ID
     * @param status 优惠券状态（可为空，表示查询全部）
     * @return 用户优惠券列表
     */
    @Override
    public List<UserCoupon> listUserCoupons(Long userId, Integer status) {
        return couponMapper.listUserCoupons(userId, status);
    }

    /**
     * 根据ID查询用户优惠券记录详情。
     *
     * @param id 用户优惠券记录ID
     * @return 用户优惠券对象，不存在时返回null
     */
    @Override
    public UserCoupon getUserCoupon(Long id) {
        return couponMapper.getUserCouponById(id);
    }

    /**
     * 使用优惠券（核销）。
     * 将用户优惠券状态更新为已使用，关联订单ID，并递增优惠券的已使用计数。
     *
     * @param userCouponId 用户优惠券记录ID
     * @param orderId      关联的订单ID
     */
    @Override
    @Transactional
    public void useCoupon(Long userCouponId, Long orderId) {
        couponMapper.updateUserCouponStatus(userCouponId, 2, orderId, LocalDateTime.now());
        UserCoupon uc = couponMapper.getUserCouponById(userCouponId);
        if (uc != null && uc.getCouponId() != null) {
            couponMapper.incrUsedCount(uc.getCouponId());
        }
    }

    /**
     * 根据订单ID退还优惠券。
     * 用于订单退款/取消时，将已使用的优惠券恢复为可用状态，并递减已使用计数。
     * 若订单未使用优惠券或优惠券已退还，则直接返回不做处理。
     *
     * @param orderId 订单ID
     */
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