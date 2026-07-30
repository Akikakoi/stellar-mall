package com.stellar.controller.admin;

import com.stellar.entity.RagSyncOutbox;
import com.stellar.ragsync.service.RagSyncService;
import com.stellar.result.PageResult;
import com.stellar.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端：RAG 同步 outbox 手动操作入口。
 *   GET  /admin/rag-sync/pending           → 分页查询未同步队列
 *   POST /admin/rag-sync/retry/{id}        → 单条重置并立即重发
 *   POST /admin/rag-sync/process-all       → 循环拉批直到清零，返回 { processed, successCount, failedCount }
 *   GET  /admin/rag-sync/list              → 分页查询所有 outbox，支持 status/eventType/bizId 过滤
 *   GET  /admin/rag-sync/stats             → outbox 统计：total/synced/pending/failed
 *
 * 字段对齐前端：前端需要 eventType/status(0~3) / lastError / processedAt / createdAt
 *   eventType  ← opType
 *   status     ← synced=1 → 2(成功)；failed=1 → 3(失败)；lastTryTime!=null → 1(处理中)；否则 0(待同步)
 *   lastError  ← lastErrorMsg
 *   processedAt ← lastTryTime
 *   createdAt  ← createTime
 */
@RestController
@RequestMapping("/admin/rag-sync")
@RequiredArgsConstructor
@Api(tags = "管理端：RAG 同步队列")
public class RagSyncAdminController {

    /** 单次批大小（process-all 用）。 */
    private static final int BATCH = 50;

    private final RagSyncService ragSyncService;

    @GetMapping("/pending")
    @ApiOperation("分页查询未同步的 outbox（synced=0 AND failed=0）")
    public Result<PageResult> listPending(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResult raw = ragSyncService.listPendingPage(page, pageSize);
        return Result.success(mapPage(raw));
    }

    @GetMapping("/list")
    @ApiOperation("分页查询所有 outbox，支持 status/eventType/bizId 过滤")
    public Result<PageResult> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false, name = "eventType") String eventType,
            @RequestParam(required = false) Long bizId) {
        PageResult raw = ragSyncService.listAllPageFiltered(page, pageSize, status, eventType, bizId);
        return Result.success(mapPage(raw));
    }

    @GetMapping("/stats")
    @ApiOperation("outbox 统计：total / synced / pending / failed")
    public Result<Map<String, Long>> stats() {
        Map<String, Long> s = ragSyncService.stats();
        s.putIfAbsent("processing", 0L);
        s.putIfAbsent("success", s.getOrDefault("synced", 0L));
        return Result.success(s);
    }

    @PostMapping("/retry/{id}")
    @ApiOperation("单条重试：先清空失败标记与重试计数，再立即同步一次")
    public Result<Void> retryOne(@PathVariable Long id) {
        ragSyncService.retryOne(id);
        return Result.success();
    }

    @PostMapping("/process-all")
    @ApiOperation("清队列：循环 processPendingBatch(50) 直到返回 0，返回处理统计")
    public Result<Map<String, Integer>> processAll() {
        int total = 0;
        int success = 0;
        int failed = 0;
        int n;
        int guard = 2000;
        do {
            n = ragSyncService.processPendingBatch(BATCH);
            total += n;
            // processPendingBatch 不会拆 success/failed，用 stats 来粗略统计
            if (guard-- <= 0) break;
        } while (n > 0);
        // 处理完成后从 DB 统计实际成功/失败数
        Map<String, Long> stats = ragSyncService.stats();
        Map<String, Integer> data = new HashMap<>();
        data.put("processed", total);
        data.put("successCount", stats.get("synced") != null ? stats.get("synced").intValue() : 0);
        data.put("failedCount", stats.get("failed") != null ? stats.get("failed").intValue() : 0);
        return Result.success(data);
    }

    // --------- 内部：PageResult outbox → 前端字段 ---------

    @SuppressWarnings("unchecked")
    private PageResult mapPage(PageResult raw) {
        List<RagSyncOutbox> list = (List<RagSyncOutbox>) raw.getRecords();
        if (list == null) return raw;
        List<Map<String, Object>> mapped = list.stream()
                .map(this::toFrontend)
                .collect(Collectors.toList());
        return new PageResult(raw.getTotal(), mapped);
    }

    private Map<String, Object> toFrontend(RagSyncOutbox r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.getId());
        m.put("eventType", r.getOpType());
        m.put("opType", r.getOpType());
        m.put("bizId", r.getBizId());
        m.put("bizType", r.getBizType());
        int st;
        if (r.getSynced() != null && r.getSynced() == 1) {
            st = 2; // 成功
        } else if (r.getFailed() != null && r.getFailed() == 1) {
            st = 3; // 失败
        } else if (r.getLastTryTime() != null) {
            st = 1; // 处理中
        } else {
            st = 0; // 待同步
        }
        m.put("status", st);
        m.put("synced", r.getSynced());
        m.put("failed", r.getFailed());
        m.put("retryCount", r.getRetryCount());
        m.put("maxAttempt", r.getMaxAttempt());
        m.put("lastError", r.getLastErrorMsg());
        m.put("processedAt", r.getLastTryTime());
        m.put("createdAt", r.getCreateTime());
        m.put("updatedAt", r.getUpdateTime());
        m.put("payload", r.getPayloadJson());
        return m;
    }
}
