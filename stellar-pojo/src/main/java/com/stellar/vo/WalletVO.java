package com.stellar.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 钱包视图对象。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletVO {
    /** 钱包 ID */
    private Long id;
    /** 可用余额 */
    private BigDecimal balance;
    /** 累计充值 */
    private BigDecimal totalRecharge;
    /** 累计消费 */
    private BigDecimal totalSpent;
}
