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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;

/**
 * SKU 库存服务实现。
 *
 * <p>锁模式由 {@code stellar.stock.lock-mode} 控制，两种模式互斥，不允许同时使用：</p>
 * <ul>
 *   <li>{@code optimistic}（默认）：单条原子条件扣减（stock &gt;= qty 兜底 + version 递增），
 *       不依赖 Redis，也不再有“读-判-写+重试”循环（RR 下快照读会读到旧 version 导致重试必然失败）。</li>
 *   <li>{@code redis}：先获取 Redis 分布式锁，再执行单次直接更新；<b>不</b>再使用 version 做并发控制。</li>
 * </ul>
 *
 * <p>Redis 模式且 Redis 不可用时，会打印 WARNING 并降级为乐观锁模式，保证单机可用性。</p>
 * <p>Redis 锁的释放被延迟到事务提交/回滚之后（{@link #unlockAfterTransaction}），
 * 确保锁保护范围不小于事务范围，避免“锁已释放但事务未提交”的临界区缺口。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkuStockServiceImpl implements SkuStockService {

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
            // 锁的释放延迟到事务提交/回滚之后，保证锁保护范围 ≥ 事务范围
            unlockAfterTransaction(lockKey, token);
        }
    }

    /**
     * 本地乐观锁模式扣库存：单条原子条件扣减，无重试循环。
     *
     * <p>UPDATE ... WHERE id AND stock >= qty 是当前读，在 REPEATABLE READ /
     * READ COMMITTED 下都正确。原先的“getById 读 version → UPDATE WHERE version”重试循环
     * 在 RR 下 getById 是快照读（永远读事务开始时的旧 version），而 UPDATE 是当前读，
     * 两者永远对不上，20 次重试必然全部失败——已实测确认。原子扣减直接消除该问题。</p>
     */
    private void deductWithOptimisticLock(Long skuId, int qty) {
        int rows = skuMapper.deductStockAtomic(skuId, qty);
        if (rows == 0) {
            // 区分“SKU 不存在”与“库存不足”，给出可读的错误信息
            Sku sku = skuMapper.getById(skuId);
            if (sku == null) {
                throw new BaseException(MessageConstant.SKU_NOT_FOUND);
            }
            int stock = sku.getStock() == null ? 0 : sku.getStock();
            throw new StockInsufficientException(buildInsufficientMsg(skuId, stock, qty));
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
            // 锁的释放延迟到事务提交/回滚之后，保证锁保护范围 ≥ 事务范围
            unlockAfterTransaction(lockKey, token);
        }
    }

    /**
     * 本地乐观锁模式回滚库存：单条原子回滚，无重试循环。
     * rows==0 仅可能表示 SKU 不存在。
     */
    private void rollbackWithOptimisticLock(Long skuId, int qty) {
        int rows = skuMapper.rollbackStockAtomic(skuId, qty);
        if (rows == 0) {
            throw new BaseException(MessageConstant.SKU_NOT_FOUND);
        }
    }

    /**
     * 释放 Redis 锁：若当前线程处于 Spring 事务同步中，则注册 afterCompletion 回调，
     * 在事务提交/回滚完成后再释放锁；否则立即释放。
     *
     * <p>原因：{@code deduct}/{@code rollback} 声明了 {@code @Transactional}，
     * 若在 finally 中直接解锁，锁的释放会早于事务提交，锁保护范围小于事务范围——
     * 目前靠 InnoDB 行锁兜底未出事，但一旦扣减后新增其他校验，就会出现不一致。</p>
     */
    private void unlockAfterTransaction(String lockKey, String token) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    redisLockUtil.unlock(lockKey, token);
                }
            });
        } else {
            redisLockUtil.unlock(lockKey, token);
        }
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
