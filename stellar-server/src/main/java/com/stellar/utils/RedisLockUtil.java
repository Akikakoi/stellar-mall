package com.stellar.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁工具（基于 SET NX EX + Lua 释放）。
 *
 * 使用示例：
 * <pre>
 *   String token = redisLockUtil.tryLock("lock:stock:" + skuId, 5, TimeUnit.SECONDS);
 *   if (token == null) { throw new BaseException("系统繁忙，请重试"); }
 *   try {
 *       // 执行业务
 *   } finally {
 *       redisLockUtil.unlock("lock:stock:" + skuId, token);
 *   }
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLockUtil {

    private final StringRedisTemplate stringRedisTemplate;

    /** 释放锁 Lua 脚本：只有 value 匹配时才删除，防止误删别人的锁。 */
    private static final String RELEASE_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "    return redis.call('del', KEYS[1]) " +
            "else " +
            "    return 0 " +
            "end";

    private static final DefaultRedisScript<Long> RELEASE_REDIS_SCRIPT =
            new DefaultRedisScript<>(RELEASE_SCRIPT, Long.class);

    /**
     * 尝试获取分布式锁。Redis 不可用时返回 {@code null}，调用方可据此决定是否降级。
     *
     * @param lockKey  锁 key
     * @param timeout  锁最大持有时间
     * @param unit     时间单位
     * @return 锁 token（释放锁时需要传入）；获取失败或 Redis 不可用返回 null
     */
    public String tryLock(String lockKey, long timeout, TimeUnit unit) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean ok = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, token, timeout, unit);
            if (Boolean.TRUE.equals(ok)) {
                return token;
            }
            return null;
        } catch (Exception e) {
            log.warn("Redis 分布式锁获取失败，将降级处理，key={}，error={}", lockKey, e.getMessage());
            return null;
        }
    }

    /**
     * 判断 Redis 是否可用。
     */
    public boolean isAvailable() {
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.getConnectionFactory()
                    .getConnection().ping());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 释放分布式锁。
     *
     * @param lockKey 锁 key
     * @param token   获取锁时返回的 token
     */
    public void unlock(String lockKey, String token) {
        if (lockKey == null || token == null) {
            return;
        }
        try {
            stringRedisTemplate.execute(RELEASE_REDIS_SCRIPT,
                    Collections.singletonList(lockKey), token);
        } catch (Exception e) {
            log.warn("释放 Redis 分布式锁失败, key={}, error={}", lockKey, e.getMessage());
        }
    }
}
