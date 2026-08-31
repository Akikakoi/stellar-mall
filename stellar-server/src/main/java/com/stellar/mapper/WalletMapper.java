package com.stellar.mapper;

import com.stellar.annotation.AutoFill;
import com.stellar.entity.Wallet;
import com.stellar.entity.WalletTransaction;
import com.stellar.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 钱包 Mapper，管理用户钱包账户余额及交易流水，支持乐观锁余额操作。
 * 表：stellar_wallet（钱包账户）、stellar_wallet_transaction（交易流水）。
 */
@Mapper
public interface WalletMapper {

    // -------- 钱包账户 --------

    @AutoFill(OperationType.INSERT)
    int insertWallet(Wallet wallet);

    Wallet getByUserId(@Param("userId") Long userId);

    /** 带乐观锁的余额扣减 */
    int deductBalance(@Param("userId") Long userId,
                      @Param("amount") BigDecimal amount,
                      @Param("oldVersion") Integer oldVersion);

    /** 带乐观锁的余额增加 */
    int addBalance(@Param("userId") Long userId,
                   @Param("amount") BigDecimal amount,
                   @Param("oldVersion") Integer oldVersion);

    /**
     * 原子余额扣减（RR 下规避"读 version → UPDATE WHERE version 重试"的快照读陷阱）。
     * 余额条件内置，rows==0 表示余额不足或账户不存在，无需先读后写。
     */
    int deductBalanceAtomic(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /**
     * 原子余额增加（RR 下规避快照读陷阱）。加余额无余额条件，rows==0 表示账户不存在。
     */
    int addBalanceAtomic(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /** 增加累计充值 */
    int addTotalRecharge(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /** 增加累计消费 */
    int addTotalSpent(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    // -------- 流水 --------

    int insertTransaction(WalletTransaction tx);

    List<WalletTransaction> listTransactions(@Param("userId") Long userId,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    int countTransactions(@Param("userId") Long userId);
}
