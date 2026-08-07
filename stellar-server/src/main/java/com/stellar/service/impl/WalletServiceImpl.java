package com.stellar.service.impl;

import com.stellar.constant.MessageConstant;
import com.stellar.dto.WalletRechargeDTO;
import com.stellar.entity.MallOrder;
import com.stellar.entity.Wallet;
import com.stellar.entity.WalletTransaction;
import com.stellar.enumeration.OrderStatus;
import com.stellar.exception.BaseException;
import com.stellar.mapper.MallOrderMapper;
import com.stellar.mapper.WalletMapper;
import com.stellar.result.PageResult;
import com.stellar.service.WalletService;
import com.stellar.vo.WalletTransactionVO;
import com.stellar.vo.WalletVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 钱包服务实现类。
 * <p>
 * 提供钱包账户管理、充值、支付、退款及交易流水查询等核心功能，
 * 余额变更采用乐观锁机制保证并发安全。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletMapper walletMapper;
    private final MallOrderMapper mallOrderMapper;

    // -------- 获取或创建钱包 --------

    /**
     * 获取或创建用户钱包。
     * 若用户尚无钱包则自动创建，初始余额为零。
     *
     * @param userId 用户ID
     * @return 钱包信息视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletVO getOrCreateWallet(Long userId) {
        Wallet wallet = walletMapper.getByUserId(userId);
        if (wallet == null) {
            wallet = Wallet.builder()
                    .userId(userId)
                    .balance(BigDecimal.ZERO)
                    .frozen(BigDecimal.ZERO)
                    .totalRecharge(BigDecimal.ZERO)
                    .totalSpent(BigDecimal.ZERO)
                    .version(0)
                    .build();
            walletMapper.insertWallet(wallet);
            log.info("[WalletService] 为用户 {} 创建钱包", userId);
        }
        return toVO(wallet);
    }

    // -------- 模拟充值 --------

    /**
     * 模拟钱包充值。
     * 使用乐观锁增加余额，并记录充值交易流水。
     *
     * @param userId 用户ID
     * @param dto    充值请求参数，包含金额和渠道
     * @return 充值后的钱包信息视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletVO recharge(Long userId, WalletRechargeDTO dto) {
        if (dto == null || dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER + "：充值金额必须大于0");
        }

        Wallet wallet = ensureWallet(userId);
        BigDecimal amount = dto.getAmount();
        String channel = dto.getChannel() != null ? dto.getChannel().toUpperCase() : "WECHAT";

        // 乐观锁增加余额
        int rows = walletMapper.addBalance(userId, amount, wallet.getVersion());
        if (rows == 0) {
            throw new BaseException("充值失败，请稍后重试（并发冲突）");
        }

        // 重新读取余额
        wallet = walletMapper.getByUserId(userId);
        walletMapper.addTotalRecharge(userId, amount);

        // 记录流水
        WalletTransaction tx = WalletTransaction.builder()
                .walletId(wallet.getId())
                .userId(userId)
                .type(1) // 充值
                .amount(amount)
                .balanceAfter(wallet.getBalance())
                .channel(channel)
                .bizType("RECHARGE")
                .remark(channel + "充值 ¥" + amount.setScale(2).toPlainString())
                .createTime(LocalDateTime.now())
                .build();
        walletMapper.insertTransaction(tx);

        log.info("[WalletService] 用户 {} {}充值 ¥{}", userId, channel, amount);
        return toVO(wallet);
    }

    // -------- 钱包支付 --------

    /**
     * 使用钱包余额支付订单。
     * 校验订单状态、余额是否充足，通过乐观锁扣减余额并更新订单状态为已支付。
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payByWallet(Long userId, Long orderId) {
        MallOrder order = mallOrderMapper.getById(orderId);
        if (order == null) {
            throw new BaseException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (!userId.equals(order.getUserId())) {
            throw new BaseException(MessageConstant.NO_PERMISSION);
        }
        if (!OrderStatus.PENDING.getBackendValue().equals(order.getStatus())) {
            throw new BaseException(MessageConstant.ORDER_STATUS_ERROR);
        }

        BigDecimal amount = order.getPayAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BaseException("订单金额无效");
        }

        Wallet wallet = ensureWallet(userId);
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new BaseException("钱包余额不足，当前余额 ¥" + wallet.getBalance().setScale(2).toPlainString());
        }

        // 乐观锁扣减
        int rows = walletMapper.deductBalance(userId, amount, wallet.getVersion());
        if (rows == 0) {
            throw new BaseException("支付失败，请稍后重试（并发冲突）");
        }

        // 重新读取余额
        wallet = walletMapper.getByUserId(userId);
        walletMapper.addTotalSpent(userId, amount);

        // 订单状态 → PAID
        mallOrderMapper.updateStatus(orderId, OrderStatus.PAID.getBackendValue());

        // 记录流水
        WalletTransaction tx = WalletTransaction.builder()
                .walletId(wallet.getId())
                .userId(userId)
                .type(2) // 消费
                .amount(amount.negate()) // 负数表示支出
                .balanceAfter(wallet.getBalance())
                .channel("WALLET")
                .bizType("ORDER")
                .bizId(orderId)
                .remark("订单支付 " + order.getOrderNo())
                .createTime(LocalDateTime.now())
                .build();
        walletMapper.insertTransaction(tx);

        log.info("[WalletService] 用户 {} 钱包支付订单 {}, ¥{}", userId, orderId, amount);
    }

    // -------- 退款到钱包 --------

    /**
     * 退款到用户钱包。
     * 使用乐观锁增加余额，最多重试3次以应对并发冲突，并记录退款交易流水。
     *
     * @param userId  用户ID
     * @param orderId 订单ID
     * @param amount  退款金额
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundToWallet(Long userId, Long orderId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return;

        // 增加余额（乐观锁 + 重试，避免并发冲突导致退款失败）
        int rows = 0;
        int maxRetry = 3;
        for (int i = 0; i < maxRetry; i++) {
            Wallet wallet = ensureWallet(userId);
            rows = walletMapper.addBalance(userId, amount, wallet.getVersion());
            if (rows > 0) break;
            if (i < maxRetry - 1) {
                log.warn("[WalletService] 退款乐观锁冲突（第{}次重试），用户 {}，订单 {}", i + 1, userId, orderId);
            }
        }
        if (rows == 0) {
            throw new BaseException("退款失败，请稍后重试（并发冲突）");
        }

        Wallet wallet = walletMapper.getByUserId(userId);

        MallOrder order = mallOrderMapper.getById(orderId);
        String orderNo = order != null ? order.getOrderNo() : "订单#" + orderId;

        // 记录流水
        WalletTransaction tx = WalletTransaction.builder()
                .walletId(wallet.getId())
                .userId(userId)
                .type(3) // 退款
                .amount(amount) // 正数表示退款入账
                .balanceAfter(wallet.getBalance())
                .channel("WALLET")
                .bizType("REFUND")
                .bizId(orderId)
                .remark("退款 " + orderNo + " ¥" + amount.setScale(2).toPlainString())
                .createTime(LocalDateTime.now())
                .build();
        walletMapper.insertTransaction(tx);

        log.info("[WalletService] 退款到钱包，用户 {}，订单 {}，¥{}", userId, orderId, amount);
    }

    // -------- 交易流水 --------

    /**
     * 分页查询用户钱包交易流水。
     *
     * @param userId   用户ID
     * @param page     当前页码（从1开始）
     * @param pageSize 每页记录数
     * @return 分页结果，包含交易流水视图对象列表
     */
    @Override
    public PageResult pageTransactions(Long userId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<WalletTransaction> list = walletMapper.listTransactions(userId, offset, pageSize);
        int total = walletMapper.countTransactions(userId);

        List<WalletTransactionVO> vos = new ArrayList<>();
        if (list != null) {
            for (WalletTransaction tx : list) {
                vos.add(toTransactionVO(tx));
            }
        }
        return new PageResult((long) total, vos);
    }

    // ================= 内部工具 =================

    private Wallet ensureWallet(Long userId) {
        Wallet wallet = walletMapper.getByUserId(userId);
        if (wallet == null) {
            wallet = Wallet.builder()
                    .userId(userId)
                    .balance(BigDecimal.ZERO)
                    .frozen(BigDecimal.ZERO)
                    .totalRecharge(BigDecimal.ZERO)
                    .totalSpent(BigDecimal.ZERO)
                    .version(0)
                    .build();
            walletMapper.insertWallet(wallet);
            wallet = walletMapper.getByUserId(userId);
        }
        return wallet;
    }

    private WalletVO toVO(Wallet w) {
        if (w == null) return null;
        return WalletVO.builder()
                .id(w.getId())
                .balance(w.getBalance() != null ? w.getBalance() : BigDecimal.ZERO)
                .totalRecharge(w.getTotalRecharge() != null ? w.getTotalRecharge() : BigDecimal.ZERO)
                .totalSpent(w.getTotalSpent() != null ? w.getTotalSpent() : BigDecimal.ZERO)
                .build();
    }

    private static final String[] TX_TYPE_TEXT = {"", "充值", "消费", "退款", "提现"};

    private WalletTransactionVO toTransactionVO(WalletTransaction tx) {
        String typeText = (tx.getType() != null && tx.getType() >= 1 && tx.getType() <= 4)
                ? TX_TYPE_TEXT[tx.getType()] : "未知";
        return WalletTransactionVO.builder()
                .id(tx.getId())
                .type(tx.getType())
                .typeText(typeText)
                .amount(tx.getAmount())
                .balanceAfter(tx.getBalanceAfter())
                .channel(tx.getChannel())
                .remark(tx.getRemark())
                .createTime(tx.getCreateTime() != null ? tx.getCreateTime().toString() : null)
                .build();
    }
}
