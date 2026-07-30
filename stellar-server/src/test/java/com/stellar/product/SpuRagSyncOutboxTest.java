package com.stellar.product;

import com.stellar.dto.SpuSaveDTO;
import com.stellar.entity.Sku;
import com.stellar.entity.Spu;
import com.stellar.mapper.SkuMapper;
import com.stellar.mapper.SpuMapper;
import com.stellar.ragsync.service.RagSyncService;
import com.stellar.result.PageResult;
import com.stellar.service.SpuService;
import com.stellar.service.impl.SpuServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * P2-M2-J-G2 RED 阶段：
 *   SpuService 在 save/update/上下架/删除 后必须调用 RagSyncService.enqueueSpuSync()
 *   把 SPU 变更写入 RAG outbox 队列。
 *
 *   在 GREEN 之前本测试应「断言失败」—— 当前 SpuServiceImpl 还没有调用 enqueueSpuSync。
 *
 * 本测试为纯 Mockito 单元测试（不加 @SpringBootTest），不依赖 MySQL/Redis，秒级跑完。
 */
@ExtendWith(MockitoExtension.class)
class SpuRagSyncOutboxTest {

    @Mock
    private SpuMapper spuMapper;
    @Mock
    private SkuMapper skuMapper;

    /** 记录型 RagSyncService 替身：记录每次 enqueueSpuSync 的 bizId 和 opType。 */
    private static class RecordingRagSyncService implements RagSyncService {
        final java.util.List<Object[]> calls = new ArrayList<>();

        @Override public Long enqueueSpuSync(Long spuId, String opType) {
            calls.add(new Object[]{spuId, opType});
            return 1000L + calls.size();
        }
        @Override public void processPendingOne(Long outboxId) { }
        @Override public int processPendingBatch(int limit) { return 0; }
        @Override public PageResult listPendingPage(int page, int pageSize) {
            return new PageResult(0L, new ArrayList<>());
        }
        @Override public void retryOne(Long outboxId) { }
        @Override public PageResult listAllPage(int page, int pageSize) {
            return new PageResult(0L, new ArrayList<>());
        }
        @Override public Map<String, Long> stats() {
            return new HashMap<>();
        }
    }

    private RecordingRagSyncService ragSyncSpy;
    private SpuService spuService;

    @BeforeEach
    void setUp() {
        // 用 lenient stub：避免 UnnecessaryStubbingException
        lenient().when(spuMapper.insert(any(Spu.class))).thenAnswer(inv -> {
            Spu s = inv.getArgument(0);
            if (s.getId() == null) {
                // 手动回填 id（MyBatis normally does key backfill）
                try {
                    java.lang.reflect.Method setId = Spu.class.getMethod("setId", Long.class);
                    setId.invoke(s, System.nanoTime() & 0x7fffffffL);
                } catch (Exception ignored) { }
            }
            return 1;
        });
        lenient().doAnswer(inv -> {
            Sku s = inv.getArgument(0);
            if (s.getId() == null) {
                try {
                    java.lang.reflect.Method setId = Sku.class.getMethod("setId", Long.class);
                    setId.invoke(s, (System.nanoTime() + 1) & 0x7fffffffL);
                } catch (Exception ignored) { }
            }
            return 1;
        }).when(skuMapper).insert(any(Sku.class));
        lenient().when(spuMapper.update(any(Spu.class))).thenReturn(1);
        lenient().when(spuMapper.deleteById(any())).thenReturn(1);
        lenient().when(spuMapper.refreshAggregatesFromSku(any(), any(), any(), any(), any())).thenReturn(1);
        lenient().when(skuMapper.deleteBySpuId(any())).thenReturn(0);

        Spu base = new Spu();
        base.setMinPrice(BigDecimal.ZERO);
        base.setMaxPrice(BigDecimal.ZERO);
        base.setTotalStock(0);
        base.setSkuCount(0);
        base.setStatus(1);
        lenient().when(spuMapper.getById(any())).thenReturn(base);

        ragSyncSpy = new RecordingRagSyncService();
        spuService = new SpuServiceImpl(spuMapper, skuMapper, ragSyncSpy);
    }

