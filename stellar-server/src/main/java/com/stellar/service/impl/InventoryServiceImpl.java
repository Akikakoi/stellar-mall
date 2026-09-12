package com.stellar.service.impl;

import com.stellar.constant.MessageConstant;
import com.stellar.context.BaseContext;
import com.stellar.dto.SkuStockChange;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 库存管理服务实现。
 * <p>
 * 将原 InventoryController 中 JdbcTemplate 直接操作数据库的逻辑迁移至此，
 * 使用 MyBatis Mapper 操作，并自动记录库存变动流水。
 * </p>
 */
@Slf4j
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
            // updateStockMeta 不触发 @AutoFill，update_user 必须在此显式传入，
            // 否则 SQL 里的 #{updateUser} 取到 null，会把操作人写成 NULL
            updateSku.setUpdateUser(currentUser == null ? 0L : currentUser);
            skuMapper.updateStockMeta(updateSku);
        }
    }

    /**
     * 批量调整库存。
     *
     * <p>原实现是 for 循环调 {@link #updateStock}，每个 SKU 要 2 次查询 + 1 次更新 +
     * 1 次流水插入：N 个 SKU 就是 4N 次数据库往返。这里改成固定 3 次往返：</p>
     * <ol>
     *   <li>一次 {@code listByIds} 批量取 SKU（原来每个 SKU 一次 getById + 一次回查）；</li>
     *   <li>一次 CASE WHEN 批量 UPDATE 库存（合并同一 SKU 的多次调整）；</li>
     *   <li>一次 INSERT ... VALUES 批量写流水，再一次 CASE WHEN 批量更新预警库存。</li>
     * </ol>
     *
     * <p><b>流水为什么仍按条记录</b>：同一 SKU 在一个批次里可能被调整多次，逐条推演出各自的
     * stockBefore / stockAfter，与原逐条执行的语义保持一致（而不是多条都记同一个最终值）。</p>
     *
     * <p><b>已知语义差异</b>：原实现每个 SKU 调一次 adjustStock，version 就 +1 一次；
     * 合并成一条 UPDATE 后同一 SKU 的 version 只 +1。库存扣减走 {@code deductStockAtomic}
     * （只依赖 stock >= qty，不依赖 version），因此不影响并发正确性；
     * 一次批量操作视为一次库存变更，语义上更合理。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdateStock(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        // ================= 1. 解析入参 =================
        List<ParsedItem> parsed = new ArrayList<>(items.size());
        for (Map<String, Object> item : items) {
            Long skuId = Long.valueOf(item.get("skuId").toString());
            Integer delta = item.get("delta") != null ? Integer.valueOf(item.get("delta").toString()) : null;
            Integer warnStock = item.get("warnStock") != null ? Integer.valueOf(item.get("warnStock").toString()) : null;
            String remark = item.get("remark") != null ? item.get("remark").toString() : null;
            parsed.add(new ParsedItem(skuId, delta, warnStock, remark));
        }

        // ================= 2. 一次批量查询替代 N 次 getById =================
        List<Long> skuIds = parsed.stream().map(p -> p.skuId).distinct().collect(Collectors.toList());
        List<Sku> skus = skuMapper.listByIds(skuIds);
        Map<Long, Sku> skuMap = (skus == null ? new ArrayList<Sku>() : skus).stream()
                .collect(Collectors.toMap(Sku::getId, s -> s, (a, b) -> a));

        Long currentUser = BaseContext.getCurrentId();
        long operator = currentUser == null ? 0L : currentUser;

        // ================= 3. 推演流水 + 合并增量 =================
        // balance：每个 SKU 推演到当前的库存；mergedDelta：合并后的增量（同一 SKU 只出现一次）
        Map<Long, Integer> balance = new HashMap<>();
        Map<Long, Integer> mergedDelta = new LinkedHashMap<>();
        Map<Long, Integer> mergedWarnStock = new LinkedHashMap<>();
        List<StockLog> logs = new ArrayList<>(parsed.size());

        for (ParsedItem p : parsed) {
            Sku sku = skuMap.get(p.skuId);
            if (sku == null) {
                throw new BaseException(MessageConstant.SKU_NOT_FOUND);
            }

            if (p.delta != null && p.delta != 0) {
                int stockBefore = balance.computeIfAbsent(p.skuId,
                        id -> sku.getStock() == null ? 0 : sku.getStock());

                // 出库校验口径与单条 updateStock 完全一致：任何一步会扣成负数就整批回滚，
                // 不允许交给 GREATEST 静默截断（那会导致流水的 quantity 与实际变动对不上）
                if (p.delta < 0 && stockBefore < -p.delta) {
                    throw new BaseException(MessageConstant.STOCK_NOT_ENOUGH
                            + " (当前库存=" + stockBefore + ", 需求=" + (-p.delta) + ")");
                }

                int stockAfter = stockBefore + p.delta;
                balance.put(p.skuId, stockAfter);
                mergedDelta.merge(p.skuId, p.delta, Integer::sum);

                logs.add(StockLog.builder()
                        .skuId(p.skuId)
                        .type(p.delta > 0 ? 1 : 2)
                        .quantity(p.delta)
                        .stockBefore(stockBefore)
                        .stockAfter(stockAfter)
                        .remark(p.remark)
                        .businessType("ADJUSTMENT")
                        .createTime(LocalDateTime.now())
                        .createUser(operator)
                        .build());
            }

            // 同一 SKU 多次带 warnStock 时以最后一次为准
            if (p.warnStock != null) {
                mergedWarnStock.put(p.skuId, p.warnStock);
            }
        }

        // ================= 4. 三次往返落地全部改动 =================
        if (!mergedDelta.isEmpty()) {
            List<SkuStockChange> changes = mergedDelta.entrySet().stream()
                    .map(e -> new SkuStockChange(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
            skuMapper.adjustStockBatch(changes);
        }
        if (!logs.isEmpty()) {
            stockLogMapper.insertBatch(logs);
        }
        if (!mergedWarnStock.isEmpty()) {
            List<Sku> metas = mergedWarnStock.entrySet().stream()
                    .map(e -> {
                        Sku meta = new Sku();
                        meta.setId(e.getKey());
                        meta.setWarnStock(e.getValue());
                        return meta;
                    })
                    .collect(Collectors.toList());
            skuMapper.updateStockMetaBatch(metas, operator);
        }

        log.info("[库存批量调整] 入参 {} 条，调整 {} 个 SKU，写流水 {} 条，更新预警 {} 个（操作人 {}）",
                items.size(), mergedDelta.size(), logs.size(), mergedWarnStock.size(), operator);
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

    /** 批量入参解析结果（仅内部使用）。 */
    private static final class ParsedItem {
        final Long skuId;
        final Integer delta;
        final Integer warnStock;
        final String remark;

        ParsedItem(Long skuId, Integer delta, Integer warnStock, String remark) {
            this.skuId = skuId;
            this.delta = delta;
            this.warnStock = warnStock;
            this.remark = remark;
        }
    }
}
