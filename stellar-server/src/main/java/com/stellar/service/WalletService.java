package com.stellar.service;

import com.stellar.dto.WalletRechargeDTO;
import com.stellar.result.PageResult;
import com.stellar.vo.WalletTransactionVO;
import com.stellar.vo.WalletVO;

/**
 * 钱包服务接口。
 */
public interface WalletService {

    /** 获取或创建用户钱包 */
    WalletVO getOrCreateWallet(Long userId);

    /** 模拟充值（微信/支付宝） */
    WalletVO recharge(Long userId, WalletRechargeDTO dto);

    /** 钱包支付（扣减余额） */
    void payByWallet(Long userId, Long orderId);

    /** 退款到钱包（由售后确认退款调用） */
    void refundToWallet(Long userId, Long orderId, java.math.BigDecimal amount);

    /** 交易流水（分页） */
    PageResult pageTransactions(Long userId, int page, int pageSize);
}
