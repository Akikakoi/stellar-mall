package com.stellar.task.expire;

import com.stellar.TestRedisConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Range;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单过期延迟队列（Redis ZSET）行为验证，跑在测试环境内嵌 Redis 上。
 *
 * <p>守护三个语义点：</p>
 * <ol>
 *   <li><b>入队时机</b>：必须等事务提交后入队，订单回滚绝不能留下延迟任务
 *       （否则会去取消一笔根本不存在的订单）；</li>
 *   <li><b>到期判定</b>：只取 score &lt;= now 的成员，未到期的不能被提前取出；</li>
 *   <li><b>取出即移除</b>：多实例同时扫描不得重复取到同一条。</li>
 * </ol>
 */
@SpringBootTest
@Import(TestRedisConfig.class)
class OrderExpireDelayQueueTest {

    private static final long ORDER_ID_COMMIT = 1001L;
    private static final long ORDER_ID_ROLLBACK = 1002L;
    private static final long ORDER_ID_DUE = 2001L;
    private static final long ORDER_ID_FUTURE = 2002L;

    @Autowired(required = false)
    private OrderExpireDelayQueue delayQueue;

    @Autowired
    private OrderExpireDelayPoller poller;

    @Autowired
    private OrderExpireProperties properties;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private PlatformTransactionManager txManager;

    /** ES 关闭后 ElasticsearchConfig 不再创建 ElasticsearchOperations，用 Mock 满足构造注入。 */
    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    private String originalDelayKey;
    private String originalStreamKey;
    private int originalPollBatchSize;
    private String testDelayKey;
    private String testStreamKey;

    @BeforeEach
    void setUp() {
        originalDelayKey = properties.getDelayKey();
        originalStreamKey = properties.getStreamKey();
        originalPollBatchSize = properties.getPollBatchSize();
        String prefix = "test:order-expire:queue:" + UUID.randomUUID();
        testDelayKey = prefix + ":delay";
        testStreamKey = prefix + ":stream";
        properties.setDelayKey(testDelayKey);
        properties.setStreamKey(testStreamKey);
        redis.delete(List.of(testDelayKey, testStreamKey));
    }

    @AfterEach
    void cleanUp() {
        try {
            redis.delete(List.of(testDelayKey, testStreamKey));
        } finally {
            properties.setDelayKey(originalDelayKey);
            properties.setStreamKey(originalStreamKey);
            properties.setPollBatchSize(originalPollBatchSize);
        }
    }

    @Test
    @DisplayName("事务提交后才入队，提交前查不到")
    void scheduleAfterCommit_enqueuesOnlyAfterCommit() {
        assertNotNull(delayQueue, "RED失败：OrderExpireDelayQueue 未注册");

        new TransactionTemplate(txManager).execute(status -> {
            delayQueue.scheduleAfterCommit(ORDER_ID_COMMIT);
            assertNull(scoreOf(ORDER_ID_COMMIT), "事务提交前不应入队");
            return null;
        });

        assertNotNull(scoreOf(ORDER_ID_COMMIT), "事务提交后应入队");
    }

    @Test
    @DisplayName("事务回滚则不入队")
    void scheduleAfterCommit_skippedWhenRollback() {
        new TransactionTemplate(txManager).execute(status -> {
            delayQueue.scheduleAfterCommit(ORDER_ID_ROLLBACK);
            status.setRollbackOnly();
            return null;
        });

        assertNull(scoreOf(ORDER_ID_ROLLBACK), "回滚的订单不得留下延迟任务");
    }

    @Test
    @DisplayName("到期时间 = 当前 + 超时分钟数")
    void schedule_usesConfiguredDelay() {
        long before = System.currentTimeMillis();
        delayQueue.schedule(ORDER_ID_DUE);

        Double due = scoreOf(ORDER_ID_DUE);
        assertNotNull(due, "入队后应能查到 score");
        assertTrue(due >= before + properties.getDelayMinutes() * 60_000L - 1000,
                "到期时间应约为 当前 + " + properties.getDelayMinutes() + " 分钟，实际 score=" + due);
    }

    @Test
    @DisplayName("只投递已到期成员，成功写入 Stream 后从队列移除")
    void transferDue_transfersOnlyDueAndRemoves() {
        long now = System.currentTimeMillis();
        delayQueue.scheduleAt(ORDER_ID_DUE, now);
        delayQueue.scheduleAt(ORDER_ID_FUTURE, now + 60_000);

        assertEquals(1, delayQueue.transferDue(now, 10));

        assertEquals(List.of(ORDER_ID_DUE), streamOrderIds(), "score 等于 now 也应投递");
        assertNull(scoreOf(ORDER_ID_DUE), "投递成功的成员必须从队列移除");
        assertNotNull(scoreOf(ORDER_ID_FUTURE), "未到期的成员不能被移除");
        assertEquals(0, delayQueue.transferDue(now, 10));
        assertEquals(List.of(ORDER_ID_DUE), streamOrderIds(), "重复扫描不得重复投递");
    }

