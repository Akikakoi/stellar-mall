package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * C 端购物车。表：stellar_cart。
 * 同一个 userId + skuId 只允许一条（Service 层去重合并数量）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cart implements Serializable {

    private Long id;

    private Long userId;

    private Long skuId;

    private Long spuId;

    /** 数量 */
    private Integer qty;

    /** 1 勾选 / 0 不勾选（下单时只读 checked=1 的项） */
    private Integer checked;

    private LocalDateTime createTime;
    private Long          createUser;
    private LocalDateTime updateTime;
    private Long          updateUser;
}
