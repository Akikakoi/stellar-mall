package com.stellar.ragsync.scheduler;

import com.stellar.ragsync.service.RagSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RAG 同步定时调度器：每隔 30 秒自动拉取一批待处理的 outbox 记录并同步到 RAG 端。
 * <p>
 * 和手动 "一键处理全部" 共享同一个 processPendingBatch 逻辑，
 * 区别是调度器每次只处理固定条数（避免单次阻塞过久）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagSyncScheduler {

    /** 每次调度最多处理条数，防止某次同步大量积压导致线程长时间占用。 */
    private static final int BATCH_SIZE = 30;

    private final RagSyncService ragSyncService;

    /**
     * 每 30 秒执行一次：拉取 synced=0 & failed=0 的 outbox 记录，逐条同步。
     * <p>
     * initialDelay = 15s —— 给应用启动预留缓冲时间（连接池、RAG 服务就绪等）。
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void processOutbox() {
        try {
            int processed = ragSyncService.processPendingBatch(BATCH_SIZE);
            if (processed > 0) {
                log.info("[RagSyncScheduler] 本轮处理 {} 条 outbox 记录", processed);
            }
        } catch (Exception e) {
            // 调度异常只打日志，不中断定时任务线程
            log.error("[RagSyncScheduler] 定时同步异常", e);
        }
    }
}
