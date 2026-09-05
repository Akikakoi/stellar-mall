package com.stellar.service;

import com.stellar.entity.Sku;
import com.stellar.entity.StockLog;
import com.stellar.result.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 库存管理服务（管理端）。
 * 提供库存分页查询、调整、批量操作及流水追溯。
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

    /** 查询指定 SKU 的库存变动流水。 */
    PageResult pageStockLog(Long skuId, Integer page, Integer pageSize);

    /** 查询所有 SKU 的库存变动流水。 */
    PageResult pageAllStockLog(Integer page, Integer pageSize);

    /**
     * 入库操作（采购入库 / 退货入库 / 盘盈入库）。
     *
     * @param skuId        SKU ID
     * @param quantity     入库数量（正数）
     * @param businessType 业务类型：PURCHASE_IN / RETURN_IN / INVENTORY_PROFIT
     * @param businessNo   关联业务单号（如采购单号）
     * @param remark       备注
     */
    void inbound(Long skuId, int quantity, String businessType, String businessNo, String remark);

    /**
     * 出库操作（销售出库 / 报废出库 / 盘亏出库）。
     *
     * @param skuId        SKU ID
     * @param quantity     出库数量（正数）
     * @param businessType 业务类型：SALE_OUT / SCRAP_OUT / INVENTORY_LOSS
     * @param businessNo   关联业务单号（如订单号）
     * @param remark       备注
     */
    void outbound(Long skuId, int quantity, String businessType, String businessNo, String remark);
}