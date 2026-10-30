package com.stellar.task.expire;

import com.stellar.TestRedisConfig;
import com.stellar.mapper.MallOrderMapper;
import com.stellar.service.OrderCancelService;
import com.stellar.service.SkuStockService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Range;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 订单过期「延迟队列 → Stream → 消费组」事件驱动链路端到端验证（跑在内嵌 Redis + H2 上）。
 *
 * <p>与轮询对账路径不同，这里验证的是消息链路本身的语义：到期任务被搬到 Stream、
 * 消费组按状态与到期时间决定是否取消、处理完必须 ACK，以及后台调度开关真的能关掉。</p>
 */
@SpringBootTest
@Import(TestRedisConfig.class)
class OrderExpireEventFlowTest {

    private static final int ORDER_EXPIRE_MINUTES = 15;
    private static final long TEST_USER_ID = 90002L;
    private static final long TEST_SPU_ID = 90002L;

    @Autowired(required = false)
    private OrderExpireDelayPoller poller;

    private OrderExpireConsumer consumer;

    @Autowired
    private MallOrderMapper mallOrderMapper;

    @Autowired
    private OrderCancelService orderCancelService;

    @SpyBean
    private SkuStockService skuStockService;

    @Autowired
    private OrderExpireDelayQueue delayQueue;

    @Autowired
    private OrderExpireProperties properties;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** ES 关闭后 ElasticsearchConfig 不再创建 ElasticsearchOperations，用 Mock 满足构造注入。 */
    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    @MockBean
    private co.elastic.clients.elasticsearch.ElasticsearchClient elasticsearchClient;

    private final List<Long> createdSkuIds = new ArrayList<>();
    private final List<Long> createdOrderIds = new ArrayList<>();
    private String originalDelayKey;
    private String originalStreamKey;
    private String originalGroup;
    private int originalDelayMinutes;
    private int originalPollBatchSize;
    private int originalConsumeBatchSize;
    private long originalClaimMinIdleMs;
    private String testDelayKey;
    private String testStreamKey;

    @BeforeEach
    void setUp() {
        originalDelayKey = properties.getDelayKey();
        originalStreamKey = properties.getStreamKey();
        originalGroup = properties.getGroup();
        originalDelayMinutes = properties.getDelayMinutes();
        originalPollBatchSize = properties.getPollBatchSize();
        originalConsumeBatchSize = properties.getConsumeBatchSize();
        originalClaimMinIdleMs = properties.getClaimMinIdleMs();
        String prefix = "test:order-expire:flow:" + UUID.randomUUID();
        testDelayKey = prefix + ":delay";
        testStreamKey = prefix + ":stream";
        properties.setDelayKey(testDelayKey);
        properties.setStreamKey(testStreamKey);
        properties.setGroup(prefix + ":group");
        properties.setDelayMinutes(ORDER_EXPIRE_MINUTES);
        properties.setPollBatchSize(200);
        properties.setConsumeBatchSize(50);
        properties.setClaimMinIdleMs(60_000);
        redis.delete(List.of(testDelayKey, testStreamKey));
        reset(skuStockService);
        consumer = newConsumer();
    }

    @AfterEach
    void cleanUp() {
        try {
            for (Long orderId : createdOrderIds) {
                jdbcTemplate.update("DELETE FROM stellar_mall_order_item WHERE order_id = ?", orderId);
                jdbcTemplate.update("DELETE FROM stellar_mall_order WHERE id = ?", orderId);
            }
            for (Long skuId : createdSkuIds) {
                jdbcTemplate.update("DELETE FROM stellar_stock_log WHERE sku_id = ?", skuId);
                jdbcTemplate.update("DELETE FROM stellar_sku WHERE id = ?", skuId);
            }
        } finally {
            createdOrderIds.clear();
            createdSkuIds.clear();
            try {
                redis.delete(List.of(testDelayKey, testStreamKey));
            } finally {
                properties.setDelayKey(originalDelayKey);
                properties.setStreamKey(originalStreamKey);
                properties.setGroup(originalGroup);
                properties.setDelayMinutes(originalDelayMinutes);
                properties.setPollBatchSize(originalPollBatchSize);
                properties.setConsumeBatchSize(originalConsumeBatchSize);
                properties.setClaimMinIdleMs(originalClaimMinIdleMs);
                reset(skuStockService);
            }
        }
    }

