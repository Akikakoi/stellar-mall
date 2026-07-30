package com.stellar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 钱包充值请求 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletRechargeDTO {
    /** 充值金额 */
    private BigDecimal amount;
    /** 充值渠道：WECHAT / ALIPAY */
    private String channel;
}
