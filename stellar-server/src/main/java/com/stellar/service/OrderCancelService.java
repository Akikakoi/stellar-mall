package com.stellar.service;

import com.stellar.entity.MallOrder;

/**
 * 过期订单逐笔取消服务。
 * <p>
 * 与 {@link OrderService#cancelExpiredOrders(int)} 解耦的独立 Bean：
 * 单笔订单的“CAS 改状态 → 回滚库存 → 解冻积分 → 退还优惠券”在独立的
 * REQUIRES_NEW 事务中执行，任何一步失败只回滚当前笔，不影响批次中的其他订单，
 * 也不会出现“订单已取消但库存未归还”的库存丢失。
 * </p>
 */
public interface OrderCancelService {

    /**
     * 取消单笔过期订单（每笔独立事务）。
     *
     * @param order 过期待付款订单（来自 listExpiredPending 扫描）
     * @return true = 本次实际取消成功；false = 状态已变更（如刚被支付）被跳过
     */
    boolean cancelExpiredOrder(MallOrder order);
}
