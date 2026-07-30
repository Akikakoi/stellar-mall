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
 * 钱包 Mapper。
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
