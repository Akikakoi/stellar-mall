package com.stellar.service;

import com.stellar.entity.MallOrder;
import com.stellar.mapper.MallOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 积分操作统一收口（Facade）。
 *
 * 背景：积分是辅助系统，任何积分操作失败都不应阻断业务主流程（支付/取消/退款），
 * 但"吞掉异常"不等于"丢了就算了"——失败意味着资损（用户积分丢失/销量不涨），
 * 必须留下统一关键字的可检索告警日志，便于后续对账补偿。
 *
 * 约定：
 * 1. 本类是所有"积分跟随主流程静默执行"的唯一入口，新调用方禁止再手写 try-catch 模板；
 * 2. 失败日志统一带 [POINTS_LOSS] 关键字（销量累加用 [SALES_LOSS]），日志采集可按关键字告警；
 * 3. 中期可基于 points_record（只追加表）建对账任务，按本类日志的 orderId 维度补偿。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointsFacade {

    private final PointsService pointsService;
    private final MallOrderMapper mallOrderMapper;

    /** 支付成功后发放订单奖励积分（静默）。 */
    public void earnForOrderQuietly(Long userId, Long orderId, BigDecimal payAmount) {
        quietly("支付后发放订单奖励积分", userId, orderId,
                () -> pointsService.earnByOrder(userId, orderId, payAmount));
    }

    /** 支付成功后将冻结的抵扣积分转为实际消费（静默）。 */
    public void consumeFrozenForOrderQuietly(Long userId, Long orderId) {
        quietly("支付后扣减冻结积分", userId, orderId,
                () -> pointsService.consumeFrozenPointsForOrder(userId, orderId));
    }

    /** 取消订单后解冻积分（静默）。 */
    public void unfreezeForOrderQuietly(Long userId, Long orderId) {
        quietly("取消订单解冻积分", userId, orderId,
                () -> pointsService.unfreezePointsForOrder(userId, orderId));
    }

    /** 退款时收回订单赠送的奖励积分（静默）。 */
    public void reclaimEarnForOrderQuietly(Long userId, Long orderId) {
        quietly("退款收回订单奖励积分", userId, orderId,
                () -> pointsService.reclaimOrderEarnPoints(userId, orderId));
    }

    /**
     * 退款时按比例退还抵扣积分（静默）。
     *
     * 比例口径：售后退款金额与订单实付金额（payAmount）同口径 —— 积分抵扣只是支付手段，
     * 全额退款应退还全部抵扣积分；若以 (payAmount + pointsAmount) 作分母，
     * 全额退款比例 < 1，积分退不全。
     */
    public void refundForOrderQuietly(Long userId, Long orderId, BigDecimal refundAmount) {
        try {
            MallOrder order = mallOrderMapper.getById(orderId);
            if (order == null) return;
            int pointsDeducted = order.getPointsDeducted() != null ? order.getPointsDeducted() : 0;
            if (pointsDeducted <= 0) return;

            BigDecimal orderPayAmount = order.getPayAmount() != null ? order.getPayAmount() : BigDecimal.ZERO;
            BigDecimal pointsAmount = order.getPointsAmount() != null ? order.getPointsAmount() : BigDecimal.ZERO;
            BigDecimal ratioDenominator = orderPayAmount.compareTo(BigDecimal.ZERO) > 0
                    ? orderPayAmount
                    : orderPayAmount.add(pointsAmount);
            if (ratioDenominator.compareTo(BigDecimal.ZERO) <= 0) return;

            BigDecimal amount = refundAmount != null ? refundAmount : BigDecimal.ZERO;
            BigDecimal refundRatio = amount.divide(ratioDenominator, 4, RoundingMode.HALF_UP);
            if (refundRatio.compareTo(BigDecimal.ONE) > 0) {
                refundRatio = BigDecimal.ONE;
            }

            pointsService.refundPointsForOrder(userId, orderId, refundRatio);
        } catch (Exception e) {
            log.error("[POINTS_LOSS] 退款退还抵扣积分失败（退款主流程不受影响）: userId={}, orderId={}, refundAmount={}",
                    userId, orderId, refundAmount, e);
        }
    }

    private void quietly(String action, Long userId, Long orderId, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.error("[POINTS_LOSS] {}失败（业务主流程不受影响）: userId={}, orderId={}",
                    action, userId, orderId, e);
        }
    }
}