    // ---- 辅助：最小 SpuSaveDTO ----
    private SpuSaveDTO minimalDto() {
        SpuSaveDTO dto = new SpuSaveDTO();
        dto.setName("单元测试电视");
        dto.setCategoryId(1L);
        dto.setStatus(1);
        dto.setDescriptionMd("# x");
        dto.setSkuList(Collections.emptyList());
        return dto;
    }

    @Test
    void saveWithSkus_invokesEnqueue_withOpSave() {
        Long id = spuService.saveWithSkus(minimalDto());
        assertNotNull(id, "save 必须返回 id（mocked by our insert answer）");
        long saveCount = ragSyncSpy.calls.stream()
                .filter(c -> "SAVE".equalsIgnoreCase(String.valueOf(c[1])))
                .count();
        assertTrue(saveCount >= 1,
                () -> "RED：saveWithSkus() 应该调用 ragSyncService.enqueueSpuSync(…, \"SAVE\")，实际调用：" + ragSyncSpy.calls.size());
    }

    @Test
    void updateWithSkus_invokesEnqueue_withOpSave() {
        Long id = spuService.saveWithSkus(minimalDto());
        ragSyncSpy.calls.clear();

        SpuSaveDTO upd = minimalDto();
        upd.setId(id);
        upd.setName("更新后");
        spuService.updateWithSkus(upd);

        long saveCount = ragSyncSpy.calls.stream()
                .filter(c -> "SAVE".equalsIgnoreCase(String.valueOf(c[1])))
                .count();
        assertTrue(saveCount >= 1,
                () -> "RED：updateWithSkus() 应该 enqueue SAVE，实际调用：" + ragSyncSpy.calls.size());
    }

    @Test
    void onOffShelf_invokesEnqueue_withOpOnOrOff() {
        Long id = spuService.saveWithSkus(minimalDto());
        ragSyncSpy.calls.clear();

        // 上架前：返回当前 status=0，才能触发真正的状态变更
        Spu beforeOn = new Spu();
        beforeOn.setStatus(0);
        when(spuMapper.getById(id)).thenReturn(beforeOn);
        spuService.onOffShelf(id, 1); // 上架
        long onCount = ragSyncSpy.calls.stream()
                .filter(c -> "ONSHELF".equalsIgnoreCase(String.valueOf(c[1])))
                .count();
        assertTrue(onCount >= 1,
                () -> "RED：上架应该 enqueue ONSHELF，实际调用：" + ragSyncSpy.calls);

        ragSyncSpy.calls.clear();

        // 下架前：返回当前 status=1，才能触发真正的状态变更
        Spu beforeOff = new Spu();
        beforeOff.setStatus(1);
        when(spuMapper.getById(id)).thenReturn(beforeOff);
        spuService.onOffShelf(id, 0); // 下架
        long offCount = ragSyncSpy.calls.stream()
                .filter(c -> "OFFSHELF".equalsIgnoreCase(String.valueOf(c[1])))
                .count();
        assertTrue(offCount >= 1,
                () -> "RED：下架应该 enqueue OFFSHELF，实际调用：" + ragSyncSpy.calls);
    }

    @Test
    void deleteById_invokesEnqueue_withOpDelete() {
        Long id = spuService.saveWithSkus(minimalDto());
        ragSyncSpy.calls.clear();

        spuService.deleteById(id);

        long delCount = ragSyncSpy.calls.stream()
                .filter(c -> "DELETE".equalsIgnoreCase(String.valueOf(c[1])))
                .count();
        assertTrue(delCount >= 1,
                () -> "RED：deleteById 应该 enqueue DELETE，实际调用：" + ragSyncSpy.calls);
    }
}
