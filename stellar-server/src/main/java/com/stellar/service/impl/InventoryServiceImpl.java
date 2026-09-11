package com.stellar.service.impl;

import com.stellar.constant.MessageConstant;
import com.stellar.context.BaseContext;
import com.stellar.entity.Sku;
import com.stellar.entity.StockLog;
import com.stellar.exception.BaseException;
import com.stellar.mapper.SkuMapper;
import com.stellar.mapper.StockLogMapper;
import com.stellar.result.PageResult;
import com.stellar.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 库存管理服务实现。
 * <p>
 * 将原 InventoryController 中 JdbcTemplate 直接操作数据库的逻辑迁移至此，
 * 使用 MyBatis Mapper 操作，并自动记录库存变动流水。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final SkuMapper skuMapper;
    private final StockLogMapper stockLogMapper;

    @Override
    public PageResult pageInventory(Integer page, Integer pageSize, String name, Integer lowStock) {
        int offset = (page - 1) * pageSize;
        List<Sku> list = skuMapper.pageForInventory(name, lowStock, offset, pageSize);
        long total = skuMapper.countForInventory(name, lowStock);
        return new PageResult(total, list == null ? new ArrayList<>() : new ArrayList<>(list));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStock(Long skuId, Integer delta, Integer warnStock, String remark) {
        // 1. 查询当前 SKU
        Sku sku = skuMapper.getById(skuId);
        if (sku == null) {
            throw new BaseException(MessageConstant.SKU_NOT_FOUND);
        }

        int stockBefore = sku.getStock() == null ? 0 : sku.getStock();
        Long currentUser = BaseContext.getCurrentId();

        // 2. 调整库存
        if (delta != null && delta != 0) {
            // 出库调整（delta<0）须先校验库存充足，避免 GREATEST 静默截断导致流水 quantity 与实际变动不一致
            if (delta < 0 && stockBefore < -delta) {
                throw new BaseException(MessageConstant.STOCK_NOT_ENOUGH
                        + " (当前库存=" + stockBefore + ", 需求=" + (-delta) + ")");
            }
            // 使用 GREATEST(0, stock + delta) 防止库存为负，同时推进 version
            skuMapper.adjustStock(skuId, delta);
            // 重新读取以确保 stock_after 准确
            Sku updated = skuMapper.getById(skuId);
            int stockAfter = updated == null ? 0 : (updated.getStock() == null ? 0 : updated.getStock());

            // 确定变动类型
            int type = delta > 0 ? 1 : 2; // 1=入库 2=出库

            // 3. 记录流水
            StockLog log = StockLog.builder()
                    .skuId(skuId)
                    .type(type)
                    .quantity(delta)
                    .stockBefore(stockBefore)
                    .stockAfter(stockAfter)
                    .remark(remark)
                    .businessType("ADJUSTMENT")
                    .createTime(LocalDateTime.now())
                    .createUser(currentUser == null ? 0L : currentUser)
                    .build();
            stockLogMapper.insert(log);
        }

        // 4. 更新预警库存
        if (warnStock != null) {
            Sku updateSku = new Sku();
            updateSku.setId(skuId);
            updateSku.setWarnStock(warnStock);
            skuMapper.updateStockMeta(updateSku);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateStock(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (Map<String, Object> item : items) {
            Long skuId = Long.valueOf(item.get("skuId").toString());
            Integer delta = item.get("delta") != null ? Integer.valueOf(item.get("delta").toString()) : null;
            Integer warnStock = item.get("warnStock") != null ? Integer.valueOf(item.get("warnStock").toString()) : null;
            String remark = item.get("remark") != null ? item.get("remark").toString() : null;
            updateStock(skuId, delta, warnStock, remark);
        }
    }

    @Override
    public PageResult pageStockLog(Long skuId, String keyword, Integer type,
                                   LocalDateTime begin, LocalDateTime end,
                                   Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        List<StockLog> list = stockLogMapper.page(skuId, keyword, type, begin, end, offset, pageSize);
        long total = stockLogMapper.count(skuId, keyword, type, begin, end);
        return new PageResult(total, list == null ? new ArrayList<>() : new ArrayList<>(list));
    }
}