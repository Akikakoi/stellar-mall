package com.stellar.concurrency;

import com.github.benmanes.caffeine.cache.Cache;
import com.stellar.config.AsyncExecutorConfig;
import com.stellar.config.BaseContextTaskDecorator;
import com.stellar.context.BaseContext;
import com.stellar.elasticsearch.service.SpuSearchService;
import com.stellar.elasticsearch.service.SynonymEngine;
import com.stellar.elasticsearch.sync.SpuEsSyncService;
import com.stellar.mapper.SpuMapper;
import com.stellar.ragsync.scheduler.RagSyncScheduler;
import com.stellar.ragsync.service.RagSyncService;
import com.stellar.service.OrderService;
import com.stellar.service.impl.MailDispatcher;
import com.stellar.service.impl.NotificationServiceImpl;
import com.stellar.service.impl.UserBehaviorServiceImpl;
import com.stellar.task.OrderExpireTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JUC 并发改造的验证：线程池配置、跨线程上下文、本地缓存策略、定时任务重入保护。
 *
 * <p>纯单元测试，不启动 Spring 上下文，因此会随 CI 的 unit-only profile 一起跑
 * （{@code @Async} 的路由与事务提交时序由 AsyncWiringIntegrationTest 单独验证）。</p>
 */
class JucConcurrencyUnitTest {

    private final AsyncExecutorConfig config = new AsyncExecutorConfig();

    // ==================== 1. 线程池配置 ====================

    @Test
    @DisplayName("三个自建异步池：容量、拒绝策略与线程名前缀都符合设计")
    void executorsAreConfiguredAsDesigned() {
        assertPool(config.notificationExecutor(), "stellar-notify-", 2, 8, 1000);
        assertPool(config.esSyncExecutor(), "stellar-essync-", 2, 8, 1000);
        assertPool(config.aggregateExecutor(), "stellar-aggr-", 8, 8, 128);
    }

    private void assertPool(Executor executor, String prefix, int core, int max, int queue) {
        ThreadPoolTaskExecutor pool = assertInstanceOf(ThreadPoolTaskExecutor.class, executor);
        assertEquals(core, pool.getCorePoolSize(), prefix + " corePoolSize");
        assertEquals(max, pool.getMaxPoolSize(), prefix + " maxPoolSize");
        assertEquals(queue, pool.getQueueCapacity(), prefix + " queueCapacity");

        // 这三个池都不能丢任务：必须 CallerRuns（能丢的只有埋点池，单独用 Discard）
        assertInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class,
                pool.getThreadPoolExecutor().getRejectedExecutionHandler(),
                prefix + " 拒绝策略应为 CallerRunsPolicy");

        // 线程名前缀要真的落到线程上（线程名是排障时唯一的抓手）
        AtomicReference<String> actual = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        executor.execute(() -> {
            actual.set(Thread.currentThread().getName());
            latch.countDown();
        });
        awaitLatch(latch, prefix + " 任务");
        assertTrue(actual.get() != null && actual.get().startsWith(prefix),
                "线程名应以 " + prefix + " 开头，实际为 " + actual.get());

