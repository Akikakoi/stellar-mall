package com.stellar.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.annotation.Idempotent;
import com.stellar.json.JacksonObjectMapper;
import com.stellar.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * IdempotentAspect 单元测试。
 * <p>
 * 不启动 Spring 容器，直接 mock ProceedingJoinPoint / HttpServletRequest / StringRedisTemplate，
 * 验证切面在 6 个关键场景下的行为。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("幂等切面 — IdempotentAspect")
class IdempotentAspectTest {

    @Mock private ProceedingJoinPoint pjp;
    @Mock private HttpServletRequest request;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private IdempotentAspect aspect;
    private Idempotent idempotentAnnotation;

    private static final String HEADER_KEY = "X-Idempotency-Key";
    private static final String CLIENT_KEY = "test-uuid-1234";
    private static final String REDIS_KEY = "idempotent:order:" + CLIENT_KEY;

    @BeforeEach
    void setUp() throws Exception {
        // 构造注解实例（用动态代理生成 @Idempotent 实例）
        idempotentAnnotation = new Idempotent() {
            @Override public String keyPrefix() { return "order"; }
            @Override public int windowSeconds() { return 300; }
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return Idempotent.class; }
        };

        // 手动 new 切面，注入 Optional<StringRedisTemplate>
        aspect = new IdempotentAspect(Optional.of(redisTemplate));

        // 把 request 绑到 RequestContextHolder（aspect 通过它拿当前请求）
        ServletRequestAttributes attrs = mock(ServletRequestAttributes.class);
        when(attrs.getRequest()).thenReturn(request);
        RequestContextHolder.setRequestAttributes(attrs);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    /** 帮 mock 一次 redisTemplate.opsForValue()，避免每个用例重复 stub */
    private void stubRedisOps() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ================================================================
    @Test
    @DisplayName("1. 命中缓存 → 直接返回上次结果，不执行目标方法")
    void cacheHit_returnsCachedResult_withoutProceed() throws Throwable {
        when(request.getHeader(HEADER_KEY)).thenReturn(CLIENT_KEY);
        stubRedisOps();

        // Redis 里有缓存
        Result<?> cachedResult = Result.success("cached-order-id");
        String cachedJson = new JacksonObjectMapper().writeValueAsString(cachedResult);
        when(valueOps.get(REDIS_KEY)).thenReturn(cachedJson);

        Object result = aspect.around(pjp, idempotentAnnotation);

        // 验证返回的是反序列化后的 Result
        assertNotNull(result);
        assertInstanceOf(Result.class, result);
        assertEquals(1, ((Result<?>) result).getCode());
        assertEquals("cached-order-id", ((Result<?>) result).getData());

        // 关键：目标方法绝对不能执行
        verify(pjp, never()).proceed();
        // 不应该再写缓存
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    // ================================================================
    @Test
    @DisplayName("2. 未命中缓存 → 执行目标方法 → 成功则写入 Redis")
    void cacheMiss_proceedAndCacheSuccess() throws Throwable {
        when(request.getHeader(HEADER_KEY)).thenReturn(CLIENT_KEY);
        stubRedisOps();
        when(valueOps.get(REDIS_KEY)).thenReturn(null);

        Result<?> freshResult = Result.success(1001L);
        when(pjp.proceed()).thenReturn(freshResult);

        Object result = aspect.around(pjp, idempotentAnnotation);

        // 返回目标方法的结果
        assertSame(freshResult, result);

        // 验证写入了 Redis（key 正确，TTL=300s）
        verify(valueOps).set(eq(REDIS_KEY), contains("\"code\":1"), eq(Duration.ofSeconds(300)));
    }

    // ================================================================
    @Test
    @DisplayName("3. 目标方法抛异常 → 不缓存，异常向上抛")
    void targetThrowsException_notCached_exceptionPropagates() throws Throwable {
        when(request.getHeader(HEADER_KEY)).thenReturn(CLIENT_KEY);
        stubRedisOps();
        when(valueOps.get(REDIS_KEY)).thenReturn(null);

        RuntimeException boom = new RuntimeException("库存不足");
        when(pjp.proceed()).thenThrow(boom);

        // 异常应该原样抛出
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> aspect.around(pjp, idempotentAnnotation));
        assertSame(boom, ex);

        // 不应该写缓存
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    // ================================================================
    @Test
    @DisplayName("4. 目标方法返回失败 Result(code=0) → 不缓存，让客户端可重试")
    void targetReturnsFailureResult_notCached() throws Throwable {
        when(request.getHeader(HEADER_KEY)).thenReturn(CLIENT_KEY);
        stubRedisOps();
        when(valueOps.get(REDIS_KEY)).thenReturn(null);

        Result<?> failResult = Result.error("库存不足");
        when(pjp.proceed()).thenReturn(failResult);

        Object result = aspect.around(pjp, idempotentAnnotation);

        // 失败结果原样返回
        assertSame(failResult, result);
        assertEquals(0, ((Result<?>) result).getCode());

        // 不应该写缓存
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    // ================================================================
    @Test
    @DisplayName("5. 请求头无 X-Idempotency-Key → 放行，不查 Redis")
    void noIdempotencyHeader_passThrough() throws Throwable {
        when(request.getHeader(HEADER_KEY)).thenReturn(null);

        Result<?> result = Result.success("ok");
        when(pjp.proceed()).thenReturn(result);

        Object ret = aspect.around(pjp, idempotentAnnotation);

        assertSame(result, ret);
        // 不应该查 Redis
        verify(redisTemplate, never()).opsForValue();
        verify(valueOps, never()).get(anyString());
    }

    // ================================================================
    @Test
    @DisplayName("6. Redis 不可用（Optional.empty）→ 降级放行，不抛异常")
    void redisUnavailable_degradeToPassThrough() throws Throwable {
        // 重新构造一个 Redis 不可用的切面
        IdempotentAspect noRedisAspect = new IdempotentAspect(Optional.empty());

        when(request.getHeader(HEADER_KEY)).thenReturn(CLIENT_KEY);

        Result<?> result = Result.success("ok");
        when(pjp.proceed()).thenReturn(result);

        Object ret = noRedisAspect.around(pjp, idempotentAnnotation);

        // 降级放行，返回目标方法结果
        assertSame(result, ret);
        // Redis 不可用时不会调用任何 Redis 操作
        verifyNoInteractions(redisTemplate);
    }

    // ================================================================
    @Test
    @DisplayName("7. 空 X-Idempotency-Key 字符串 → 等同于未带，放行")
    void emptyIdempotencyHeader_passThrough() throws Throwable {
        when(request.getHeader(HEADER_KEY)).thenReturn("");

        Result<?> result = Result.success("ok");
        when(pjp.proceed()).thenReturn(result);

        Object ret = aspect.around(pjp, idempotentAnnotation);

        assertSame(result, ret);
        verify(redisTemplate, never()).opsForValue();
    }

    // ================================================================
    @Test
    @DisplayName("8. Redis 读取抛异常 → 降级放行执行目标方法（不抛）")
    void redisGetThrows_degradeToProceed() throws Throwable {
        when(request.getHeader(HEADER_KEY)).thenReturn(CLIENT_KEY);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(REDIS_KEY)).thenThrow(new RuntimeException("Redis 连接断开"));

        Result<?> result = Result.success("ok");
        when(pjp.proceed()).thenReturn(result);

        Object ret = aspect.around(pjp, idempotentAnnotation);

        // Redis 异常被吞，降级执行目标方法
        assertSame(result, ret);
        verify(pjp).proceed();
    }
}
