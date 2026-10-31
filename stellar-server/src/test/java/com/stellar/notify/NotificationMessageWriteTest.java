package com.stellar.notify;

import com.stellar.entity.MallOrder;
import com.stellar.service.NotificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 通知落库路径验证：发货通知要同时写「用户消息表」与「通知日志表」。
 *
 * <p>此前 H2 测试 schema 里这两张表都不存在，{@code sendUserMessage} 的异常被 catch 后只打日志，
 * 于是这条路径在测试中<b>静默失效</b>——用例全绿但一行都没写进去。这里补上表并真正断言落库内容。</p>
 *
 * <p>通知方法是 {@code @Async}，所以断言前需要等异步线程写完（带超时的轮询，不用 sleep 硬等）。</p>
 */
@SpringBootTest
class NotificationMessageWriteTest {

    private static final long USER_ID = 90003L;
    private static final long ORDER_ID = 90003L;
    private static final String ORDER_NO = "NOTICE-UT-90003";

    @Autowired(required = false)
    private NotificationService notificationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** ES 关闭后 ElasticsearchConfig 不再创建 ElasticsearchOperations，用 Mock 满足构造注入。 */
    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    @MockBean
    private co.elastic.clients.elasticsearch.ElasticsearchClient elasticsearchClient;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM stellar_user_message WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM stellar_notification_log WHERE user_id = ?", USER_ID);
    }

    @Test
    @DisplayName("发货通知：用户消息表与通知日志表各落一行")
    void shippedNotice_writesUserMessageAndNotificationLog() {
        assertNotNull(notificationService, "RED失败：NotificationService 未注册");
        MallOrder order = MallOrder.builder()
                .id(ORDER_ID)
                .orderNo(ORDER_NO)
                .userId(USER_ID)
                .build();

        notificationService.sendOrderShippedNotice(order);

        awaitAtLeastOne("SELECT COUNT(*) FROM stellar_user_message WHERE user_id = ?", USER_ID);
        awaitAtLeastOne("SELECT COUNT(*) FROM stellar_notification_log WHERE user_id = ?", USER_ID);

        // 用户消息：类型/关联订单/未读状态都要对得上
        assertEquals("ORDER_NOTICE",
                jdbcTemplate.queryForObject(
                        "SELECT type FROM stellar_user_message WHERE user_id = ?", String.class, USER_ID));
        assertEquals(ORDER_ID,
                jdbcTemplate.queryForObject(
                        "SELECT ref_id FROM stellar_user_message WHERE user_id = ?", Long.class, USER_ID));
        assertEquals(0,
                jdbcTemplate.queryForObject(
                        "SELECT is_read FROM stellar_user_message WHERE user_id = ?", Integer.class, USER_ID));
        String content = jdbcTemplate.queryForObject(
                "SELECT content FROM stellar_user_message WHERE user_id = ?", String.class, USER_ID);
        assertTrue(content != null && content.contains(ORDER_NO), "消息正文应带上订单号，实际: " + content);

        // 通知日志：类型/状态对约定值；渠道为 INBOX——项目未接入短信网关，
        // 发货通知实际触达渠道是站内信（user_message），日志按真实渠道记录
        assertEquals("ORDER_SHIPPED",
                jdbcTemplate.queryForObject(
                        "SELECT type FROM stellar_notification_log WHERE user_id = ?", String.class, USER_ID));
        assertEquals("INBOX",
                jdbcTemplate.queryForObject(
                        "SELECT channel FROM stellar_notification_log WHERE user_id = ?", String.class, USER_ID));
        assertEquals(1,
                jdbcTemplate.queryForObject(
                        "SELECT status FROM stellar_notification_log WHERE user_id = ?", Integer.class, USER_ID));
    }

    /** 等异步通知落库：带超时轮询，避免固定 sleep 既慢又不稳。 */
    private void awaitAtLeastOne(String countSql, Object... args) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            Integer count = jdbcTemplate.queryForObject(countSql, Integer.class, args);
            if (count != null && count >= 1) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        fail("等待异步通知落库超时: " + countSql);
    }
}
