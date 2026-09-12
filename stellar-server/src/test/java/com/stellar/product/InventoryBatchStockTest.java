package com.stellar.product;

import com.stellar.entity.Sku;
import com.stellar.entity.StockLog;
import com.stellar.exception.BaseException;
import com.stellar.mapper.StockLogMapper;
import com.stellar.service.InventoryService;
import com.stellar.service.SkuService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 库存批量调整（{@code InventoryServiceImpl#batchUpdateStock}）端到端验证。
 *
 * <p>改造前该方法是 for 循环调 updateStock（每 SKU 4 次数据库往返），改造后合并为
 * 一次 listByIds + 一次 CASE WHEN 批量 UPDATE + 一次批量 INSERT。
 * 这里在 H2 上真正执行新写的批量 SQL，验证三件事：</p>
 * <ol>
 *   <li>库存与预警库存落库正确（CASE WHEN 批量 UPDATE 的两条语句都能跑通）；</li>
 *   <li>流水条数与 stockBefore / stockAfter 与逐条执行语义一致（同一 SKU 多次调整各自记中间值）；</li>
 *   <li>批次内任一 SKU 库存不足时整批回滚，不留部分改动。</li>
 * </ol>
 */
@SpringBootTest
class InventoryBatchStockTest {

    @Autowired(required = false)
    private InventoryService inventoryService;
    @Autowired(required = false)
    private SkuService skuService;
    @Autowired(required = false)
    private StockLogMapper stockLogMapper;
    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdSkuIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        for (Long id : createdSkuIds) {
            try {
                skuService.deleteById(id);
            } catch (Exception ignored) {
                // 清理失败不影响用例结论
            }
            try {
                jdbcTemplate.update("DELETE FROM stellar_stock_log WHERE sku_id = ?", id);
            } catch (Exception ignored) {
                // 同上
            }
        }
        createdSkuIds.clear();
    }

    @Test
    @DisplayName("批量入库 + 出库：库存、流水、预警库存都落对")
    void batchUpdateStock_appliesDeltasAndWritesLogs() {
        Sku a = createSku(100);
        Sku b = createSku(50);

        inventoryService.batchUpdateStock(List.of(
                item(a.getId(), 20, 5),
                item(b.getId(), -10, null)));

        assertEquals(120, skuService.getById(a.getId()).getStock());
        assertEquals(40, skuService.getById(b.getId()).getStock());
        assertEquals(5, skuService.getById(a.getId()).getWarnStock());

        List<StockLog> logsA = logsOf(a.getId());
        assertEquals(1, logsA.size());
        assertEquals(100, logsA.get(0).getStockBefore());
        assertEquals(120, logsA.get(0).getStockAfter());
        assertEquals(20, logsA.get(0).getQuantity());
        assertEquals(1, logsA.get(0).getType(), "入库应记为 type=1");
        assertEquals("ADJUSTMENT", logsA.get(0).getBusinessType());

        List<StockLog> logsB = logsOf(b.getId());
        assertEquals(1, logsB.size());
        assertEquals(50, logsB.get(0).getStockBefore());
        assertEquals(40, logsB.get(0).getStockAfter());
        assertEquals(-10, logsB.get(0).getQuantity());
        assertEquals(2, logsB.get(0).getType(), "出库应记为 type=2");
    }

    @Test
    @DisplayName("同一 SKU 在批次内多次调整：流水逐条记中间值，库存合并为一次更新")
    void batchUpdateStock_sameSkuTwice_keepsPerStepSnapshot() {
        Sku s = createSku(10);

        inventoryService.batchUpdateStock(List.of(
                item(s.getId(), 5, null),
                item(s.getId(), -3, null)));

        // 合并增量 +2，最终库存 12
        assertEquals(12, skuService.getById(s.getId()).getStock());

        List<StockLog> logs = logsOf(s.getId());
        assertEquals(2, logs.size(), "两次调整应写两条流水，而不是合并成一条");
        // 查询按 create_time DESC, id DESC —— 后插入的排前面
        assertEquals(15, logs.get(0).getStockBefore());
        assertEquals(12, logs.get(0).getStockAfter());
        assertEquals(10, logs.get(1).getStockBefore());
        assertEquals(15, logs.get(1).getStockAfter());
    }

    @Test
    @DisplayName("批次内任一 SKU 库存不足：整批回滚，前一个 SKU 的改动不落地")
    void batchUpdateStock_insufficientStock_rollsBackWholeBatch() {
        Sku a = createSku(100);
        Sku b = createSku(5);

        assertThrows(BaseException.class, () -> inventoryService.batchUpdateStock(List.of(
                item(a.getId(), 20, null),
                item(b.getId(), -50, null))));

        assertEquals(100, skuService.getById(a.getId()).getStock(), "a 的 +20 必须随整批一起回滚");
        assertEquals(5, skuService.getById(b.getId()).getStock());
        assertTrue(logsOf(a.getId()).isEmpty(), "回滚后不应留下流水");
    }

    @Test
    @DisplayName("只带 warnStock：库存不动，也不写流水")
    void batchUpdateStock_warnStockOnly_leavesStockUntouched() {
        Sku s = createSku(30);

        Map<String, Object> onlyWarn = new HashMap<>();
        onlyWarn.put("skuId", s.getId());
        onlyWarn.put("warnStock", 8);

        inventoryService.batchUpdateStock(List.of(onlyWarn));

        Sku after = skuService.getById(s.getId());
        assertEquals(30, after.getStock());
        assertEquals(8, after.getWarnStock());
        assertTrue(logsOf(s.getId()).isEmpty());
    }

    // ======================== 辅助 ========================

    private Sku createSku(int stock) {
        Sku s = new Sku();
        s.setSpuId(1L);
        s.setName("批量库存-" + System.currentTimeMillis() + "-" + createdSkuIds.size());
        s.setSpecs("测试");
        s.setPrice(BigDecimal.ONE);
        s.setStock(stock);
        s.setSort(1);
        s.setStatus(1);
        skuService.save(s);
        createdSkuIds.add(s.getId());
        return s;
    }

    private Map<String, Object> item(Long skuId, Integer delta, Integer warnStock) {
        Map<String, Object> m = new HashMap<>();
        m.put("skuId", skuId);
        m.put("delta", delta);
        m.put("warnStock", warnStock);
        m.put("remark", "批量测试");
        return m;
    }

    private List<StockLog> logsOf(Long skuId) {
        List<StockLog> logs = stockLogMapper.page(skuId, null, null, null, null, 0, 50);
        return logs == null ? List.of() : logs;
    }
}
