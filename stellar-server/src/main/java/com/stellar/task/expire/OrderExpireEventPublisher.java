package com.stellar.task.expire;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 订单过期事件的发布入口：把「订单已创建」翻译成一条延迟任务。
 *
 * <p>作为订单域与过期基础设施之间的唯一接缝，让 OrderServiceImpl 既不需要知道延迟队列，
 * 也不需要知道开关配置；关闭（{@code stellar.order.expire.enabled=false}）时退化为
 * 什么都不做，超时取消交由对账扫描兜底。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpireEventPublisher {

    private final OrderExpireDelayQueue delayQueue;

    private final OrderExpireProperties properties;

    /**
     * 下单落库后调用：开启事件驱动时把订单排入延迟队列（事务提交后才真正入队，回滚不入队）。
     */
    public void publishOrderCreated(Long orderId) {
        if (!properties.isEnabled()) {
            return;
        }
        delayQueue.scheduleAfterCommit(orderId);
    }
}
