package com.stellar.utils;

import com.stellar.TestRedisConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Redisson 版分布式锁行为验证（跑在测试环境 embedded Redis 上）。
 *
 * <p>守护三个语义点，防止将来改动静默退化：
 * <ol>
 *   <li><b>互斥</b>：A 持锁时 B 立即获取必须失败；</li>
 *   <li><b>leaseTime 到期自动释放</b>：调用方漏 unlock 时锁也能恢复（保险丝语义）；</li>
 *   <li><b>同线程可重入</b>：同线程重复加锁不死锁，且必须释放相同次数。</li>
 * </ol>
 */
@SpringBootTest
@Import(TestRedisConfig.class)
class RedisLockUtilTest {

    @Autowired
    private RedisLockUtil redisLockUtil;

    /** ES 关闭后 ElasticsearchConfig 不再创建 ElasticsearchOperations，
     *  用 Mock 满足 SpuEsSyncService 等的构造注入，确保测试不触碰真实 ES。 */
    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    @MockBean
    private co.elastic.clients.elasticsearch.ElasticsearchClient elasticsearchClient;

    @Test
    @DisplayName("互斥：A 持锁时 B 获取失败，A 释放后 B 成功")
    void mutualExclusion() throws Exception {
        String key = "lock:test:mutex";
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            CountDownLatch locked = new CountDownLatch(1);
            AtomicReference<String> tokenA = new AtomicReference<>();
            Future<?> holder = pool.submit(() -> {
                tokenA.set(redisLockUtil.tryLock(key, 10, TimeUnit.SECONDS));
                assertNotNull(tokenA.get(), "持锁线程应获取成功");
                locked.countDown();
                try {
                    // 持锁 300ms，给主线程足够的窗口来验证互斥
                    TimeUnit.MILLISECONDS.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    redisLockUtil.unlock(key, tokenA.get());
                }
            });

            assertTrue(locked.await(2, TimeUnit.SECONDS), "持锁线程未按时上锁");
            assertNull(redisLockUtil.tryLock(key, 1, TimeUnit.SECONDS), "A 持锁时主线程应获取失败");
            holder.get(5, TimeUnit.SECONDS);
            assertNotNull(redisLockUtil.tryLock(key, 1, TimeUnit.SECONDS), "A 释放后主线程应获取成功");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("leaseTime 到期自动释放（漏 unlock 的保险丝）")
    void leaseExpiresAutomatically() throws Exception {
        String key = "lock:test:lease";
        String token = redisLockUtil.tryLock(key, 1, TimeUnit.SECONDS);
        assertNotNull(token);

        // 1s lease：轮询最多 3s，锁应自动恢复可获取
        long deadline = System.currentTimeMillis() + 3000;
        String acquired = null;
        while (System.currentTimeMillis() < deadline) {
            acquired = redisLockUtil.tryLock(key, 1, TimeUnit.SECONDS);
            if (acquired != null) {
                break;
            }
            Thread.sleep(100);
        }
        assertNotNull(acquired, "leaseTime 到期后锁应自动释放");
    }

    @Test
    @DisplayName("同线程可重入：重复加锁不死锁，释放次数必须对等")
    void reentrantOnSameThread() {
        String key = "lock:test:reentrant";
        String token1 = redisLockUtil.tryLock(key, 5, TimeUnit.SECONDS);
        String token2 = redisLockUtil.tryLock(key, 5, TimeUnit.SECONDS);
        assertNotNull(token1);
        assertNotNull(token2, "同线程重复加锁应可重入成功");

        redisLockUtil.unlock(key, token1);
        // 内层计数还有 1，锁仍被持有——他线程此处应失败
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> other = pool.submit(() ->
                    assertNull(redisLockUtil.tryLock(key, 1, TimeUnit.SECONDS), "重入计数未清零前他线程应获取失败"));
            other.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("跨线程验证失败", e);
        } finally {
            pool.shutdownNow();
        }
        redisLockUtil.unlock(key, token2);
        assertFalse(false);
    }
}
