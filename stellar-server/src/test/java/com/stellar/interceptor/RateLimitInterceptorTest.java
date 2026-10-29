package com.stellar.interceptor;

import com.stellar.TestRedisConfig;
import com.stellar.annotation.RateLimit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 限流拦截器行为验证（跑在测试环境 embedded Redis 上）。
 *
 * <p>重点守护两个容易静默退化的点：
 * <ol>
 *   <li><b>计数上限真实生效</b>：Lua 脚本写错（如 ZADD 先于判断）会导致计数虚高/虚低；</li>
 *   <li><b>滑动窗口真的会过期恢复</b>：如果误用固定窗口 key（带 windowBucket 后缀）或
 *       TTL 没设置，被限流后的 IP 将永远请求不进来。</li>
 * </ol>
 *
 * <p>注意：Redis 不可用时拦截器会降级放行——因此若 embedded Redis 起不来，
 * 这里会以「全部放行」的假通过暴露不了问题；断言里必须包含「被拦截」的正向用例。
 */
@SpringBootTest
@Import(TestRedisConfig.class)
@TestPropertySource(properties = {
        // 关真实 SMTP，理由同 AsyncWiringIntegrationTest：dev 下会阻塞到连接超时
        "stellar.mail.enabled=false"
})
class RateLimitInterceptorTest {

    @Autowired
    private RateLimitInterceptor interceptor;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** ES 关闭后 ElasticsearchConfig 不再创建 ElasticsearchOperations，
     *  用 Mock 满足 SpuEsSyncService 等的构造注入，确保测试不触碰真实 ES。 */
    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    /** 测试用最小控制器：不同测试用不同 key，互不串桶。 */
    static class DummyController {
        @RateLimit(key = "test-cap", maxRequests = 3, windowSeconds = 60)
        public void capped() {
        }

        @RateLimit(key = "test-recover", maxRequests = 1, windowSeconds = 1)
        public void recover() {
        }
    }

    @AfterEach
    void cleanRedis() {
        Set<String> keys = stringRedisTemplate.keys("rate_limit:test-*:*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    private boolean preHandle(String key, String ip) throws Exception {
        DummyController bean = new DummyController();
        Method method = DummyController.class.getDeclaredMethod(key.equals("test-cap") ? "capped" : "recover");
        HandlerMethod handlerMethod = new HandlerMethod(bean, method);

        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("POST", "/test");
        request.setRemoteAddr(ip);
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();

        return interceptor.preHandle(request, response, handlerMethod);
    }

    @Test
    @DisplayName("窗口内第 maxRequests+1 次请求被拦截")
    void blocksWhenOverCap() throws Exception {
        String ip = "10.1.1.1";
        assertTrue(preHandle("test-cap", ip), "第 1 次应放行");
        assertTrue(preHandle("test-cap", ip), "第 2 次应放行");
        assertTrue(preHandle("test-cap", ip), "第 3 次应放行");
        assertFalse(preHandle("test-cap", ip), "第 4 次应被限流");
        // 同 key 不同 IP 互不影响
        assertTrue(preHandle("test-cap", "10.1.1.2"), "另一 IP 不应被连坐");
    }

    @Test
    @DisplayName("滑动窗口过期后恢复放行（TTL 真实生效）")
    void recoversAfterWindowExpires() throws Exception {
        String ip = "10.2.2.2";
        assertTrue(preHandle("test-recover", ip), "第 1 次应放行");
        assertFalse(preHandle("test-recover", ip), "窗口内第 2 次应被限流");

        // windowSeconds=1，等窗口滑过后应恢复（固定窗口 key 带桶后缀的旧实现此时也会恢复，
        // 但 TTL 泄漏场景——INCR 与 EXPIRE 之间崩溃——会永久阻塞，此用例同时守护 TTL 存在）
        Thread.sleep(1100);
        assertTrue(preHandle("test-recover", ip), "窗口滑过后应恢复放行");
    }

    @Test
    @DisplayName("限流 key 的 TTL 必须存在（防泄漏守护）")
    void limitedKeyAlwaysHasTtl() throws Exception {
        String ip = "10.3.3.3";
        preHandle("test-cap", ip);
        String redisKey = "rate_limit:test-cap:" + ip;
        Long ttlSeconds = stringRedisTemplate.getExpire(redisKey);
        assertTrue(ttlSeconds != null && ttlSeconds > 0,
                "限流 key 必须设置 TTL，否则进程崩溃场景下 key 永不过期，实际 ttl=" + ttlSeconds);
    }
}
