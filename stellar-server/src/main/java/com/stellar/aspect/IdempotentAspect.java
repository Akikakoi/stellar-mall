package com.stellar.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stellar.annotation.Idempotent;
import com.stellar.json.JacksonObjectMapper;
import com.stellar.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Optional;

/**
 * 幂等性切面 — 基于 Redis 缓存请求结果，防重复提交。
 * <p>
 * 替代原 IdempotentInterceptor：原拦截器 cacheResponse() 无人调用导致缓存永远不写入，
 * 改用 @Around 切面在方法返回后直接缓存返回值，从根本上修复该 bug。
 * <p>
 * 流程：
 *   1. 读取请求头 X-Idempotency-Key，为空则跳过（向后兼容，未带 key 的客户端不强制幂等）
 *   2. Redis 命中已完成的缓存结果 → 直接反序列化返回上次的 Result，不执行目标方法
 *   3. 未命中 → setIfAbsent 原子抢占"处理中"标记；抢占失败说明有并发重复请求正在执行，
 *      直接返回"处理中"错误，杜绝原 get-then-set 间隙下两个重复请求都穿透执行的竞态
 *   4. 抢占成功 → 执行目标方法，成功返回后把 Result 序列化写入 Redis（TTL=windowSeconds）并释放标记
 *   5. 目标方法抛异常 → 释放标记不缓存结果，让客户端可重试
 * <p>
 * Redis 不可用时降级为放行（牺牲幂等性保证可用性）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class IdempotentAspect {

    private static final String HEADER_KEY = "X-Idempotency-Key";
    private static final String REDIS_PREFIX = "idempotent:";
    private static final String LOCK_SUFFIX = ":processing";
    private static final ObjectMapper MAPPER = new JacksonObjectMapper();

    private final Optional<StringRedisTemplate> redisTemplate;

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint pjp, Idempotent idempotent) throws Throwable {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            // 非 HTTP 上下文（如测试直接调用 service）→ 跳过幂等
            return pjp.proceed();
        }

        String clientKey = request.getHeader(HEADER_KEY);
        if (clientKey == null || clientKey.isEmpty()) {
            // 客户端未带幂等键 → 跳过，保持向后兼容
            return pjp.proceed();
        }

        String redisKey = REDIS_PREFIX + idempotent.keyPrefix() + ":" + clientKey;
        String lockKey = redisKey + LOCK_SUFFIX;
        Duration window = Duration.ofSeconds(idempotent.windowSeconds() > 0 ? idempotent.windowSeconds() : 300);

        // Redis 不可用 → 降级放行（保持可用性）
        if (redisTemplate.isEmpty()) {
            return pjp.proceed();
        }

        // 1. 命中已完成结果的缓存 → 直接返回上次结果
        try {
            String cached = redisTemplate.get().opsForValue().get(redisKey);
            if (cached != null) {
                log.info("[Idempotent] 重复请求命中缓存, key={}", redisKey);
                return MAPPER.readValue(cached, Result.class);
            }
        } catch (Exception e) {
            log.warn("[Idempotent] Redis 读取失败，降级放行: {}", e.getMessage());
            return pjp.proceed();
        }

        // 2. setIfAbsent 原子抢占"处理中"标记，未抢到说明并发重复请求正在执行
        Boolean locked;
        try {
            locked = redisTemplate.get().opsForValue().setIfAbsent(lockKey, "1", window);
        } catch (Exception e) {
            log.warn("[Idempotent] Redis 写入失败，降级放行: {}", e.getMessage());
            return pjp.proceed();
        }
        if (!Boolean.TRUE.equals(locked)) {
            log.info("[Idempotent] 并发重复请求被拦截（正在处理中）, key={}", redisKey);
            return Result.error("请求正在处理中，请勿重复提交");
        }

        // 3. 抢占成功 → 执行目标方法
        try {
            Object result = pjp.proceed();
            // 4. 仅缓存成功结果（Result.code == 1），失败结果不缓存以便客户端重试
            if (result instanceof Result<?> r && Integer.valueOf(1).equals(r.getCode())) {
                cacheResult(redisKey, r, window);
            }
            return result;
        } finally {
            // 5. 无论成败都释放"处理中"标记（成功场景由结果缓存兜底，失败场景放行重试）
            releaseLock(lockKey);
        }
    }

    private void cacheResult(String redisKey, Result<?> result, Duration ttl) {
        try {
            String json = MAPPER.writeValueAsString(result);
            redisTemplate.get().opsForValue().set(redisKey, json, ttl);
            log.debug("[Idempotent] 已缓存响应, key={}, ttl={}s", redisKey, ttl.getSeconds());
        } catch (Exception e) {
            log.warn("[Idempotent] 缓存响应失败: {}", e.getMessage());
        }
    }

    private void releaseLock(String lockKey) {
        try {
            redisTemplate.get().delete(lockKey);
        } catch (Exception e) {
            log.warn("[Idempotent] 释放处理标记失败（将随 TTL 自动过期）: {}", e.getMessage());
        }
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }
}
