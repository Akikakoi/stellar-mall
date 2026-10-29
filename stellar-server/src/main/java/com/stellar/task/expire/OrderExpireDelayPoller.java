package com.stellar.task.expire;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 延迟队列 → Stream 的投递器：把已到期的订单从 ZSET 取出并投递到 Stream，交给消费组处理。
 *
 * <p>投递到 Stream 而不是直接处理，是为了让「到期」与「处理」解耦：多个实例通过消费组
 * 分摊消息（不会重复处理），且消息在 Stream 里可观测（XLEN / XPENDING / 消费滞后）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpireDelayPoller {

    private final OrderExpireDelayQueue delayQueue;

    private final OrderExpireProperties properties;

    private final StringRedisTemplate redis;

    @Scheduled(fixedDelayString = "${stellar.order.expire.poll-interval-ms:1000}")
    public void scheduledPoll() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            pollAndPublish();
        } catch (Exception e) {
            log.error("[OrderExpireDelayPoller] 扫描到期订单失败", e);
        }
    }

    /**
     * 取出到期订单并投递到 Stream。
     *
     * @return 本轮投递条数
     */
    public int pollAndPublish() {
        List<Long> due = delayQueue.popDue(System.currentTimeMillis(), properties.getPollBatchSize());
        if (due.isEmpty()) {
            return 0;
        }
        for (Long orderId : due) {
            redis.opsForStream().add(
                    StreamRecords.string(Collections.singletonMap("orderId", String.valueOf(orderId)))
                            .withStreamKey(properties.getStreamKey()));
        }
        // Stream 只作投递通道，不承担长期存储：按近似长度裁剪，防止无界增长
        // （XADD 本身不接受裁剪参数，用等价的独立 XTRIM）
        redis.opsForStream().trim(properties.getStreamKey(), properties.getStreamMaxLen(), true);
        log.info("[OrderExpireDelayPoller] 投递 {} 笔到期订单到 {}", due.size(), properties.getStreamKey());
        return due.size();
    }
}
