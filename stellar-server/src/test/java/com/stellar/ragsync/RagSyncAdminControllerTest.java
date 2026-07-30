package com.stellar.ragsync;

import com.stellar.controller.admin.RagSyncAdminController;
import com.stellar.entity.RagSyncOutbox;
import com.stellar.ragsync.service.RagSyncService;
import com.stellar.result.PageResult;
import com.stellar.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * M2-J-G3 RED+GREEN：管理端 RagSyncAdminController 行为契约（纯 Mockito，不启 Spring）。
 *
 * 契约：
 *  1. GET  /admin/rag-sync/pending?page=&pageSize=   → listPendingPage(page, pageSize)
 *  2. GET  /admin/rag-sync/list?status=&eventType=&bizId= → listAllPageFiltered(...)
 *  3. POST /admin/rag-sync/retry/{id}                → retryOne(id)
 *  4. POST /admin/rag-sync/process-all               → processPendingBatch 直到为 0，返回统计
 */
public class RagSyncAdminControllerTest {

    private RagSyncService svc;
    private RagSyncAdminController ctrl;
    private AtomicInteger pendingLeft;

    @BeforeEach
    void setUp() {
        svc = mock(RagSyncService.class);
        ctrl = new RagSyncAdminController(svc);
        pendingLeft = new AtomicInteger(0);

        Map<String, Long> statsMap = new HashMap<>();
        statsMap.put("synced", 100L);
        statsMap.put("failed", 5L);
        when(svc.stats()).thenReturn(statsMap);
    }

    @Test
    void listPending_delegatesToServiceAndWrapsInResultSuccess() {
        List<RagSyncOutbox> records = new ArrayList<>();
        records.add(RagSyncOutbox.builder()
                .id(1L).bizType("SPU").bizId(100L).opType("SAVE")
                .synced(0).failed(0).retryCount(0).maxAttempt(3)
                .createTime(LocalDateTime.now()).build());
        when(svc.listPendingPage(1, 10)).thenReturn(new PageResult(1L, records));

        Result<PageResult> r = ctrl.listPending(1, 10);

        assertNotNull(r);
        assertEquals(1, r.getCode());
        assertNotNull(r.getData());
        assertEquals(1L, r.getData().getTotal());
        assertEquals(1, r.getData().getRecords().size());

        verify(svc, times(1)).listPendingPage(eq(1), eq(10));
    }

    @Test
    void list_delegatesToServiceListAllPageFiltered() {
        when(svc.listAllPageFiltered(eq(1), eq(10), eq(2), eq("SAVE"), eq(100L)))
                .thenReturn(new PageResult(0L, List.of()));

        Result<PageResult> r = ctrl.list(1, 10, 2, "SAVE", 100L);

        assertNotNull(r);
        assertEquals(1, r.getCode());
        verify(svc, times(1)).listAllPageFiltered(eq(1), eq(10), eq(2), eq("SAVE"), eq(100L));
    }

    @Test
    void list_withNoFilters_delegatesWithNullParams() {
        when(svc.listAllPageFiltered(eq(1), eq(10), isNull(), isNull(), isNull()))
                .thenReturn(new PageResult(0L, List.of()));

        Result<PageResult> r = ctrl.list(1, 10, null, null, null);

        assertNotNull(r);
        verify(svc, times(1)).listAllPageFiltered(eq(1), eq(10), isNull(), isNull(), isNull());
    }

    @Test
    void retryOne_delegatesToServiceRetryOne() {
        Result<Void> r = ctrl.retryOne(99L);
        assertNotNull(r);
        assertEquals(1, r.getCode());
        verify(svc, times(1)).retryOne(eq(99L));
    }

    @Test
    void processAll_loopsUntilZero_returnsTotalProcessed() {
        pendingLeft.set(112);
        when(svc.processPendingBatch(anyInt())).thenAnswer(inv -> {
            int batch = inv.getArgument(0);
            int left = pendingLeft.get();
            int take = Math.min(left, Math.max(1, batch));
            pendingLeft.addAndGet(-take);
            return take;
        });

        Result<Map<String, Integer>> r = ctrl.processAll();
        assertNotNull(r);
        assertEquals(1, r.getCode());
        assertNotNull(r.getData());
        assertEquals(112, r.getData().get("processed"));
        // stats mock 返回 synced=100, failed=5
        assertEquals(100, r.getData().get("successCount"));
        assertEquals(5, r.getData().get("failedCount"));
    }
}
