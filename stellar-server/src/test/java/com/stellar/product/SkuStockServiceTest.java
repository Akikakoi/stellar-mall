package com.stellar.product;

import com.stellar.entity.Sku;
import com.stellar.exception.BaseException;
import com.stellar.service.SkuStockService;
import com.stellar.service.SkuService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-M1 RED阶段：SKU 库存乐观锁（stock + version）。
 *   - 扣减：成功时 stock--, version++
 *   - 并发：两次 version 冲突 → 第二次必须失败或重试成功但不能超卖
 *   - 回滚：deductRollback 后 stock++, version++
 *   - 扣不足：抛 StockInsufficientException / BaseException
 */
@SpringBootTest
class SkuStockServiceTest {

    @Autowired(required = false)
    private SkuStockService skuStockService;
    @Autowired(required = false)
    private SkuService skuService;

    /**
     * 本测试写入真实 H2，spu_id 是硬编码的虚拟值 1L。
     * H2 是测试间共享的（DB_CLOSE_DELAY=-1），若不清理会污染后续按 spu_id 聚合的用例
     * （例如 SpuSkuServiceTest 新建的 SPU 恰好也拿到 id=1，会把这里的孤儿 SKU 一起聚合）。
     */
    private final List<Long> createdSkuIds = new ArrayList<>();

    @AfterEach
    void cleanUpSkus() {
        for (Long id : createdSkuIds) {
            try {
                skuService.deleteById(id);
            } catch (Exception ignored) {
                // 清理失败不影响用例本身
            }
        }
        createdSkuIds.clear();
    }

    private Sku createSkuWithStock(int stock) {
        Sku s = new Sku();
        s.setSpuId(1L);
        s.setName("库存-" + stock + "-" + System.currentTimeMillis());
        s.setSpecs("测试");
        s.setPrice(BigDecimal.ONE);
        s.setStock(stock);
        s.setSort(1);
        s.setStatus(1);
        skuService.save(s);
        createdSkuIds.add(s.getId());
        return s;
    }

    @Test
    void deductStock_decrementsStock_andIncrementsVersion() {
        assertNotNull(skuStockService, "RED失败：SkuStockService 未注册");
        Sku s = createSkuWithStock(10);

        skuStockService.deduct(s.getId(), 3);
        Sku got = skuService.getById(s.getId());
        assertEquals(Integer.valueOf(7), got.getStock());
        assertEquals(Integer.valueOf(1), got.getVersion(), "扣减成功 version 必须 +1");
    }

    @Test
    void deductStock_whenInsufficient_throwsBaseException() {
        assertNotNull(skuStockService);
        Sku s = createSkuWithStock(2);

        assertThrows(BaseException.class, () -> skuStockService.deduct(s.getId(), 3),
                "库存不足必须抛出 BaseException（子类 StockInsufficientException 也行）");
        Sku got = skuService.getById(s.getId());
        assertEquals(Integer.valueOf(2), got.getStock(), "扣失败后库存必须保持原值");
        assertEquals(Integer.valueOf(0), got.getVersion(), "扣失败后 version 不能递增");
    }

    @Test
    void rollbackStock_incrementsStock_andIncrementsVersion() {
        assertNotNull(skuStockService);
        Sku s = createSkuWithStock(10);
        skuStockService.deduct(s.getId(), 4); // stock=6, v=1

        skuStockService.rollback(s.getId(), 4);
        Sku got = skuService.getById(s.getId());
        assertEquals(Integer.valueOf(10), got.getStock());
        assertEquals(Integer.valueOf(2), got.getVersion(), "回滚也必须推进 version，避免 ABA");
    }

    @Test
    void concurrentDeduct_noOversell() throws InterruptedException {
        assertNotNull(skuStockService);
        Sku s = createSkuWithStock(5);

        // 10 个并发线程各扣 1，最终成功 5 次，失败 5 次；stock 最终 = 0
        int threads = 10;
        int[] successCount = {0};
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            ts[i] = new Thread(() -> {
                try {
                    skuStockService.deduct(s.getId(), 1);
                    synchronized (successCount) { successCount[0]++; }
                } catch (Exception ignored) { }
            });
        }
        for (Thread t : ts) t.start();
        for (Thread t : ts) t.join();

        Sku got = skuService.getById(s.getId());
        assertEquals(5, successCount[0], "恰好 5 次成功，不能超卖");
        assertEquals(Integer.valueOf(0), got.getStock(), "最终库存必须为 0，不能负数");
    }
}
