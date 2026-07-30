package com.stellar.task;

import com.stellar.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单自动过期定时任务：每 30 秒扫描一次，将超过 15 分钟未支付的订单自动取消。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpireTask {

    private final OrderService orderService;

    /** 每次扫描最多取消 200 笔订单，避免一次性处理过多。 */
    private static final int MAX_BATCH = 200;

    /** 每 30 秒执行一次 */
    @Scheduled(fixedRate = 30_000)
    public void cancelExpiredOrders() {
        try {
            int count = orderService.cancelExpiredOrders(MAX_BATCH);
            if (count > 0) {
                log.info("[OrderExpireTask] 本轮自动取消 {} 笔过期订单", count);
            }
        } catch (Exception e) {
            log.error("[OrderExpireTask] 执行失败", e);
        }
    }
}
