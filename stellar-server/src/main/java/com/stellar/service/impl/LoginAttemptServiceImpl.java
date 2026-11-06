package com.stellar.service.impl;

import com.stellar.constant.MessageConstant;
import com.stellar.exception.BaseException;
import com.stellar.service.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
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
 * <p>
 * 降级策略（健全性实验发现的缺陷修复）：Redis 不可用时登录链路不阻断——
 * 锁定检查放行、失败计数跳过，与限流拦截器 / TokenBlacklistService 的
 * "可用性优先"降级口径保持一致。代价是 Redis 离线期间暴力破解防护暂时失效，
 * 由 JWT 签名校验与接口限流兜底，恢复后自动重新计数。
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
        String val;
        try {
            val = stringRedisTemplate.opsForValue().get(k);
        } catch (DataAccessException e) {
            // Redis 不可用：锁定检查降级放行（不阻断登录主流程）
            log.warn("[LoginAttempt] Redis 不可用，锁定检查降级放行: type={}, account={}, error={}",
                    type, account, e.getMessage());
            return;
        }
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
        Long count;
        try {
            count = stringRedisTemplate.opsForValue().increment(k);
            if (count != null && count == 1) {
                // 第一次失败时设置 TTL（15 分钟）
                stringRedisTemplate.expire(k, LOCK_MINUTES, TimeUnit.MINUTES);
            }
        } catch (DataAccessException e) {
            // Redis 不可用：失败计数跳过（Redis 恢复后重新计数）
            log.warn("[LoginAttempt] Redis 不可用，失败计数跳过: type={}, account={}, error={}",
                    type, account, e.getMessage());
            return;
        }
        if (count != null && count >= MAX_ATTEMPTS) {
            log.warn("[LoginAttempt] 账号被临时锁定: type={}, account={}, failures={}", type, account, count);
        }
    }

    @Override
    public void clearAttempts(String type, String account) {
        try {
            stringRedisTemplate.delete(key(type, account));
        } catch (DataAccessException e) {
            // Redis 不可用：计数 key 带 TTL 会自动过期，无需阻断登录
            log.warn("[LoginAttempt] Redis 不可用，清零计数跳过: type={}, account={}, error={}",
                    type, account, e.getMessage());
        }
    }

    @Override
    public void unlock(String type, String account) {
        try {
            stringRedisTemplate.delete(key(type, account));
        } catch (DataAccessException e) {
            log.warn("[LoginAttempt] Redis 不可用，手动解锁跳过（计数将随 TTL 过期）: type={}, account={}, error={}",
                    type, account, e.getMessage());
            return;
        }
        log.info("[LoginAttempt] 手动解锁: type={}, account={}", type, account);
    }
}