    @Test
    @DisplayName("投递：到期订单被搬出延迟队列并进入 Stream")
    void pollAndPublish_movesDueOrderOutOfDelayQueue() {
        assertNotNull(poller, "RED失败：OrderExpireDelayPoller 未注册");
        long orderId = insertOrder("PENDING", LocalDateTime.now().minusMinutes(ORDER_EXPIRE_MINUTES + 5));
        delayQueue.scheduleAt(orderId, System.currentTimeMillis() - 1);

        int published = poller.pollAndPublish();

        assertEquals(1, published);
        assertNull(delayScoreOf(orderId), "投递后应已从延迟队列移除");
        List<MapRecord<String, Object, Object>> records = streamRecords();
        assertEquals(1, records.size());
        assertEquals(String.valueOf(orderId), records.get(0).getValue().get("orderId"));
    }

    @Test
    @DisplayName("消费：过期未支付订单被取消、库存归还，且消息已 ACK")
    void consumeOnce_cancelsExpiredPendingOrderAndRestoresStock() {
        long skuId = insertSku(7);
        long orderId = insertOrder("PENDING", LocalDateTime.now().minusMinutes(ORDER_EXPIRE_MINUTES + 5));
        insertOrderItem(orderId, skuId, 3);
        delayQueue.scheduleAt(orderId, System.currentTimeMillis() - 1);
        poller.pollAndPublish();

        int fetched = consumer.consumeOnce();

        assertEquals(1, fetched);
        assertEquals("CANCELLED", orderStatusOf(orderId), "过期订单应被取消");
        assertEquals(10, stockOf(skuId), "取消后库存必须归还（7 + 3）");
        assertEquals(1, rollbackLogCountOf(skuId), "应写入一条入库回滚流水");
        assertEquals(0, pendingCount(), "处理完的消息必须已 ACK，不能残留在 PEL");
        assertStreamEmpty();
    }

    @Test
    @DisplayName("消费：扫描后已支付（CAS 失败）→ 不动库存，消息照样 ACK")
    void consumeOnce_skipsPaidOrderWithoutTouchingStock() {
        long skuId = insertSku(7);
        // 库里已是 PAID：延迟消息到达时订单已支付，取消必须被 CAS 拦住
        long orderId = insertOrder("PAID", LocalDateTime.now().minusMinutes(ORDER_EXPIRE_MINUTES + 5));
        insertOrderItem(orderId, skuId, 3);
        delayQueue.scheduleAt(orderId, System.currentTimeMillis() - 1);
        poller.pollAndPublish();

        consumer.consumeOnce();

        assertEquals("PAID", orderStatusOf(orderId), "已支付订单不能被改成 CANCELLED");
        assertEquals(7, stockOf(skuId), "CAS 失败时绝不能回滚库存");
        assertEquals(0, rollbackLogCountOf(skuId));
        assertEquals(0, pendingCount(), "业务上「无需处理」也算处理完成，必须 ACK");
        assertStreamEmpty();
    }

    @Test
    @DisplayName("消费：尚未到期的订单不取消，重新入队等待真正到期")
    void consumeOnce_requeuesNotYetExpiredOrder() {
        long skuId = insertSku(7);
        long orderId = insertOrder("PENDING", LocalDateTime.now());
        insertOrderItem(orderId, skuId, 3);
        // 故意投早（模拟配置调整/时钟偏差）
        delayQueue.scheduleAt(orderId, System.currentTimeMillis() - 1);
        poller.pollAndPublish();

        consumer.consumeOnce();

        assertEquals("PENDING", orderStatusOf(orderId), "未到期订单绝不能被提前取消");
        assertEquals(7, stockOf(skuId), "未取消时库存不应变化");
        Double requeued = delayScoreOf(orderId);
        assertNotNull(requeued, "应重新入队，等待真正到期");
        assertTrue(requeued > System.currentTimeMillis(), "重投的到期时间应在未来");
        assertEquals(0, pendingCount());
        assertStreamEmpty();
    }

