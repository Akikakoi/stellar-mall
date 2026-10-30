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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 订单过期事件发布入口的开关语义验证。
 *
 * <p>延迟入队本身（含提交/回滚语义）已由 {@link OrderExpireDelayQueueTest} 覆盖，这里只验证
 * 发布入口这一层：开启时把订单交给延迟队列，关闭时完全不碰队列（退回对账兜底）。</p>
 */
@SpringBootTest
@Import(TestRedisConfig.class)
class OrderExpireEventPublisherTest {

    private static final long ORDER_ID = 5001L;

    @Autowired(required = false)
    private OrderExpireEventPublisher publisher;

    @Autowired
    private OrderExpireProperties properties;

    /** 用 Mock 顶替真实队列：既不写 Redis，也不会被后台轮询打扰。 */
    @MockBean
    private OrderExpireDelayQueue delayQueue;

    /** ES 关闭后 ElasticsearchConfig 不再创建 ElasticsearchOperations，用 Mock 满足构造注入。 */
    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    @MockBean
    private co.elastic.clients.elasticsearch.ElasticsearchClient elasticsearchClient;

    private boolean originalEnabled;

    @org.junit.jupiter.api.BeforeEach
    void rememberFlag() {
        // properties 是共享单例，用例内改动必须在结束后还原，否则污染同上下文的其它用例
        originalEnabled = properties.isEnabled();
    }

    @AfterEach
    void restoreFlag() {
        properties.setEnabled(originalEnabled);
    }

    @Test
    @DisplayName("开启事件驱动：下单后委托延迟队列，由队列负责提交后入队")
    void publishOrderCreated_delegatesWhenEnabled() {
        assertNotNull(publisher, "RED失败：OrderExpireEventPublisher 未注册");
        properties.setEnabled(true);

        publisher.publishOrderCreated(ORDER_ID);

        verify(delayQueue).scheduleAfterCommit(ORDER_ID);
    }

    @Test
    @DisplayName("关闭事件驱动：完全不碰延迟队列，退回对账兜底")
    void publishOrderCreated_skipsWhenDisabled() {
        properties.setEnabled(false);

        publisher.publishOrderCreated(ORDER_ID);

        verifyNoInteractions(delayQueue);
    }
}
