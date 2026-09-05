package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 库存变动流水记录（对应 stellar_stock_log）。
 * 每次库存变动自动写入一条记录，用于追溯和审计。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockLog implements Serializable {

    private Long id;
    /** SKU ID */
    private Long skuId;
    /** 变动类型：1 入库，2 出库，3 盘盈，4 盘亏，5 调整 */
    private Integer type;
    /** 变动数量（正数增加，负数减少） */
    private Integer quantity;
    /** 变动前库存 */
    private Integer stockBefore;
    /** 变动后库存 */
    private Integer stockAfter;
    /** 备注 */
    private String remark;
    /** 业务类型：PURCHASE_IN / SALE_OUT / INVENTORY_PROFIT / INVENTORY_LOSS / ADJUSTMENT */
    private String businessType;
    /** 关联业务单号 */
    private String businessNo;
    private LocalDateTime createTime;
    private Long createUser;
}