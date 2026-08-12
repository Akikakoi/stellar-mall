package com.stellar.service.impl;

import com.stellar.constant.MessageConstant;
import com.stellar.exception.BaseException;
import com.stellar.service.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 登录失败计数与账号临时锁定实现（E2）。
 * <p>
 * Redis key 设计：
 *   失败计数：login_fail:{type}:{account} → 次数，TTL = LOCK_MINUTES
 *   （第 5 次失败时 increment 返回 5，触发锁定；计数 key 的 TTL 即锁定时长）
 * <p>
 * 阈值：5 次失败 / 15 分钟锁定（统一管理端与 C 端，见 SPEC.md Decision 2）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

    /** 最大失败次数：达到后锁定 */
    private static final int MAX_ATTEMPTS = 5;
    /** 锁定时长（分钟） */
    private static final long LOCK_MINUTES = 15;

    private final StringRedisTemplate stringRedisTemplate;

    /** Redis key 前缀：login_fail:{type}:{account} */
    private static String key(String type, String account) {
        return "login_fail:" + type + ":" + account;
    }

    @Override
    public void checkLocked(String type, String account) {
        String k = key(type, account);
        String val = stringRedisTemplate.opsForValue().get(k);
        if (val != null) {
            try {
                long count = Long.parseLong(val);
                if (count >= MAX_ATTEMPTS) {
                    // 计算 TTL 剩余（这里简化为固定提示，实际 TTL 由 Redis 管理）
                    long ttl = stringRedisTemplate.getExpire(k, TimeUnit.MINUTES);
                    long remainMin = ttl > 0 ? ttl : LOCK_MINUTES;
                    throw new BaseException(String.format(MessageConstant.ACCOUNT_LOCKED_BY_ATTEMPTS, remainMin));
                }
            } catch (NumberFormatException e) {
                // val 不是数字（异常情况），忽略
            }
        }
    }

    @Override
    public void recordFailure(String type, String account) {
        String k = key(type, account);
        Long count = stringRedisTemplate.opsForValue().increment(k);
        if (count != null && count == 1) {
            // 第一次失败时设置 TTL（15 分钟）
            stringRedisTemplate.expire(k, LOCK_MINUTES, TimeUnit.MINUTES);
        }
        if (count != null && count >= MAX_ATTEMPTS) {
            log.warn("[LoginAttempt] 账号被临时锁定: type={}, account={}, failures={}", type, account, count);
        }
    }

    @Override
    public void clearAttempts(String type, String account) {
        stringRedisTemplate.delete(key(type, account));
    }

    @Override
    public void unlock(String type, String account) {
        stringRedisTemplate.delete(key(type, account));
        log.info("[LoginAttempt] 手动解锁: type={}, account={}", type, account);
    }
}
