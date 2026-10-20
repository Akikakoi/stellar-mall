package com.stellar.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 分布式锁工具（基于 Redisson RLock，内部实现为 EVAL Lua 脚本）。
 *
 * <p><b>相对旧版 SET NX EX + Lua 释放的收益</b>：
 * 加锁/续期/释放全部是服务端原子脚本；unlock 带 owner 校验，不可能误删别人的锁；
 * 同线程可重入；持锁超过 leaseTime 的竞态窗口由 Redisson 的加锁协议收窄。</p>
 *
 * <p><b>语义与旧版保持一致（刻意不用看门狗）</b>：{@code tryLock(key, timeout, unit)}
 * 中 timeout 仍是最大持有时间（leaseTime），到期自动释放、不自动续期。
 * 原因：本项目的锁用在「事务提交后释放」的短临界区（库存扣减/回滚），lease 上界
 * 恰是防「业务线程挂死 → 锁永不释放」的保险丝；若改成看门狗续期模式，
 * 挂死的持有者会让锁无限期占用。确需看门狗的场景直接注入 {@link RedissonClient} 使用。</p>
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
 *
 * <p>注：token 仅作向后兼容保留（旧调用方签名不变），释放判定已由 RLock 的
 * 持有者校验（线程维度）接管；unlock 必须与 tryLock 在同一线程执行——
 * 现有调用点（含 {@code afterCompletion} 事务回调）均满足。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLockUtil {

    /** Redisson client 是 @Lazy bean：首次用锁才创建，Redis 离线时在此处降级而非启动失败。 */
    private final ObjectProvider<RedissonClient> redissonClientProvider;
    private final StringRedisTemplate stringRedisTemplate;

    /** 惰性获取 client；创建失败（Redis 连不上）返回 null，由调用方降级。 */
    private RedissonClient client() {
        try {
            return redissonClientProvider.getIfAvailable();
        } catch (Exception e) {
            log.warn("Redisson client 创建失败（Redis 可能离线），将降级处理: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 尝试获取分布式锁（不等待，立即返回）。Redis 不可用时返回 {@code null}，调用方可据此决定是否降级。
     *
     * @param lockKey  锁 key
     * @param timeout  锁最大持有时间（leaseTime，到期自动释放）
     * @param unit     时间单位
     * @return 锁 token（释放锁时需传入）；获取失败或 Redis 不可用返回 null
     */
    public String tryLock(String lockKey, long timeout, TimeUnit unit) {
        RedissonClient client = client();
        if (client == null) {
            return null;
        }
        try {
            RLock lock = client.getLock(lockKey);
            boolean ok = lock.tryLock(0, timeout, unit);
            return ok ? "redisson" : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Redis 分布式锁获取被中断，key={}", lockKey);
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
     * 释放分布式锁。带持有者校验：只释放当前线程持有的锁，不会误删他人的锁。
     *
     * @param lockKey 锁 key
     * @param token   获取锁时返回的 token（兼容保留，实际由 RLock 持有者校验接管）
     */
    public void unlock(String lockKey, String token) {
        if (lockKey == null) {
            return;
        }
        RedissonClient client = client();
        if (client == null) {
            return;
        }
        try {
            RLock lock = client.getLock(lockKey);
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception e) {
            log.warn("释放 Redis 分布式锁失败, key={}, error={}", lockKey, e.getMessage());
        }
    }
}
