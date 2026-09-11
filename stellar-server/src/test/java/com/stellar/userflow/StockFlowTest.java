package com.stellar.userflow;

import com.stellar.entity.Sku;
import com.stellar.entity.StockLog;
import com.stellar.exception.BaseException;
import com.stellar.exception.StockInsufficientException;
import com.stellar.mapper.SkuMapper;
import com.stellar.mapper.StockLogMapper;
import com.stellar.service.impl.SkuStockServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * M2-J-G4 RED(a): 纯 Mockito 测试 SkuStockService deduct + rollback 流程。
 * 不启动 Spring，@ExtendWith(MockitoExtension.class)，全量 Mock。
 *
 * <p>2026-08-31 并发修复后重构：生产代码已从“getById 读 version + UPDATE WHERE version 重试”
 * 改为单条原子条件扣减（deductStockAtomic / rollbackStockAtomic，RR 下快照读陷阱的修复），
 * 本测试同步对齐新行为，不再断言已删除的 deductStockWithVersion / MAX_RETRY 重试逻辑。</p>
 */
@ExtendWith(MockitoExtension.class)
class StockFlowTest {

    /** 关联业务单号：写入出入库日志，断言它确实从入参落到流水记录 */
    private static final String BIZ_NO = "UNITTEST";

    @Mock
    private SkuMapper skuMapper;

    @Mock
    private StockLogMapper stockLogMapper;

    @InjectMocks
    private SkuStockServiceImpl skuStockService;

    private Sku buildSku(Long id, int stock, int version) {
        return Sku.builder()
                .id(id)
                .spuId(1L)
                .name("测试SKU-" + id)
                .specs("默认规格")
                .price(BigDecimal.valueOf(999))
                .stock(stock)
                .version(version)
                .status(1)
                .build();
    }

    // ========== deduct 相关 ==========

    @Test
    void deduct_success_callsDeductStockAtomic_once() {
        // 原子扣减命中（rows=1）→ 不再读 getById
        when(skuMapper.deductStockAtomic(100L, 3)).thenReturn(1);

        assertDoesNotThrow(() -> skuStockService.deduct(100L, 3, BIZ_NO));

        verify(skuMapper, times(1)).deductStockAtomic(100L, 3);
        // 成功后须记录出库流水（读取当前库存 + 写一条 SALE_OUT，且带上业务单号）
        verify(skuMapper, times(1)).getById(100L);
        ArgumentCaptor<StockLog> logCaptor = ArgumentCaptor.forClass(StockLog.class);
        verify(stockLogMapper, times(1)).insert(logCaptor.capture());
        StockLog written = logCaptor.getValue();
        assertEquals(BIZ_NO, written.getBusinessNo(), "出库流水必须记录业务单号");
        assertEquals("SALE_OUT", written.getBusinessType());
        assertEquals(Integer.valueOf(-3), written.getQuantity());
    }

    @Test
    void deduct_whenStockLessThanQty_throwsStockInsufficientException() {
        // 原子扣减 rows=0（库存不足）→ getById 确认 SKU 存在且 stock < qty → 抛库存不足
        Sku s = buildSku(101L, 2, 0);
        when(skuMapper.deductStockAtomic(101L, 5)).thenReturn(0);
        when(skuMapper.getById(101L)).thenReturn(s);

        StockInsufficientException ex = assertThrows(StockInsufficientException.class,
                () -> skuStockService.deduct(101L, 5, BIZ_NO),
                "库存不足必须抛 StockInsufficientException");
        assertTrue(ex.getMessage().contains("skuId=101"));
    }

    @Test
    void deduct_whenSkuNotFound_throwsBaseException() {
        // 原子扣减 rows=0（记录不存在）→ getById 返回 null → 抛 SKU_NOT_FOUND
        when(skuMapper.deductStockAtomic(9999L, 1)).thenReturn(0);
        when(skuMapper.getById(9999L)).thenReturn(null);

        assertThrows(BaseException.class, () -> skuStockService.deduct(9999L, 1, BIZ_NO));
        verify(skuMapper, never()).deductStockWithVersion(anyLong(), anyInt(), anyInt());
    }

    // ========== rollback 相关 ==========

    @Test
    void rollback_success_callsRollbackStockAtomic_once() {
        when(skuMapper.rollbackStockAtomic(200L, 3)).thenReturn(1);

        assertDoesNotThrow(() -> skuStockService.rollback(200L, 3, BIZ_NO));

        verify(skuMapper, times(1)).rollbackStockAtomic(200L, 3);
        // 成功后须记录入库流水（读取当前库存 + 写一条 ORDER_ROLLBACK，且带上业务单号）
        verify(skuMapper, times(1)).getById(200L);
        ArgumentCaptor<StockLog> logCaptor = ArgumentCaptor.forClass(StockLog.class);
        verify(stockLogMapper, times(1)).insert(logCaptor.capture());
        StockLog written = logCaptor.getValue();
        assertEquals(BIZ_NO, written.getBusinessNo(), "入库流水必须记录业务单号");
        assertEquals("ORDER_ROLLBACK", written.getBusinessType());
        assertEquals(Integer.valueOf(3), written.getQuantity());
    }

    @Test
    void rollback_whenSkuNotFound_throwsBaseException() {
        // 回滚 rows=0 仅可能表示 SKU 不存在
        when(skuMapper.rollbackStockAtomic(9998L, 2)).thenReturn(0);

        assertThrows(BaseException.class, () -> skuStockService.rollback(9998L, 2, BIZ_NO));
    }

    // ========== 参数非法 ==========

    @Test
    void deduct_withIllegalParams_throwsBaseException() {
        assertThrows(BaseException.class, () -> skuStockService.deduct(null, 1, BIZ_NO));
        assertThrows(BaseException.class, () -> skuStockService.deduct(1L, 0, BIZ_NO));
        assertThrows(BaseException.class, () -> skuStockService.deduct(1L, -1, BIZ_NO));
    }

    @Test
    void rollback_withIllegalParams_throwsBaseException() {
        assertThrows(BaseException.class, () -> skuStockService.rollback(null, 1, BIZ_NO));
        assertThrows(BaseException.class, () -> skuStockService.rollback(1L, 0, BIZ_NO));
    }
}
