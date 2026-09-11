package com.stellar.service;

import com.stellar.entity.Sku;
import com.stellar.result.PageResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 库存管理服务（管理端）。
 * 提供库存分页查询、调整、批量操作及出入库日志追溯。
 */
public interface InventoryService {

    /** SKU 库存分页查询。 */
    PageResult pageInventory(Integer page, Integer pageSize, String name, Integer lowStock);

    /**
     * 调整单个 SKU 库存。
     *
     * @param skuId    SKU ID
     * @param delta    变动数量（正数入库，负数出库）
     * @param warnStock 预警库存（null 表示不修改）
     * @param remark   备注
     */
    void updateStock(Long skuId, Integer delta, Integer warnStock, String remark);

    /**
     * 批量调整库存。
     *
     * @param items 批量调整项列表，每项包含 skuId、delta、warnStock、remark
     */
    void batchUpdateStock(List<Map<String, Object>> items);

    /**
     * 出入库日志分页查询（按时间倒序）。
     *
     * @param skuId   SKU ID（精确筛选，可空）
     * @param keyword 商品名称模糊（可空）
     * @param type    操作类型：1 入库，2 出库（可空）
     * @param begin   操作时间起（含，可空）
     * @param end     操作时间止（含，可空）
     */
    PageResult pageStockLog(Long skuId, String keyword, Integer type,
                            LocalDateTime begin, LocalDateTime end,
                            Integer page, Integer pageSize);
}