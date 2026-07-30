package com.stellar.userflow;

import com.stellar.entity.Sku;
import com.stellar.exception.BaseException;
import com.stellar.exception.StockInsufficientException;
import com.stellar.mapper.SkuMapper;
import com.stellar.service.impl.SkuStockServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 */
@ExtendWith(MockitoExtension.class)
class StockFlowTest {

    @Mock
    private SkuMapper skuMapper;

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
    void deduct_success_callsDeductStockWithVersion_once() {
        Sku s = buildSku(100L, 10, 0);
        when(skuMapper.getById(100L)).thenReturn(s);
        when(skuMapper.deductStockWithVersion(100L, 0, 3)).thenReturn(1);

        assertDoesNotThrow(() -> skuStockService.deduct(100L, 3));

        verify(skuMapper, times(1)).deductStockWithVersion(100L, 0, 3);
    }

    @Test
    void deduct_whenStockLessThanQty_throwsStockInsufficientException() {
        Sku s = buildSku(101L, 2, 0);
        when(skuMapper.getById(101L)).thenReturn(s);

        assertThrows(StockInsufficientException.class,
                () -> skuStockService.deduct(101L, 5),
                "库存不足必须抛 StockInsufficientException");

        verify(skuMapper, never()).deductStockWithVersion(anyLong(), anyInt(), anyInt());
    }

    @Test
    void deduct_whenVersionConflict_thenRetryUntilSuccess() {
        // 第 1 次 getById → v=0, deduct 返回 0（冲突）
        // 第 2 次 getById → v=1, deduct 返回 1（成功）
        Sku s0 = buildSku(102L, 10, 0);
        Sku s1 = buildSku(102L, 10, 1);
        when(skuMapper.getById(102L)).thenReturn(s0, s1);
        when(skuMapper.deductStockWithVersion(102L, 0, 2)).thenReturn(0);
        when(skuMapper.deductStockWithVersion(102L, 1, 2)).thenReturn(1);

        assertDoesNotThrow(() -> skuStockService.deduct(102L, 2));

        verify(skuMapper, times(2)).getById(102L);
        verify(skuMapper, times(1)).deductStockWithVersion(102L, 0, 2);
        verify(skuMapper, times(1)).deductStockWithVersion(102L, 1, 2);
    }

    @Test
    void deduct_whenMaxRetryStillConflict_throwsStockInsufficientException() {
        // 20 次都冲突 → 抛异常（SkuStockServiceImpl.MAX_RETRY = 20）
        Sku s = buildSku(103L, 10, 7);
        when(skuMapper.getById(103L)).thenReturn(s);
        when(skuMapper.deductStockWithVersion(anyLong(), anyInt(), anyInt())).thenReturn(0);

        assertThrows(StockInsufficientException.class,
                () -> skuStockService.deduct(103L, 1),
                "20 次版本冲突都失败必须抛 StockInsufficientException");

        verify(skuMapper, times(20)).deductStockWithVersion(anyLong(), anyInt(), anyInt());
    }

    @Test
    void deduct_whenSkuNotFound_throwsBaseException() {
        when(skuMapper.getById(9999L)).thenReturn(null);
        assertThrows(BaseException.class, () -> skuStockService.deduct(9999L, 1));
    }

    // ========== rollback 相关 ==========

    @Test
    void rollback_success_callsRollbackStockWithVersion_once() {
        Sku s = buildSku(200L, 7, 1);
        when(skuMapper.getById(200L)).thenReturn(s);
        when(skuMapper.rollbackStockWithVersion(200L, 1, 3)).thenReturn(1);

        assertDoesNotThrow(() -> skuStockService.rollback(200L, 3));

        verify(skuMapper, times(1)).rollbackStockWithVersion(200L, 1, 3);
    }

    @Test
    void rollback_whenVersionConflict_retriesAndSucceeds() {
        Sku s0 = buildSku(201L, 6, 1);
        Sku s1 = buildSku(201L, 6, 2);
        when(skuMapper.getById(201L)).thenReturn(s0, s1);
        when(skuMapper.rollbackStockWithVersion(201L, 1, 4)).thenReturn(0);
        when(skuMapper.rollbackStockWithVersion(201L, 2, 4)).thenReturn(1);

        assertDoesNotThrow(() -> skuStockService.rollback(201L, 4));
        verify(skuMapper, times(2)).getById(201L);
    }

    @Test
    void rollback_whenMaxRetryConflict_throwsBaseException() {
        Sku s = buildSku(202L, 5, 3);
        when(skuMapper.getById(202L)).thenReturn(s);
        when(skuMapper.rollbackStockWithVersion(anyLong(), anyInt(), anyInt())).thenReturn(0);

        assertThrows(BaseException.class,
                () -> skuStockService.rollback(202L, 2),
                "20 次版本冲突都失败必须抛 BaseException");
    }

    // ========== 参数非法 ==========

    @Test
    void deduct_withIllegalParams_throwsBaseException() {
        assertThrows(BaseException.class, () -> skuStockService.deduct(null, 1));
        assertThrows(BaseException.class, () -> skuStockService.deduct(1L, 0));
        assertThrows(BaseException.class, () -> skuStockService.deduct(1L, -1));
    }

    @Test
    void rollback_withIllegalParams_throwsBaseException() {
        assertThrows(BaseException.class, () -> skuStockService.rollback(null, 1));
        assertThrows(BaseException.class, () -> skuStockService.rollback(1L, 0));
    }
}
