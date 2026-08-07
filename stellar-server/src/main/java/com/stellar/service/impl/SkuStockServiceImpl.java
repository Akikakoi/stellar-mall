package com.stellar.service.impl;

import com.stellar.constant.MessageConstant;
import com.stellar.entity.Sku;
import com.stellar.exception.BaseException;
import com.stellar.exception.StockInsufficientException;
import com.stellar.mapper.SkuMapper;
import com.stellar.service.SkuStockService;
import com.stellar.utils.RedisLockUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * SKU 库存服务实现。
 *
 * <p>锁模式由 {@code stellar.stock.lock-mode} 控制，两种模式互斥，不允许同时使用：</p>
 * <ul>
 *   <li>{@code optimistic}（默认）：纯本地乐观锁（version + READ_COMMITTED 重试），不依赖 Redis。</li>
 *   <li>{@code redis}：先获取 Redis 分布式锁，再执行单次直接更新；<b>不</b>再使用 version 做并发控制。</li>
 * </ul>
 *
 * <p>Redis 模式且 Redis 不可用时，会打印 WARNING 并降级为乐观锁模式，保证单机可用性。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkuStockServiceImpl implements SkuStockService {

    private static final int MAX_RETRY = 20;
    private static final String LOCK_PREFIX = "lock:stock:sku:";
    private static final long LOCK_TIMEOUT_SECONDS = 10;

    private final SkuMapper skuMapper;
    private final RedisLockUtil redisLockUtil;

    @Value("${stellar.stock.lock-mode:optimistic}")
    private String lockMode;

    /**
     * 扣减 SKU 库存。
     *
     * <p>根据 {@code stellar.stock.lock-mode} 配置选择锁模式：
     * Redis 分布式锁模式或本地乐观锁模式。</p>
     *
     * @param skuId SKU ID
     * @param qty   扣减数量
     * @throws StockInsufficientException 库存不足或并发冲突时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void deduct(Long skuId, int qty) {
        validateParam(skuId, qty);

        if ("redis".equalsIgnoreCase(lockMode)) {
            deductWithRedisLock(skuId, qty);
        } else {
            deductWithOptimisticLock(skuId, qty);
        }
    }

    /**
     * 回滚 SKU 库存（将已扣减的库存加回）。
     *
     * <p>根据 {@code stellar.stock.lock-mode} 配置选择锁模式：
     * Redis 分布式锁模式或本地乐观锁模式。</p>
     *
     * @param skuId SKU ID
     * @param qty   回滚数量
     * @throws BaseException 回滚失败时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void rollback(Long skuId, int qty) {
        validateParam(skuId, qty);

        if ("redis".equalsIgnoreCase(lockMode)) {
            rollbackWithRedisLock(skuId, qty);
        } else {
            rollbackWithOptimisticLock(skuId, qty);
        }
    }

    /**
     * Redis 分布式锁模式扣库存：加锁后直接更新，不使用 version 做并发控制。
     * Redis 不可用时降级为乐观锁。
     */
    private void deductWithRedisLock(Long skuId, int qty) {
        String lockKey = LOCK_PREFIX + skuId;
        String token = redisLockUtil.tryLock(lockKey, LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (token == null) {
            if (redisLockUtil.isAvailable()) {
                log.warn("获取库存分布式锁失败, lockKey={}", lockKey);
                throw new StockInsufficientException(MessageConstant.STOCK_NOT_ENOUGH
                        + "（系统繁忙，请稍后重试）");
            }
            log.warn("Redis 不可用，库存扣减降级为本地乐观锁, skuId={}", skuId);
            deductWithOptimisticLock(skuId, qty);
            return;
        }

        try {
            Sku sku = skuMapper.getById(skuId);
            if (sku == null) {
                throw new BaseException(MessageConstant.SKU_NOT_FOUND);
            }
            int stock = sku.getStock() == null ? 0 : sku.getStock();
            if (stock < qty) {
                throw new StockInsufficientException(buildInsufficientMsg(skuId, stock, qty));
            }
            int rows = skuMapper.deductStock(skuId, qty);
            if (rows == 0) {
                throw new StockInsufficientException(MessageConstant.STOCK_NOT_ENOUGH
                        + "（扣减失败，请稍后重试）");
            }
        } finally {
            redisLockUtil.unlock(lockKey, token);
        }
    }

    /**
     * 本地乐观锁模式扣库存：version 字段作为并发控制，失败则带指数退避重试。
     */
    private void deductWithOptimisticLock(Long skuId, int qty) {
        for (int i = 0; i < MAX_RETRY; i++) {
            Sku sku = skuMapper.getById(skuId);
            if (sku == null) {
                throw new BaseException(MessageConstant.SKU_NOT_FOUND);
            }
            int stock = sku.getStock() == null ? 0 : sku.getStock();
            if (stock < qty) {
                throw new StockInsufficientException(buildInsufficientMsg(skuId, stock, qty));
            }
            Integer version = sku.getVersion() == null ? 0 : sku.getVersion();
            int rows = skuMapper.deductStockWithVersion(skuId, version, qty);
            if (rows > 0) {
                return;
            }
            // 指数退避 + 抖动，避免高并发下所有线程同频重试造成空转
            if (i < MAX_RETRY - 1) {
                backoff(i);
            }
        }
        throw new StockInsufficientException(MessageConstant.STOCK_NOT_ENOUGH
                + "（并发冲突，已重试 " + MAX_RETRY + " 次）");
    }

    private void backoff(int attempt) {
        try {
            long baseMs = 5L << attempt; // 5, 10, 20, 40...
            long maxMs = Math.min(baseMs, 200L);
            long jitter = ThreadLocalRandom.current().nextLong(maxMs / 2, maxMs + 1);
            Thread.sleep(jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BaseException("库存扣减被中断");
        }
    }

    /**
     * Redis 分布式锁模式回滚库存：加锁后直接更新，不使用 version 做并发控制。
     * Redis 不可用时降级为乐观锁。
     */
    private void rollbackWithRedisLock(Long skuId, int qty) {
        String lockKey = LOCK_PREFIX + skuId;
        String token = redisLockUtil.tryLock(lockKey, LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (token == null) {
            if (redisLockUtil.isAvailable()) {
                log.warn("获取库存分布式锁失败, lockKey={}", lockKey);
                throw new BaseException("库存回滚失败（系统繁忙，请稍后重试）");
            }
            log.warn("Redis 不可用，库存回滚降级为本地乐观锁, skuId={}", skuId);
            rollbackWithOptimisticLock(skuId, qty);
            return;
        }

        try {
            Sku sku = skuMapper.getById(skuId);
            if (sku == null) {
                throw new BaseException(MessageConstant.SKU_NOT_FOUND);
            }
            int rows = skuMapper.rollbackStock(skuId, qty);
            if (rows == 0) {
                throw new BaseException("库存回滚失败（请稍后重试）");
            }
        } finally {
            redisLockUtil.unlock(lockKey, token);
        }
    }

    /**
     * 本地乐观锁模式回滚库存：version 字段作为并发控制，失败则带指数退避重试。
     */
    private void rollbackWithOptimisticLock(Long skuId, int qty) {
        for (int i = 0; i < MAX_RETRY; i++) {
            Sku sku = skuMapper.getById(skuId);
            if (sku == null) {
                throw new BaseException(MessageConstant.SKU_NOT_FOUND);
            }
            Integer version = sku.getVersion() == null ? 0 : sku.getVersion();
            int rows = skuMapper.rollbackStockWithVersion(skuId, version, qty);
            if (rows > 0) {
                return;
            }
            if (i < MAX_RETRY - 1) {
                backoff(i);
            }
        }
        throw new BaseException("库存回滚失败（并发冲突，已重试 " + MAX_RETRY + " 次）");
    }

    private void validateParam(Long skuId, int qty) {
        if (skuId == null || qty <= 0) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        }
    }

    private String buildInsufficientMsg(Long skuId, int current, int need) {
        return MessageConstant.STOCK_NOT_ENOUGH
                + " (skuId=" + skuId + ", current=" + current + ", need=" + need + ")";
    }
}
