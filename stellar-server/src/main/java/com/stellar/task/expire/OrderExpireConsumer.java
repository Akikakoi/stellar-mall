package com.stellar.task.expire;

import com.stellar.entity.MallOrder;
import com.stellar.mapper.MallOrderMapper;
import com.stellar.service.OrderCancelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 订单过期消费者：从消费组读取到期订单并执行取消。
 *
 * <p>取消动作直接复用 {@link OrderCancelService#cancelExpiredOrder}——「CAS 占状态 → 回滚库存 →
 * 解冻积分 → 退券」在同一 REQUIRES_NEW 事务里完成，与轮询路径行为完全一致。</p>
 *
 * <p><b>失败语义</b>：处理抛异常时记 ERROR 并照样 ACK，不做流内重试。原因是取消动作本身幂等，
 * 且低频对账扫描（{@code OrderExpireTask}）会重新捞起仍未支付的过期订单——用对账兜底换取
 * 消费端不引入重试计数与毒消息治理。若后续要求秒级重试，再引入 PEL 重投即可。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpireConsumer {

    private final StringRedisTemplate redis;

    private final OrderExpireProperties properties;

    private final OrderExpireDelayQueue delayQueue;

    private final MallOrderMapper mallOrderMapper;

    private final OrderCancelService orderCancelService;

    /** 消费组是否已就绪（只在本实例内做一次性判断，避免每轮都发 XGROUP CREATE）。 */
    private volatile boolean groupReady = false;

    @Scheduled(fixedDelayString = "${stellar.order.expire.poll-interval-ms:1000}")
    public void scheduledConsume() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            consumeOnce();
        } catch (Exception e) {
            log.error("[OrderExpireConsumer] 消费异常", e);
        }
    }

    /**
     * 读取一批到期消息并处理。
     *
     * @return 本批实际取到的消息条数
     */
    public int consumeOnce() {
        ensureGroup();
        List<MapRecord<String, Object, Object>> records;
        try {
            records = redis.opsForStream().read(
                    Consumer.from(properties.getGroup(), consumerName()),
                    StreamReadOptions.empty().count(properties.getConsumeBatchSize()),
                    // lastConsumed() = XREADGROUP 的 '>'：只取从未投递过的新消息
                    StreamOffset.create(properties.getStreamKey(), ReadOffset.lastConsumed()));
        } catch (Exception e) {
            log.warn("[OrderExpireConsumer] 读取消息失败（消费组可能尚未就绪），下一轮重试: {}", e.getMessage());
            return 0;
        }
        if (records == null || records.isEmpty()) {
            return 0;
        }
        for (MapRecord<String, Object, Object> record : records) {
            processOne(record);
        }
        return records.size();
    }

    private void processOne(MapRecord<String, Object, Object> record) {
        Object rawOrderId = record.getValue().get("orderId");
        Long orderId = rawOrderId == null ? null : parseOrderId(String.valueOf(rawOrderId));
        if (orderId == null) {
            log.warn("[OrderExpireConsumer] 消息缺少合法 orderId，丢弃: id={}, body={}",
                    record.getId(), record.getValue());
            ack(record);
            return;
        }
        try {
            MallOrder order = mallOrderMapper.getById(orderId);
            if (order == null) {
                log.warn("[OrderExpireConsumer] 订单不存在，丢弃延迟消息: orderId={}", orderId);
                ack(record);
                return;
            }
            // 到期校验：延迟消息可能因配置调整提前送达，绝不能提前取消未到期订单
            if (!isExpired(order)) {
                delayQueue.scheduleAt(orderId, expireAtMillis(order));
                log.warn("[OrderExpireConsumer] 订单尚未到期，重新入队: orderId={}, createTime={}",
                        orderId, order.getCreateTime());
                ack(record);
                return;
            }
            // 状态守卫在 cancelExpiredOrder 内部（非 PENDING 直接跳过，不动库存/券/积分）
            boolean cancelled = orderCancelService.cancelExpiredOrder(order);
            if (cancelled) {
                log.info("[OrderExpireConsumer] 已按延迟消息取消过期订单: orderId={}", orderId);
            }
        } catch (Exception e) {
            log.error("[OrderExpireConsumer] 处理失败，交由对账扫描兜底: orderId={}", orderId, e);
        } finally {
            ack(record);
        }
    }

    private boolean isExpired(MallOrder order) {
        if (order.getCreateTime() == null) {
            // 没有创建时间无从判断，交给 cancelExpiredOrder 的状态守卫兜底
            return true;
        }
        return !order.getCreateTime().plusMinutes(properties.getDelayMinutes()).isAfter(LocalDateTime.now());
    }

    private long expireAtMillis(MallOrder order) {
        return order.getCreateTime().plusMinutes(properties.getDelayMinutes())
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private Long parseOrderId(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void ack(MapRecord<String, Object, Object> record) {
        try {
            redis.opsForStream().acknowledge(properties.getStreamKey(), properties.getGroup(), record.getId());
        } catch (Exception e) {
            log.error("[OrderExpireConsumer] ACK 失败: id={}", record.getId(), e);
        }
    }

    /**
     * 确保消费组存在。
     * <p>用底层 xGroupCreate(mkStream=true)：Stream 还没被投递过时一并创建，
     * 不依赖「先有订单写过 Stream」这个时序。已存在时 Redis 返回 BUSYGROUP，属正常路径。</p>
     */
    private void ensureGroup() {
        if (groupReady) {
            return;
        }
        synchronized (this) {
            if (groupReady) {
                return;
            }
            try {
                redis.execute((RedisCallback<Void>) connection -> {
                    connection.streamCommands().xGroupCreate(
                            properties.getStreamKey().getBytes(StandardCharsets.UTF_8),
                            properties.getGroup(), ReadOffset.from("0"), true);
                    return null;
                });
                groupReady = true;
                log.info("[OrderExpireConsumer] 消费组已就绪: stream={}, group={}",
                        properties.getStreamKey(), properties.getGroup());
            } catch (Exception e) {
                String msg = String.valueOf(e.getMessage());
                if (msg.contains("BUSYGROUP")) {
                    groupReady = true;
                } else {
                    log.warn("[OrderExpireConsumer] 消费组初始化失败，下一轮重试: {}", msg);
                }
            }
        }
    }

    /** 消费者名：实例级唯一，便于用 XPENDING 区分各实例的待确认消息。 */
    private String consumerName() {
        return properties.getGroup() + "-" + Integer.toHexString(System.identityHashCode(this));
    }
}
