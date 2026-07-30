package com.stellar.product;

import com.stellar.entity.Sku;
import com.stellar.exception.BaseException;
import com.stellar.service.SkuStockService;
import com.stellar.service.SkuService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

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