    @Test
    @DisplayName("库存回滚失败时事务整体回滚并保留 PEL，下轮认领后只归还一次库存")
    void consumeOnce_retriesFailedStockRollbackWithoutLosingTransactionOrMessage() {
        long skuId = insertSku(7);
        long orderId = insertOrder("PENDING", LocalDateTime.now().minusMinutes(ORDER_EXPIRE_MINUTES + 5));
        insertOrderItem(orderId, skuId, 3);
        AtomicBoolean failOnce = new AtomicBoolean(true);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (failOnce.getAndSet(false)) {
                throw new IllegalStateException("test: stock rollback failed after writing");
            }
            return result;
        }).when(skuStockService).rollback(eq(skuId), eq(3), anyString());
        publishDueOrder(orderId);
        RecordId messageId = streamRecords().get(0).getId();

        assertEquals(1, consumer.consumeOnce());

        assertFalse(failOnce.get(), "故障必须发生在真实库存回滚调用中");
        assertEquals("PENDING", orderStatusOf(orderId), "库存异常必须回滚取消订单的 CAS");
        assertEquals(7, stockOf(skuId), "异常前真实写入的库存也必须回滚");
        assertEquals(0, rollbackLogCountOf(skuId));
        assertEquals(1, pendingCount());
        assertEquals(List.of(messageId), streamRecords().stream().map(MapRecord::getId).toList());
        assertEquals(0, consumer.consumeOnce(), "未达到最小 idle 时间不能立刻重试");
        assertEquals(1, pendingCount());
        verify(skuStockService, times(1)).rollback(eq(skuId), eq(3), anyString());

        properties.setClaimMinIdleMs(0);
        assertEquals(1, consumer.consumeOnce());

        assertEquals("CANCELLED", orderStatusOf(orderId));
        assertEquals(10, stockOf(skuId));
        assertEquals(1, rollbackLogCountOf(skuId));
        assertEquals(0, pendingCount());
        assertStreamEmpty();
        assertEquals(0, consumer.consumeOnce());
        verify(skuStockService, times(2)).rollback(eq(skuId), eq(3), anyString());
    }

    @Test
    @DisplayName("旧消费者读后未 ACK 的消息由新消费者实例认领处理")
    void consumeOnce_newInstanceClaimsMessagesAbandonedByOldConsumer() {
        long skuId = insertSku(7);
        long orderId = insertOrder("PENDING", LocalDateTime.now().minusMinutes(ORDER_EXPIRE_MINUTES + 5));
        insertOrderItem(orderId, skuId, 3);
        publishDueOrder(orderId);
        String oldConsumer = "departed-" + UUID.randomUUID();
        List<MapRecord<String, Object, Object>> abandoned = readWithoutAck(oldConsumer, 1);

        assertEquals(1, abandoned.size());
        assertEquals(1, pendingCount());
        PendingMessages pending = pendingMessages();
        assertEquals(oldConsumer, pending.get(0).getConsumerName());
        assertEquals(abandoned.get(0).getId(), pending.get(0).getId());
        assertEquals("PENDING", orderStatusOf(orderId));
        properties.setClaimMinIdleMs(0);
        OrderExpireConsumer replacement = newConsumer();

        assertEquals(1, replacement.consumeOnce());

        assertEquals("CANCELLED", orderStatusOf(orderId));
        assertEquals(10, stockOf(skuId));
        assertEquals(1, rollbackLogCountOf(skuId));
        assertEquals(0, pendingCount());
        assertStreamEmpty();
        assertEquals(0, replacement.consumeOnce());
    }

    @Test
    @DisplayName("同一订单的重复事件只取消一次、只写一条库存回滚流水")
    void consumeOnce_duplicateEventsRestoreStockOnlyOnce() {
        long skuId = insertSku(7);
        long orderId = insertOrder("PENDING", LocalDateTime.now().minusMinutes(ORDER_EXPIRE_MINUTES + 5));
        insertOrderItem(orderId, skuId, 3);
        publishDueOrder(orderId);
        publishDueOrder(orderId);
        List<MapRecord<String, Object, Object>> records = streamRecords();
        assertEquals(2, records.size());
        assertNotEquals(records.get(0).getId(), records.get(1).getId());

        assertEquals(2, consumer.consumeOnce());

        assertEquals("CANCELLED", orderStatusOf(orderId));
        assertEquals(10, stockOf(skuId));
        assertEquals(1, rollbackLogCountOf(skuId));
        verify(skuStockService, times(1)).rollback(eq(skuId), eq(3), anyString());
        assertEquals(0, pendingCount());
        assertStreamEmpty();
        assertEquals(0, consumer.consumeOnce());
        assertEquals(10, stockOf(skuId));
        assertEquals(1, rollbackLogCountOf(skuId));
    }

    @Test
    @DisplayName("同一消费者实例缓存的消费组被删除后能重建并继续处理")
    void consumeOnce_recoversDestroyedGroupOnSameInstance() {
        assertEquals(0, consumer.consumeOnce());
        assertEquals(0, pendingCount());
        assertEquals(Boolean.TRUE, redis.opsForStream().destroyGroup(testStreamKey, properties.getGroup()));
        long skuId = insertSku(7);
        long orderId = insertOrder("PENDING", LocalDateTime.now().minusMinutes(ORDER_EXPIRE_MINUTES + 5));
        insertOrderItem(orderId, skuId, 3);
        publishDueOrder(orderId);

        int consumed = consumer.consumeOnce();
        if (consumed == 0) {
            consumed += consumer.consumeOnce();
        }

        assertEquals(1, consumed, "NOGROUP 最迟下一轮应恢复，不能永久信任 groupReady");
        assertEquals("CANCELLED", orderStatusOf(orderId));
        assertEquals(10, stockOf(skuId));
        assertEquals(1, rollbackLogCountOf(skuId));
        assertEquals(0, pendingCount());
        assertStreamEmpty();
    }

    @Test
    @DisplayName("提前消息重排失败时保留 PEL，修复 ZSET 后认领重排成功")
    void consumeOnce_keepsPendingWhenEarlyMessageCannotBeRequeued() {
        long skuId = insertSku(7);
        long orderId = insertOrder("PENDING", LocalDateTime.now());
        insertOrderItem(orderId, skuId, 3);
        publishDueOrder(orderId);
        RecordId messageId = streamRecords().get(0).getId();
        redis.opsForValue().set(testDelayKey, "wrong-type");

        try {
            assertEquals(1, consumer.consumeOnce());
            assertEquals("PENDING", orderStatusOf(orderId));
            assertEquals(7, stockOf(skuId));
            assertEquals(0, rollbackLogCountOf(skuId));
            assertEquals(1, pendingCount(), "重排失败不能 ACK，否则提前消息会丢失");
            assertEquals(List.of(messageId), streamRecords().stream().map(MapRecord::getId).toList());
            assertEquals("wrong-type", redis.opsForValue().get(testDelayKey));
        } finally {
            redis.delete(testDelayKey);
        }

        properties.setClaimMinIdleMs(0);
        assertEquals(1, consumer.consumeOnce());

        assertEquals("PENDING", orderStatusOf(orderId));
        assertEquals(7, stockOf(skuId));
        assertEquals(0, rollbackLogCountOf(skuId));
        Double requeued = delayScoreOf(orderId);
        assertNotNull(requeued);
        assertTrue(requeued > System.currentTimeMillis());
        assertEquals(0, pendingCount());
        assertStreamEmpty();
    }

    @Test
    @DisplayName("PEL 首页消息尚未空闲时，仍能分页认领后页的空闲消息")
    void consumeOnce_claimsIdleMessagesBeyondRecentFirstPage() {
        long recentSkuId = insertSku(7);
        long idleSkuId = insertSku(7);
        long recentOrderId = insertOrder("PENDING", LocalDateTime.now().minusMinutes(ORDER_EXPIRE_MINUTES + 5));
        long idleOrderId = insertOrder("PENDING", LocalDateTime.now().minusMinutes(ORDER_EXPIRE_MINUTES + 5));
        insertOrderItem(recentOrderId, recentSkuId, 3);
        insertOrderItem(idleOrderId, idleSkuId, 3);
        publishDueOrder(recentOrderId);
        publishDueOrder(idleOrderId);
        String oldConsumer = "departed-" + UUID.randomUUID();
        List<MapRecord<String, Object, Object>> abandoned = readWithoutAck(oldConsumer, 2);
        assertEquals(2, abandoned.size());
        assertEquals(String.valueOf(recentOrderId), abandoned.get(0).getValue().get("orderId"));
        assertEquals(String.valueOf(idleOrderId), abandoned.get(1).getValue().get("orderId"));
        properties.setConsumeBatchSize(1);
        List<MapRecord<String, Object, Object>> aged = redis.opsForStream().claim(
                testStreamKey, properties.getGroup(), oldConsumer,
                XClaimOptions.minIdle(Duration.ZERO).ids(abandoned.get(1).getId()).idle(Duration.ofMinutes(2)));
        assertNotNull(aged);
        assertEquals(1, aged.size());
        PendingMessages before = pendingMessages();
        assertEquals(2, before.size());
        assertTrue(before.get(0).getElapsedTimeSinceLastDelivery().toMillis() < properties.getClaimMinIdleMs());
        assertTrue(before.get(1).getElapsedTimeSinceLastDelivery().toMillis() >= properties.getClaimMinIdleMs());

        int consumed = 0;
        for (int round = 0; round < 5 && "PENDING".equals(orderStatusOf(idleOrderId)); round++) {
            consumed += consumer.consumeOnce();
        }

        assertEquals(1, consumed, "不能每轮只检查 recent 的第一页而饿死后页");
        assertEquals("CANCELLED", orderStatusOf(idleOrderId));
        assertEquals(10, stockOf(idleSkuId));
        assertEquals(1, rollbackLogCountOf(idleSkuId));
        assertEquals("PENDING", orderStatusOf(recentOrderId));
        assertEquals(7, stockOf(recentSkuId));
        assertEquals(0, rollbackLogCountOf(recentSkuId));
        assertEquals(1, pendingCount());
        assertEquals(abandoned.get(0).getId(), pendingMessages().get(0).getId());
        assertEquals(List.of(abandoned.get(0).getId()), streamRecords().stream().map(MapRecord::getId).toList());

        properties.setClaimMinIdleMs(0);
        int recovered = 0;
        for (int round = 0; round < 3 && "PENDING".equals(orderStatusOf(recentOrderId)); round++) {
            recovered += consumer.consumeOnce();
        }
        assertEquals(1, recovered, "分页游标绕回后应处理剩余消息");
        assertEquals("CANCELLED", orderStatusOf(recentOrderId));
        assertEquals(10, stockOf(recentSkuId));
        assertEquals(1, rollbackLogCountOf(recentSkuId));
        assertEquals(0, pendingCount());
        assertStreamEmpty();
    }

    @Test
    @DisplayName("PEL 持续追加时，分页仍会绕回并重试已扫描的旧消息")
    void consumeOnce_revisitsOldPendingWhileNewMessagesKeepArriving() {
        long skuId = insertSku(7);
        long orderId = insertOrder("PENDING", LocalDateTime.now().minusMinutes(ORDER_EXPIRE_MINUTES + 5));
        insertOrderItem(orderId, skuId, 3);
        publishDueOrder(orderId);
        addEvent(Map.of("unexpected", "initial-backlog"));
        String oldConsumer = "departed-" + UUID.randomUUID();
        List<MapRecord<String, Object, Object>> abandoned = readWithoutAck(oldConsumer, 2);
        assertEquals(2, abandoned.size());
        RecordId oldest = abandoned.get(0).getId();
        properties.setConsumeBatchSize(1);
        assertEquals(0, consumer.consumeOnce());
        assertEquals(2, pendingCount());
        List<MapRecord<String, Object, Object>> aged = redis.opsForStream().claim(
                testStreamKey, properties.getGroup(), oldConsumer,
                XClaimOptions.minIdle(Duration.ZERO).ids(oldest).idle(Duration.ofMinutes(2)));
        assertNotNull(aged);
        assertEquals(1, aged.size());

        int appended = 0;
        int consumed = 0;
        for (int round = 0; round < 4 && "PENDING".equals(orderStatusOf(orderId)); round++) {
            addEvent(Map.of("unexpected", "new-backlog-" + round));
            List<MapRecord<String, Object, Object>> added = redis.opsForStream().read(
                    Consumer.from(properties.getGroup(), oldConsumer),
                    StreamReadOptions.empty().count(1),
                    StreamOffset.create(testStreamKey, ReadOffset.lastConsumed()));
            assertNotNull(added);
            assertEquals(1, added.size());
            appended++;
            consumed += consumer.consumeOnce();
        }

        assertEquals(1, consumed, "即使每轮都新增 PEL，也必须绕回处理已经空闲的旧消息");
        assertEquals("CANCELLED", orderStatusOf(orderId));
        assertEquals(10, stockOf(skuId));
        assertEquals(1, rollbackLogCountOf(skuId));
        verify(skuStockService, times(1)).rollback(eq(skuId), eq(3), anyString());
        assertEquals(appended + 1, pendingCount(), "近期消息不能被提前认领或误删");
        assertFalse(streamRecords().stream().anyMatch(record -> record.getId().equals(oldest)));

        properties.setClaimMinIdleMs(0);
        for (int round = 0; round < appended + 3 && pendingCount() > 0; round++) {
            consumer.consumeOnce();
        }
        assertEquals(0, pendingCount());
        assertStreamEmpty();
    }

    @Test
    @DisplayName("缺少或非法 orderId、订单不存在的消息均 ACK 并删除")
    void consumeOnce_deletesInvalidAndMissingOrderMessages() {
        addEvent(Map.of("unexpected", "value"));
        addEvent(Map.of("orderId", "not-a-number"));
        addEvent(Map.of("orderId", String.valueOf(Long.MIN_VALUE)));
        assertEquals(3, streamRecords().size());

        assertEquals(3, consumer.consumeOnce());

        assertEquals(0, pendingCount());
        assertStreamEmpty();
        assertEquals(0, consumer.consumeOnce());
    }

    @Test
    @DisplayName("后台调度开关关闭时，@Scheduled 入口不消费（测试间不被抢跑）")
    void scheduledEntryPoints_noOpWhenDisabled() {
        assertFalse(properties.isEnabled(), "测试环境应关闭后台调度开关");
        long skuId = insertSku(7);
        long orderId = insertOrder("PENDING", LocalDateTime.now().minusMinutes(ORDER_EXPIRE_MINUTES + 5));
        insertOrderItem(orderId, skuId, 3);
        delayQueue.scheduleAt(orderId, System.currentTimeMillis() - 1);
        poller.pollAndPublish();

        poller.scheduledPoll();
        consumer.scheduledConsume();

        assertEquals("PENDING", orderStatusOf(orderId), "开关关闭时后台调度不得处理消息");
        assertEquals(7, stockOf(skuId), "开关关闭时库存不应变化");
    }

    // ============================ 测试数据与查询辅助 ============================

    private long insertSku(int stock) {
        jdbcTemplate.update("INSERT INTO stellar_sku "
                        + "(spu_id, name, price, stock, version, status, create_time, create_user, update_time, update_user) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                TEST_SPU_ID, "过期事件用例SKU", new BigDecimal("1.00"), stock, 0, 1,
                LocalDateTime.now(), 0L, LocalDateTime.now(), 0L);
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM stellar_sku", Long.class);
        assertNotNull(id, "插入 SKU 后应能取到自增主键");
        createdSkuIds.add(id);
        return id;
    }

    private long insertOrder(String status, LocalDateTime createTime) {
        jdbcTemplate.update("INSERT INTO stellar_mall_order "
                        + "(order_no, user_id, total_amount, pay_amount, status, create_time, create_user, update_time, update_user) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "EXPIRE-UT-" + Long.toHexString(System.nanoTime()), TEST_USER_ID,
                new BigDecimal("3.00"), new BigDecimal("3.00"),
                status, createTime, 0L, LocalDateTime.now(), 0L);
        Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM stellar_mall_order", Long.class);
        assertNotNull(id, "插入订单后应能取到自增主键");
        createdOrderIds.add(id);
        return id;
    }

    private void insertOrderItem(long orderId, long skuId, int qty) {
        jdbcTemplate.update("INSERT INTO stellar_mall_order_item "
                        + "(order_id, spu_id, sku_id, spu_name, price, qty, subtotal) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                orderId, TEST_SPU_ID, skuId, "过期事件用例SPU快照", new BigDecimal("1.00"), qty,
                new BigDecimal("1.00").multiply(BigDecimal.valueOf(qty)));
    }

    private String orderStatusOf(long orderId) {
        return jdbcTemplate.queryForObject("SELECT status FROM stellar_mall_order WHERE id = ?", String.class, orderId);
    }

    private int stockOf(long skuId) {
        Integer stock = jdbcTemplate.queryForObject("SELECT stock FROM stellar_sku WHERE id = ?", Integer.class, skuId);
        return stock == null ? -1 : stock;
    }

    private int rollbackLogCountOf(long skuId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stellar_stock_log WHERE sku_id = ? AND type = 1", Integer.class, skuId);
        return count == null ? -1 : count;
    }

    private Double delayScoreOf(long orderId) {
        return redis.opsForZSet().score(properties.getDelayKey(), String.valueOf(orderId));
    }

    private OrderExpireConsumer newConsumer() {
        return new OrderExpireConsumer(redis, properties, delayQueue, mallOrderMapper, orderCancelService);
    }

    private void publishDueOrder(long orderId) {
        delayQueue.scheduleAt(orderId, System.currentTimeMillis() - 1);
        assertEquals(1, poller.pollAndPublish());
    }

    private void addEvent(Map<String, String> body) {
        assertNotNull(redis.opsForStream().add(StreamRecords.string(body).withStreamKey(testStreamKey)));
    }

    private List<MapRecord<String, Object, Object>> readWithoutAck(String consumerName, int count) {
        assertEquals("OK", redis.opsForStream().createGroup(testStreamKey, ReadOffset.from("0"), properties.getGroup()));
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                Consumer.from(properties.getGroup(), consumerName),
                StreamReadOptions.empty().count(count),
                StreamOffset.create(testStreamKey, ReadOffset.lastConsumed()));
        assertNotNull(records);
        return records;
    }

    private List<MapRecord<String, Object, Object>> streamRecords() {
        List<MapRecord<String, Object, Object>> records = redis.opsForStream()
                .range(testStreamKey, Range.unbounded());
        assertNotNull(records);
        return records;
    }

    private void assertStreamEmpty() {
        assertEquals(Long.valueOf(0), redis.opsForStream().size(testStreamKey));
        assertTrue(streamRecords().isEmpty(), "ACK 后必须 XDEL，而不是只清理 PEL");
    }

    private PendingMessages pendingMessages() {
        PendingMessages pending = redis.opsForStream()
                .pending(testStreamKey, properties.getGroup(), Range.unbounded(), 100);
        assertNotNull(pending);
        return pending;
    }

    private int pendingCount() {
        var summary = redis.opsForStream().pending(testStreamKey, properties.getGroup());
        assertNotNull(summary);
        return Math.toIntExact(summary.getTotalPendingMessages());
    }
}
