package com.stellar.task;

import com.stellar.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 过期订单对账兜底任务。
 *
 * <p>超时自动取消的主链路已改为延迟消息（见 {@code com.stellar.task.expire} 包：下单登记延迟任务
 * → 到期投递到 Stream → 消费组执行取消）。本任务退居为<b>低频对账</b>：兜住延迟消息丢失的情况
 * （事务提交后 Redis 入队失败、Redis 数据丢失等），重新扫出仍未支付的过期订单。</p>
 *
 * <p>扫描周期由 {@code stellar.order.expire.reconcile-interval-ms} 控制，默认 10 分钟。若把事件驱动
 * 开关 {@code stellar.order.expire.enabled} 关掉，并把该周期调回 30 秒，即等价于旧的纯轮询方案。</p>
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
     * 低频对账扫描。
     * <p>用 fixedDelay 而非 fixedRate：fixedRate 是「按固定频率发起」，单轮耗时超过周期时
     * 会连续补跑、任务不断叠加；fixedDelay 是「上一轮结束后再等一个周期」，永不叠加。</p>
     */
    @Scheduled(fixedDelayString = "${stellar.order.expire.reconcile-interval-ms:600000}")
    public void cancelExpiredOrders() {
        if (!running.compareAndSet(false, true)) {
            log.warn("[OrderExpireTask] 上一轮尚未结束，跳过本轮");
            return;
        }
        try {
            int count = orderService.cancelExpiredOrders(MAX_BATCH);
            if (count > 0) {
                log.info("[OrderExpireTask] 对账扫描自动取消 {} 笔过期订单", count);
            }
        } catch (Exception e) {
            log.error("[OrderExpireTask] 执行失败", e);
        } finally {
            running.set(false);
        }
    }
}
