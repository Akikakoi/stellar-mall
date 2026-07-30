package com.stellar.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 钱包交易流水视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransactionVO {
    /** 流水 ID */
    private Long id;
    /** 交易类型：1 充值，2 消费，3 退款 */
    private Integer type;
    /** 交易类型文案 */
    private String typeText;
    /** 交易金额 */
    private BigDecimal amount;
    /** 交易后余额 */
    private BigDecimal balanceAfter;
    /** 渠道 */
    private String channel;
    /** 备注 */
    private String remark;
    /** 创建时间 */
    private String createTime;
}
