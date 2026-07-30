package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户钱包实体，映射 stellar_wallet 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wallet implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long userId;
    /** 可用余额 */
    private BigDecimal balance;
    /** 冻结金额 */
    private BigDecimal frozen;
    /** 累计充值 */
    private BigDecimal totalRecharge;
    /** 累计消费 */
    private BigDecimal totalSpent;
    /** 乐观锁版本 */
    private Integer version;
    private LocalDateTime createTime;
    private Long createUser;
    private LocalDateTime updateTime;
    private Long updateUser;
}