        pool.shutdown();
    }

    // ==================== 2. 跨线程上下文 ====================

    @Test
    @DisplayName("上下文传递：池线程读得到提交线程的快照，且上一个任务不会泄漏给下一个")
    void taskDecoratorTransfersAndClearsContext() throws Exception {
        // core=1 保证两个任务落在同一个线程上，才能验证「用完即清」
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setCorePoolSize(1);
        pool.setMaxPoolSize(1);
        pool.setQueueCapacity(8);
        pool.setThreadNamePrefix("verify-ctx-");
        pool.setTaskDecorator(new BaseContextTaskDecorator());
        pool.initialize();

        AtomicReference<Long> seenByFirst = new AtomicReference<>();
        AtomicReference<Long> seenBySecond = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(2);

        BaseContext.setCurrentId(42L);
        pool.execute(() -> {
            seenByFirst.set(BaseContext.getCurrentId());
            done.countDown();
        });
        // 模拟请求线程结束（拦截器 afterCompletion 会做同样的事）
        BaseContext.remove();
        pool.execute(() -> {
            seenBySecond.set(BaseContext.getCurrentId());
            done.countDown();
        });

        awaitLatch(done, "两个池任务");
        assertEquals(Long.valueOf(42L), seenByFirst.get(),
                "池线程应拿到提交线程的 BaseContext 快照");
        assertNull(seenBySecond.get(),
                "上一个任务结束必须清理上下文，否则会把前一个用户的 ID 泄漏给下一个任务");
        pool.shutdown();
    }

    @Test
    @DisplayName("上下文传递：decorator 本身也保证执行完清理")
    void decoratorClearsContextEvenOnException() {
        BaseContextTaskDecorator decorator = new BaseContextTaskDecorator();
        BaseContext.setCurrentId(7L);
        Runnable decorated = decorator.decorate(() -> {
            assertEquals(Long.valueOf(7L), BaseContext.getCurrentId(), "任务内应看到快照");
            throw new IllegalStateException("模拟任务失败");
        });
        BaseContext.remove();

        // 任务抛异常也不能把上下文留在工作线程上
        try {
            decorated.run();
        } catch (IllegalStateException expected) {
            // 预期：异常照常向外抛，但上下文已清理
        }
        assertNull(BaseContext.getCurrentId(), "任务异常时也必须清理 ThreadLocal");
    }

    // ==================== 3. 本地缓存策略 ====================

    @Test
    @DisplayName("搜索缓存：容量上限与写入过期由 Caffeine 管理，且上限真正生效")
    @SuppressWarnings("unchecked")
    void searchCachesUseCaffeinePolicies() throws Exception {
        SpuSearchService service = new SpuSearchService(
                mock(ElasticsearchOperations.class),
                mock(SpuMapper.class),
                mock(SynonymEngine.class));

        Cache<String, ?> resultCache = field(service, "resultCache");
        assertEquals(1024, resultCache.policy().eviction().orElseThrow().getMaximum(),
                "结果缓存容量上限");
        assertEquals(60, resultCache.policy().expireAfterWrite().orElseThrow()
                .getExpiresAfter(TimeUnit.SECONDS), "结果缓存写入后 60s 过期");

        Cache<String, Object> embedCache = field(service, "embedCache");
        assertEquals(512, embedCache.policy().eviction().orElseThrow().getMaximum(),
                "向量缓存容量上限");

        // 关键回归点：原实现是「超过上限整体 clear」，会连热 key 一起清空；
        // Caffeine 应该是逐步淘汰。写入 600 条后条数被限制在 512 以内，而不是归零。
        for (int i = 0; i < 600; i++) {
            embedCache.put("word-" + i, new double[]{i});
        }
        embedCache.cleanUp();
        long size = embedCache.estimatedSize();
        assertTrue(size <= 512, "缓存条数应被限制在 512 以内，实际 " + size);
        assertTrue(size > 0, "淘汰不应把整个缓存清空（原实现清空是缺陷）");
    }

    // ==================== 4. @Async 接线规范 ====================

    @Test
    @DisplayName("@Async 全部显式指定了已知执行器名（漏写会静默退化成 SimpleAsyncTaskExecutor）")
    void everyAsyncDeclaresKnownExecutor() {
        Set<String> known = Set.of(
                AsyncExecutorConfig.NOTIFICATION_EXECUTOR,
                AsyncExecutorConfig.ES_SYNC_EXECUTOR,
                AsyncExecutorConfig.AGGREGATE_EXECUTOR,
                "behaviorExecutor");

        List<Class<?>> ownerClasses = List.of(
                NotificationServiceImpl.class,
                UserBehaviorServiceImpl.class,
                SpuEsSyncService.class,
                MailDispatcher.class);

        List<String> problems = new ArrayList<>();
        for (Class<?> owner : ownerClasses) {
            for (Method method : owner.getDeclaredMethods()) {
                Async async = method.getAnnotation(Async.class);
                if (async == null) {
                    continue;
                }
                String executor = async.value();
                String where = owner.getSimpleName() + "#" + method.getName();
                if (executor == null || executor.isBlank()) {
                    problems.add(where + " 未指定执行器名");
                } else if (!known.contains(executor)) {
                    problems.add(where + " 指向未知执行器：" + executor);
                }
            }
        }
        assertTrue(problems.isEmpty(), "存在不规范的 @Async：" + problems);
    }

    // ==================== 5. 定时任务重入保护 ====================

    @Test
    @DisplayName("订单过期任务：上一轮未结束时重入被跳过")
    void orderExpireTaskSkipsReentrantRun() throws Exception {
        OrderService orderService = mock(OrderService.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(orderService.cancelExpiredOrders(anyInt())).thenAnswer(inv -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return 3;
        });

        OrderExpireTask task = new OrderExpireTask(orderService);
        Thread first = new Thread(task::cancelExpiredOrders, "first-run");
        first.start();
        awaitLatch(started, "第一轮进入");
        assertTrue(first.isAlive(), "第一轮应仍处于执行中（被阻塞在业务调用里）");

        // 第一轮仍在执行，此时的重入必须被 CAS 挡住
        task.cancelExpiredOrders();

        release.countDown();
        first.join(5000);
        verify(orderService, times(1)).cancelExpiredOrders(anyInt());
    }

    @Test
    @DisplayName("RAG 同步任务：上一轮未结束时重入被跳过")
    void ragSyncSchedulerSkipsReentrantRun() throws Exception {
        RagSyncService syncService = mock(RagSyncService.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(syncService.processPendingBatch(anyInt())).thenAnswer(inv -> {
            started.countDown();
            release.await(5, TimeUnit.SECONDS);
            return 1;
        });

        RagSyncScheduler scheduler = new RagSyncScheduler(syncService);
        Thread first = new Thread(scheduler::processOutbox, "first-run");
        first.start();
        awaitLatch(started, "第一轮进入");

        scheduler.processOutbox();

        release.countDown();
        first.join(5000);
        verify(syncService, times(1)).processPendingBatch(anyInt());
    }

    @Test
    @DisplayName("重入保护不粘死：上一轮正常结束后，下一轮仍能执行")
    void reentrancyGuardReleasesAfterCompletion() {
        OrderService orderService = mock(OrderService.class);
        when(orderService.cancelExpiredOrders(anyInt())).thenReturn(2);

        OrderExpireTask task = new OrderExpireTask(orderService);
        task.cancelExpiredOrders();
        task.cancelExpiredOrders();

        verify(orderService, times(2)).cancelExpiredOrders(anyInt());
    }

    // ==================== 辅助 ====================

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(target);
    }

    private static void awaitLatch(CountDownLatch latch, String what) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                fail("等待「" + what + "」超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("等待「" + what + "」被中断");
        }
    }
}