    @Test
    @DisplayName("单次投递受批次上限约束，剩余留待下轮")
    void transferDue_respectsBatchLimit() {
        long now = System.currentTimeMillis();
        delayQueue.scheduleAt(3001L, now - 1);
        delayQueue.scheduleAt(3002L, now - 1);
        delayQueue.scheduleAt(3003L, now - 1);

        assertEquals(2, delayQueue.transferDue(now, 2));
        assertEquals(List.of(3001L, 3002L), streamOrderIds());
        assertNull(scoreOf(3001L));
        assertNull(scoreOf(3002L));
        assertNotNull(scoreOf(3003L));

        assertEquals(1, delayQueue.transferDue(now, 2));
        assertEquals(List.of(3001L, 3002L, 3003L), streamOrderIds());
        assertNull(scoreOf(3003L));
        assertEquals(0, delayQueue.transferDue(now, 2));
    }

    @Test
    @DisplayName("Stream 类型错误时不丢失 ZSET，修复后仍能投递")
    void transferDue_keepsDueOrdersWhenStreamHasWrongType() {
        long now = System.currentTimeMillis();
        delayQueue.scheduleAt(3001L, now - 2);
        delayQueue.scheduleAt(3002L, now - 1);
        redis.opsForValue().set(testStreamKey, "wrong-type");

        try {
            assertThrows(RuntimeException.class, () -> delayQueue.transferDue(now, 10));
            assertEquals(Double.valueOf(now - 2), scoreOf(3001L));
            assertEquals(Double.valueOf(now - 1), scoreOf(3002L));
            assertEquals("wrong-type", redis.opsForValue().get(testStreamKey));
        } finally {
            redis.delete(testStreamKey);
        }

        assertEquals(2, delayQueue.transferDue(now, 10));
        assertEquals(List.of(3001L, 3002L), streamOrderIds());
        assertNull(scoreOf(3001L));
        assertNull(scoreOf(3002L));
    }

    @Test
    @DisplayName("零或负批次上限不搬运、不删除任何消息")
    void transferDue_nonPositiveLimitDoesNothing() {
        long now = System.currentTimeMillis();
        delayQueue.scheduleAt(ORDER_ID_DUE, now - 1);

        for (int limit : new int[]{0, -1, Integer.MIN_VALUE}) {
            assertEquals(0, delayQueue.transferDue(now, limit));
            assertEquals(Double.valueOf(now - 1), scoreOf(ORDER_ID_DUE));
            assertTrue(streamOrderIds().isEmpty());
        }

        assertEquals(1, delayQueue.transferDue(now, 1));
        assertEquals(List.of(ORDER_ID_DUE), streamOrderIds());
    }

    @Test
    @DisplayName("两个投递实例并发扫描时总发布数准确，Stream 无重复")
    void pollAndPublish_concurrentPublishersPublishEachOrderOnlyOnce() throws Exception {
        long now = System.currentTimeMillis();
        int count = 64;
        properties.setPollBatchSize(count);
        Set<Long> expected = new HashSet<>();
        for (long orderId = 4001L; orderId < 4001L + count; orderId++) {
            expected.add(orderId);
            delayQueue.scheduleAt(orderId, now - 1);
        }
        OrderExpireDelayQueue otherQueue = new OrderExpireDelayQueue(redis, properties);
        OrderExpireDelayPoller otherPoller = new OrderExpireDelayPoller(otherQueue, properties);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Integer> first = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return poller.pollAndPublish();
            });
            Future<Integer> second = executor.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, TimeUnit.SECONDS));
                return otherPoller.pollAndPublish();
            });
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            assertEquals(count, first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS));
            List<Long> published = streamOrderIds();
            assertEquals(count, published.size());
            assertEquals(expected, new HashSet<>(published));
            assertEquals(Long.valueOf(0), redis.opsForZSet().size(testDelayKey));
            assertEquals(0, delayQueue.transferDue(now, count));
            assertEquals(0, otherQueue.transferDue(now, count));
            assertEquals(published, streamOrderIds());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("未消费积压超过一万条时，投递器不得裁剪 Stream")
    void pollAndPublish_keepsEntireUnconsumedBacklog() {
        int count = 10_250;
        double dueAt = System.currentTimeMillis() - 1;
        Set<ZSetOperations.TypedTuple<String>> due = new LinkedHashSet<>();
        Set<Long> expected = new HashSet<>();
        for (long orderId = 50_000L; orderId < 50_000L + count; orderId++) {
            expected.add(orderId);
            due.add(new DefaultTypedTuple<>(String.valueOf(orderId), dueAt));
        }
        assertEquals(Long.valueOf(count), redis.opsForZSet().add(testDelayKey, due));
        properties.setPollBatchSize(512);

        int published = 0;
        int rounds = (count + properties.getPollBatchSize() - 1) / properties.getPollBatchSize();
        for (int i = 0; i < rounds; i++) {
            published += poller.pollAndPublish();
        }

        assertEquals(count, published);
        assertEquals(0, poller.pollAndPublish());
        List<Long> records = streamOrderIds();
        assertEquals(count, records.size());
        assertEquals(expected, new HashSet<>(records));
        assertEquals(Long.valueOf(0), redis.opsForZSet().size(testDelayKey));
    }

    private List<Long> streamOrderIds() {
        List<MapRecord<String, Object, Object>> records = redis.opsForStream()
                .range(testStreamKey, Range.unbounded());
        assertNotNull(records);
        return records.stream()
                .map(record -> Long.valueOf(String.valueOf(record.getValue().get("orderId"))))
                .toList();
    }

    private Double scoreOf(long orderId) {
        return redis.opsForZSet().score(properties.getDelayKey(), String.valueOf(orderId));
    }
}
