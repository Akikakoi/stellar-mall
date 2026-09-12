package com.stellar.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量调整库存的单项：SKU ID + 增量。
 *
 * <p>供 {@code SkuMapper#adjustStockBatch} 的 CASE WHEN 批量 UPDATE 使用。
 * <b>同一 SKU 在一个批次内只应出现一次</b>——CASE 表达式遇到重复的 WHEN 只会命中第一个，
 * 因此调用方必须先按 SKU 合并增量再传入。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkuStockChange {

    /** SKU ID。 */
    private Long id;

    /** 库存增量：正数入库、负数出库。 */
    private Integer delta;
}
