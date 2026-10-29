package com.stellar.task.expire;

import com.stellar.TestRedisConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

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
    private OrderExpireProperties properties;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private PlatformTransactionManager txManager;

    /** ES 关闭后 ElasticsearchConfig 不再创建 ElasticsearchOperations，用 Mock 满足构造注入。 */
    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    @AfterEach
    void cleanUp() {
        redis.delete(properties.getDelayKey());
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
    @DisplayName("只取出已到期成员，且取出后即从队列移除")
    void popDue_takesOnlyDueAndRemoves() {
        long now = System.currentTimeMillis();
        delayQueue.scheduleAt(ORDER_ID_DUE, now - 1_000);
        delayQueue.scheduleAt(ORDER_ID_FUTURE, now + 60_000);

        List<Long> due = delayQueue.popDue(now, 10);

        assertEquals(List.of(ORDER_ID_DUE), due, "只应取到已到期的那条");
        assertNull(scoreOf(ORDER_ID_DUE), "取出的成员必须从队列移除");
        assertNotNull(scoreOf(ORDER_ID_FUTURE), "未到期的成员不能被移除");
    }

    @Test
    @DisplayName("单次取出受批次上限约束，剩余留待下轮")
    void popDue_respectsBatchLimit() {
        long now = System.currentTimeMillis();
        delayQueue.scheduleAt(3001L, now - 1);
        delayQueue.scheduleAt(3002L, now - 1);
        delayQueue.scheduleAt(3003L, now - 1);

        List<Long> first = delayQueue.popDue(now, 2);
        List<Long> second = delayQueue.popDue(now, 2);

        assertEquals(2, first.size(), "首次应取满上限 2 条");
        assertEquals(1, second.size(), "剩余 1 条应留到下一轮");
    }

    private Double scoreOf(long orderId) {
        return redis.opsForZSet().score(properties.getDelayKey(), String.valueOf(orderId));
    }
}
