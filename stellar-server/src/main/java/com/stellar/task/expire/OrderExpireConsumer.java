package com.stellar.task.expire;

import com.stellar.entity.MallOrder;
import com.stellar.mapper.MallOrderMapper;
import com.stellar.service.OrderCancelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpireConsumer {

    // 此 Stream 专属于一个消费组，只删除已确认记录，不能按长度裁掉未处理消息。
    private static final DefaultRedisScript<Long> ACK_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "local acked = redis.call('XACK', KEYS[1], ARGV[1], ARGV[2]) "
                    + "if acked == 1 then redis.call('XDEL', KEYS[1], ARGV[2]) end "
                    + "return acked", Long.class);

    private final StringRedisTemplate redis;
    private final OrderExpireProperties properties;
    private final OrderExpireDelayQueue delayQueue;
    private final MallOrderMapper mallOrderMapper;
    private final OrderCancelService orderCancelService;
    private final String consumerName = UUID.randomUUID().toString();

    private boolean groupReady;
    private String pendingCursor;
    private String pendingUpperBound;

    @Scheduled(fixedDelayString = "${stellar.order.expire.poll-interval-ms:1000}")
    public void scheduledConsume() {
        if (!properties.isEnabled()) {
            return;
        }
        consumeOnce();
    }

    public synchronized int consumeOnce() {
        try {
            ensureGroup();
            int fetched = reclaimPending();
            List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                    Consumer.from(properties.getGroup(), consumerName),
                    StreamReadOptions.empty().count(properties.getConsumeBatchSize()),
                    StreamOffset.create(properties.getStreamKey(), ReadOffset.lastConsumed()));
            if (records != null) {
                for (MapRecord<String, Object, Object> record : records) {
                    processOne(record);
                }
                fetched += records.size();
            }
            return fetched;
        } catch (Exception e) {
            resetGroupIfMissing(e);
            log.error("[OrderExpireConsumer] 消费异常，下一轮重试", e);
            return 0;
        }
    }

    private int reclaimPending() {
        if (pendingUpperBound == null) {
            PendingMessagesSummary summary = redis.opsForStream()
                    .pending(properties.getStreamKey(), properties.getGroup());
            if (summary.getTotalPendingMessages() == 0) {
                return 0;
            }
            // 固定本轮扫描上界，持续流入的新消息不能阻止游标绕回旧消息。
            pendingUpperBound = summary.maxMessageId();
        }
        Range<String> range = Range.closed(pendingCursor == null ? "-" : pendingCursor, pendingUpperBound);
        // Redis 5 不支持 XPENDING 排他下界，多取一条并跳过已扫描的边界记录。
        PendingMessages pending = redis.opsForStream().pending(properties.getStreamKey(),
                properties.getGroup(), range, (long) properties.getConsumeBatchSize() + 1);
        Duration minIdle = Duration.ofMillis(properties.getClaimMinIdleMs());
        List<RecordId> ids = new ArrayList<>();
        String lastScanned = null;
        int scanned = 0;
        for (PendingMessage message : pending) {
            if (message.getIdAsString().equals(pendingCursor)) {
                continue;
            }
            if (scanned == properties.getConsumeBatchSize()) {
                break;
            }
            lastScanned = message.getIdAsString();
            scanned++;
            if (message.getElapsedTimeSinceLastDelivery().compareTo(minIdle) >= 0) {
                ids.add(message.getId());
            }
        }
        if (scanned < properties.getConsumeBatchSize() || pendingUpperBound.equals(lastScanned)) {
            pendingCursor = null;
            pendingUpperBound = null;
        } else {
            pendingCursor = lastScanned;
        }
        if (ids.isEmpty()) {
            return 0;
        }
        List<MapRecord<String, Object, Object>> claimed = redis.opsForStream().claim(
                properties.getStreamKey(), properties.getGroup(), consumerName, minIdle,
                ids.toArray(RecordId[]::new));
        if (claimed == null) {
            return 0;
        }
        for (MapRecord<String, Object, Object> record : claimed) {
            processOne(record);
        }
        return claimed.size();
    }

    private void processOne(MapRecord<String, Object, Object> record) {
        Object rawOrderId = record.getValue().get("orderId");
        Long orderId = rawOrderId == null ? null : parseOrderId(String.valueOf(rawOrderId));
        try {
            if (orderId == null) {
                log.warn("[OrderExpireConsumer] 消息缺少合法 orderId，丢弃: id={}", record.getId());
            } else {
                MallOrder order = mallOrderMapper.getById(orderId);
                if (order == null) {
                    log.warn("[OrderExpireConsumer] 订单不存在，丢弃延迟消息: orderId={}", orderId);
                } else if (!isExpired(order)) {
                    delayQueue.scheduleAtOrThrow(orderId, expireAtMillis(order));
                } else if (orderCancelService.cancelExpiredOrder(order)) {
                    log.info("[OrderExpireConsumer] 已按延迟消息取消过期订单: orderId={}", orderId);
                }
            }
            redis.execute(ACK_AND_DELETE_SCRIPT, List.of(properties.getStreamKey()),
                    properties.getGroup(), record.getId().getValue());
        } catch (Exception e) {
            resetGroupIfMissing(e);
            log.error("[OrderExpireConsumer] 处理失败，保留待确认消息重试: orderId={}, messageId={}",
                    orderId, record.getId(), e);
        }
    }

    private boolean isExpired(MallOrder order) {
        if (order.getCreateTime() == null) {
            return true;
        }
        return !order.getCreateTime().plusMinutes(properties.getDelayMinutes()).isAfter(LocalDateTime.now());
    }

    private long expireAtMillis(MallOrder order) {
        return order.getCreateTime().plusMinutes(properties.getDelayMinutes())
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private Long parseOrderId(String raw) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void ensureGroup() {
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
        } catch (Exception e) {
            if (!hasRedisError(e, "BUSYGROUP")) {
                throw e;
            }
        }
        groupReady = true;
    }

    private void resetGroupIfMissing(Exception error) {
        if (hasRedisError(error, "NOGROUP")) {
            groupReady = false;
            pendingCursor = null;
            pendingUpperBound = null;
        }
    }

    private boolean hasRedisError(Throwable error, String code) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains(code)) {
                return true;
            }
        }
        return false;
    }
}
