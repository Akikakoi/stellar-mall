package com.stellar.ragsync.scheduler;

import com.stellar.ragsync.service.RagSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

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
     * 单机重入保护。
     * <p>手动「一键处理全部」与定时任务会共用同一批 outbox 记录，调度池扩容后也可能并行调度；
     * 这里用 CAS 保证同一时刻只有一轮在跑，避免同一条记录被两条线程同时推送。</p>
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 每 30 秒执行一次：拉取 synced=0 & failed=0 的 outbox 记录，逐条同步。
     * <p>
     * fixedDelay —— 上一轮结束（含同步耗时）后再等 30 秒，避免轮次叠加。
     * initialDelay = 15s —— 给应用启动预留缓冲时间（连接池、RAG 服务就绪等）。
     */
    @Scheduled(fixedDelay = 30_000, initialDelay = 15_000)
    public void processOutbox() {
        if (!running.compareAndSet(false, true)) {
            log.warn("[RagSyncScheduler] 上一轮尚未结束，跳过本轮");
            return;
        }
        try {
            int processed = ragSyncService.processPendingBatch(BATCH_SIZE);
            if (processed > 0) {
                log.info("[RagSyncScheduler] 本轮处理 {} 条 outbox 记录", processed);
            }
        } catch (Exception e) {
            // 调度异常只打日志，不中断定时任务线程
            log.error("[RagSyncScheduler] 定时同步异常", e);
        } finally {
            running.set(false);
        }
    }
}
