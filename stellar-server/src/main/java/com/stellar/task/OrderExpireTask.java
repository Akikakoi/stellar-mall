package com.stellar.task;

import com.stellar.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

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

    /**
     * 单机重入保护。
     * <p>调度池扩容后同一任务可能被并行调度，手工触发或运维脚本也可能与定时执行重叠；
     * 重叠只会重复扫同一批订单、做无谓的 CAS 与库存回滚，这里用 CAS 保证同一时刻只有一轮在跑。
     * 多实例之间仍会各跑一份，靠取消逻辑自身的 CAS 幂等兜底（要彻底互斥需上 Redis 锁）。</p>
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 每 30 秒执行一次。
     * <p>用 fixedDelay 而非 fixedRate：fixedRate 是「按固定频率发起」，单轮耗时超过 30 秒时
     * 会连续补跑、任务不断叠加；fixedDelay 是「上一轮结束后再等 30 秒」，永不叠加。</p>
     */
    @Scheduled(fixedDelay = 30_000)
    public void cancelExpiredOrders() {
        if (!running.compareAndSet(false, true)) {
            log.warn("[OrderExpireTask] 上一轮尚未结束，跳过本轮");
            return;
        }
        try {
            int count = orderService.cancelExpiredOrders(MAX_BATCH);
            if (count > 0) {
                log.info("[OrderExpireTask] 本轮自动取消 {} 笔过期订单", count);
            }
        } catch (Exception e) {
            log.error("[OrderExpireTask] 执行失败", e);
        } finally {
            running.set(false);
        }
    }
}
