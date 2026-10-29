package com.stellar.task.expire;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpireDelayQueue {

    // Lua 出错不会回滚已执行命令，必须逐条先投递成功再移除延迟任务。
    private static final DefaultRedisScript<Long> TRANSFER_DUE_SCRIPT = new DefaultRedisScript<>(
            "local due = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2]) "
                    + "for _, orderId in ipairs(due) do "
                    + "redis.call('XADD', KEYS[2], '*', 'orderId', orderId) "
                    + "redis.call('ZREM', KEYS[1], orderId) "
                    + "end "
                    + "return #due", Long.class);

    private final StringRedisTemplate redis;

    private final OrderExpireProperties properties;

    /** 按配置的超时分钟数入队。 */
    public void schedule(Long orderId) {
        scheduleAt(orderId, System.currentTimeMillis() + properties.getDelayMinutes() * 60_000L);
    }

    /** 指定到期毫秒入队（便于测试与补投）。 */
    public void scheduleAt(Long orderId, long dueAtMillis) {
        if (orderId == null) {
            return;
        }
        try {
            scheduleAtOrThrow(orderId, dueAtMillis);
        } catch (Exception e) {
            // Redis 抖动不能反噬已提交的下单主流程：漏掉的延迟任务由低频对账扫描兜底
            log.error("[OrderExpireDelayQueue] 入队失败，将由对账扫描兜底: orderId={}", orderId, e);
        }
    }

    void scheduleAtOrThrow(Long orderId, long dueAtMillis) {
        redis.opsForZSet().add(properties.getDelayKey(), String.valueOf(orderId), dueAtMillis);
    }

    /**
     * 事务提交后再入队；无事务时立即入队。
     * <p>订单回滚不入队——否则会去取消一笔根本不存在的订单。</p>
     */
    public void scheduleAfterCommit(Long orderId) {
        if (orderId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    schedule(orderId);
                }
            });
            return;
        }
        schedule(orderId);
    }

    public int transferDue(long nowMillis, int limit) {
        if (limit <= 0) {
            return 0;
        }
        Long transferred = redis.execute(TRANSFER_DUE_SCRIPT,
                List.of(properties.getDelayKey(), properties.getStreamKey()),
                String.valueOf(nowMillis), String.valueOf(limit));
        return transferred == null ? 0 : transferred.intValue();
    }
}
