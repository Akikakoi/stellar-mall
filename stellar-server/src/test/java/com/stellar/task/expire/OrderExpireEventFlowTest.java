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
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

    @Autowired(required = false)
    private OrderExpireConsumer consumer;

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

    private final List<Long> createdSkuIds = new ArrayList<>();
    private final List<Long> createdOrderIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long orderId : createdOrderIds) {
            jdbcTemplate.update("DELETE FROM stellar_mall_order_item WHERE order_id = ?", orderId);
            jdbcTemplate.update("DELETE FROM stellar_mall_order WHERE id = ?", orderId);
        }
        for (Long skuId : createdSkuIds) {
            jdbcTemplate.update("DELETE FROM stellar_stock_log WHERE sku_id = ?", skuId);
            jdbcTemplate.update("DELETE FROM stellar_sku WHERE id = ?", skuId);
        }
        createdOrderIds.clear();
        createdSkuIds.clear();
        redis.delete(properties.getDelayKey());
    }

    @Test
    @DisplayName("投递：到期订单被搬出延迟队列并进入 Stream")
    void pollAndPublish_movesDueOrderOutOfDelayQueue() {
        assertNotNull(poller, "RED失败：OrderExpireDelayPoller 未注册");
        long orderId = insertOrder("PENDING", LocalDateTime.now().minusMinutes(ORDER_EXPIRE_MINUTES + 5));
        delayQueue.scheduleAt(orderId, System.currentTimeMillis() - 1);

        int published = poller.pollAndPublish();

        assertTrue(published >= 1, "应至少投递本用例这一笔，实际 " + published);
        assertNull(delayScoreOf(orderId), "投递后应已从延迟队列移除");
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

        assertTrue(fetched >= 1, "应至少取到本用例这一条消息，实际 " + fetched);
        assertEquals("CANCELLED", orderStatusOf(orderId), "过期订单应被取消");
        assertEquals(10, stockOf(skuId), "取消后库存必须归还（7 + 3）");
        assertEquals(1, rollbackLogCountOf(skuId), "应写入一条入库回滚流水");
        assertEquals(0, pendingCount(), "处理完的消息必须已 ACK，不能残留在 PEL");
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
        assertEquals(0, pendingCount(), "业务上「无需处理」也算处理完成，必须 ACK");
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
                        + "(order_id, spu_id, sku_id, price, qty, create_time, create_user, update_time, update_user) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                orderId, TEST_SPU_ID, skuId, new BigDecimal("1.00"), qty,
                LocalDateTime.now(), 0L, LocalDateTime.now(), 0L);
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

    /** 消费组内未确认（未 ACK）的消息条数；组不存在时返回 -1。 */
    private int pendingCount() {
        try {
            return (int) redis.opsForStream()
                    .pending(properties.getStreamKey(), properties.getGroup())
                    .getTotalPendingMessages();
        } catch (Exception e) {
            return -1;
        }
    }
}
