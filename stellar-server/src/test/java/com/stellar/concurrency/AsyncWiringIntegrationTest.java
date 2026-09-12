package com.stellar.concurrency;

import com.stellar.entity.EmailCode;
import com.stellar.entity.MallOrder;
import com.stellar.entity.NotificationLog;
import com.stellar.mapper.EmailCodeMapper;
import com.stellar.mapper.NotificationLogMapper;
import com.stellar.service.NotificationService;
import com.stellar.service.impl.MailDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

/**
 * {@code @Async} 接线验证——必须启动 Spring 上下文才能证明的两件事：
 *
 * <ol>
 *   <li><b>真的路由到了指定线程池</b>：{@code @Async("xxx")} 写错名字不会报错，只会静默
 *       换一个执行器（项目里 Executor Bean 多于一个时找不到唯一候选就退化为
 *       SimpleAsyncTaskExecutor）。这里用 spy 通知日志 Mapper 捕获执行线程名，
 *       断言它是 {@code stellar-notify-} 前缀，才能证明配置真正生效。</li>
 *   <li><b>发信真的在事务提交之后</b>：{@code afterCommit} 的注册一旦写错位置，
 *       行为会退化成「事务内就投递」，而这类退化在功能上不报错、只在 SMTP 抖动时暴露。</li>
 * </ol>
 */
@SpringBootTest
@TestPropertySource(properties = {
        // 必须关掉真实 SMTP：dev 配置里 stellar.mail.enabled=true，
        // 否则 javaMailSender.send() 会阻塞到连接超时（数十秒），
        // 「事务内不发信」「回滚不发信」两个用例在超时前断言，会变成假通过。
        "stellar.mail.enabled=false"
})
class AsyncWiringIntegrationTest {

    /** 捕获执行线程名：spy 掉日志落库，既能观测线程又不会真写库。 */
    @SpyBean
    private NotificationLogMapper notificationLogMapper;

    /** 避免真写 stellar_email_code（H2 测试 schema 里没有这张表）。 */
    @SpyBean
    private EmailCodeMapper emailCodeMapper;

    @Autowired(required = false)
    private MailDispatcher mailDispatcher;

    @Autowired(required = false)
    private NotificationService notificationService;

    @Autowired(required = false)
    private PlatformTransactionManager txManager;

    @Test
    @DisplayName("MailDispatcher 的 @Async 真的落在 notificationExecutor 上")
    void verifyCodeMailRunsOnNotificationExecutor() throws Exception {
        CountDownLatch executed = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        captureThread(executed, threadName);

        mailDispatcher.sendVerifyCodeMail("wiring@example.com", "LOGIN", "123456");

        assertTrue(executed.await(10, TimeUnit.SECONDS), "发信任务没有在预期时间内执行");
        assertTrue(threadName.get() != null && threadName.get().startsWith("stellar-notify-"),
                "@Async 未路由到 notificationExecutor，实际执行线程：" + threadName.get());
    }

    @Test
    @DisplayName("NotificationServiceImpl 的 @Async 与发信共用同一个池")
    void notificationAsyncMethodsShareNotificationExecutor() throws Exception {
        CountDownLatch executed = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        captureThread(executed, threadName);

        MallOrder order = new MallOrder();
        order.setId(1L);
        order.setOrderNo("SO-WIRING-TEST");
        order.setUserId(9L);
        notificationService.sendOrderShippedNotice(order);

        assertTrue(executed.await(10, TimeUnit.SECONDS), "发货通知没有在预期时间内执行");
        assertTrue(threadName.get() != null && threadName.get().startsWith("stellar-notify-"),
                "通知类 @Async 未路由到 notificationExecutor，实际执行线程：" + threadName.get());
    }

    @Test
    @DisplayName("发信在事务提交之后：事务内不投递，提交后才投递")
    void mailIsDispatchedOnlyAfterCommit() throws Exception {
        CountDownLatch executed = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        captureThread(executed, threadName);

        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.execute(status -> {
            notificationService.sendEmailCode("commit@example.com", "LOGIN");
            // 关键断言：事务还没提交，绝不能已经投递。
            // 即使实现退化成「事务内直接异步发」，300ms 也足够它跑完并暴露。
            sleepQuietly(300);
            assertEquals(1, executed.getCount(), "事务尚未提交就已经发信，afterCommit 逻辑未生效");
            return null;
        });

        assertTrue(executed.await(10, TimeUnit.SECONDS), "事务提交后应触发发信，但没有发生");
        assertTrue(threadName.get() != null && threadName.get().startsWith("stellar-notify-"),
                "发信线程应为 notificationExecutor，实际：" + threadName.get());
    }

    @Test
    @DisplayName("事务回滚时不发信：不会投递一条数据库里不存在的验证码")
    void mailIsNotDispatchedOnRollback() throws Exception {
        CountDownLatch executed = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();
        captureThread(executed, threadName);

        TransactionTemplate tx = new TransactionTemplate(txManager);
        try {
            tx.execute(status -> {
                notificationService.sendEmailCode("rollback@example.com", "LOGIN");
                throw new IllegalStateException("模拟后续步骤失败，触发回滚");
            });
        } catch (Exception expected) {
            // 预期异常
        }

        sleepQuietly(500);
        assertEquals(1, executed.getCount(), "事务回滚后仍然发信了");
    }

    // ======================== 辅助 ========================

    private void captureThread(CountDownLatch executed, AtomicReference<String> threadName) {
        doAnswer(inv -> {
            threadName.set(Thread.currentThread().getName());
            executed.countDown();
            return 1;
        }).when(notificationLogMapper).insert(any(NotificationLog.class));

        // stellar_email_code 不在 H2 测试 schema 里：必须打桩，否则 sendEmailCode 会在
        // insert 处就抛 BadSqlGrammar，后面的 afterCommit 注册逻辑根本执行不到，
        // 「事务内不发信 / 回滚不发信」两个用例会变成假通过。
        doReturn(1).when(emailCodeMapper).insert(any(EmailCode.class));
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
