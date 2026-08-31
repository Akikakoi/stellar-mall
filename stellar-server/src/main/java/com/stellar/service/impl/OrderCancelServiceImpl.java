package com.stellar.service.impl;

import com.stellar.entity.MallOrder;
import com.stellar.entity.MallOrderItem;
import com.stellar.enumeration.OrderStatus;
import com.stellar.mapper.MallOrderItemMapper;
import com.stellar.mapper.MallOrderMapper;
import com.stellar.service.CouponService;
import com.stellar.service.OrderCancelService;
import com.stellar.service.PointsService;
import com.stellar.service.SkuStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 过期订单逐笔取消实现。
 * <p>
 * 通过 {@code REQUIRES_NEW} 为每一笔订单单独开启事务：CAS 占状态、回滚库存、
 * 解冻积分、退还优惠券在同一事务内原子提交。若回滚库存等步骤抛异常，异常穿过
 * 本 Bean 的事务代理边界时整个事务回滚（订单保持 PENDING，库存不丢失），
 * 定时任务下一轮会重新处理；批次中其他订单不受影响。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCancelServiceImpl implements OrderCancelService {

    private final MallOrderMapper mallOrderMapper;
    private final MallOrderItemMapper mallOrderItemMapper;
    private final SkuStockService skuStockService;
    private final CouponService couponService;
    private final PointsService pointsService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean cancelExpiredOrder(MallOrder order) {
        if (!OrderStatus.PENDING.getBackendValue().equals(order.getStatus())) {
            log.info("[OrderCancelService] 订单 {} 已非待付款状态（当前={}），跳过自动取消",
                    order.getId(), order.getStatus());
            return false;
        }

        // 先 CAS 占住 PENDING → CANCELLED，竞争失败说明订单刚被支付，跳过（不动库存）
        int rows = mallOrderMapper.casUpdateStatus(order.getId(),
                OrderStatus.PENDING.getBackendValue(), OrderStatus.CANCELLED.getBackendValue());
        if (rows == 0) {
            log.info("[OrderCancelService] 订单 {} CAS 竞争失败（可能刚被支付），跳过自动取消", order.getId());
            return false;
        }

        // 回滚库存：若此处抛异常，REQUIRES_NEW 事务整体回滚（含上面的 CAS），
        // 订单保持 PENDING，库存不丢失，下轮定时任务重试
        List<MallOrderItem> items = mallOrderItemMapper.listByOrderId(order.getId());
        if (items != null) {
            for (MallOrderItem it : items) {
                skuStockService.rollback(it.getSkuId(), it.getQty() == null ? 0 : it.getQty());
            }
        }

        // 解冻积分（沿用“积分异常不阻断取消主流程”的既有语义：仅记日志）
        try {
            pointsService.unfreezePointsForOrder(order.getUserId(), order.getId());
        } catch (Exception e) {
            log.error("[OrderCancelService] 积分解冻失败（取消不受影响）: orderId={}, userId={}",
                    order.getId(), order.getUserId(), e);
        }

        // 退还优惠券
        couponService.returnCouponByOrderId(order.getId());

        log.info("[OrderCancelService] 订单 {} 已自动过期取消，库存已回滚", order.getId());
        return true;
    }
}
