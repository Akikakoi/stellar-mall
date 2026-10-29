package com.stellar.task.expire;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpireDelayPoller {

    private final OrderExpireDelayQueue delayQueue;

    private final OrderExpireProperties properties;

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

    public int pollAndPublish() {
        int transferred = delayQueue.transferDue(System.currentTimeMillis(), properties.getPollBatchSize());
        if (transferred > 0) {
            log.info("[OrderExpireDelayPoller] 投递 {} 笔到期订单到 {}", transferred, properties.getStreamKey());
        }
        return transferred;
    }
}
