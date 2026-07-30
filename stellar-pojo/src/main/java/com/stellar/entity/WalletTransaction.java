package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包交易流水实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransaction implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 钱包 ID */
    private Long walletId;
    /** 用户 ID */
    private Long userId;
    /** 交易类型：1 充值，2 消费，3 退款，4 提现 */
    private Integer type;
    /** 交易金额 */
    private BigDecimal amount;
    /** 交易后余额 */
    private BigDecimal balanceAfter;
    /** 渠道：WECHAT/ALIPAY/WALLET/ADMIN */
    private String channel;
    /** 关联业务类型 */
    private String bizType;
    /** 关联业务 ID */
    private Long bizId;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private LocalDateTime createTime;
}
