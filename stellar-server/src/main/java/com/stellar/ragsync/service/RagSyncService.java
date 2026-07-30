package com.stellar.ragsync.service;

import com.stellar.result.PageResult;

import java.util.Map;

public interface RagSyncService {

    /**
     * SPU 变更时先调用此方法 enqueue 一条 outbox 记录（synced=0, failed=0）。
     * 返回 outbox id。
     *
     * @param spuId  业务 SPU id
     * @param opType 操作类型：SAVE / ONSHELF / OFFSHELF / DELETE
     * @return outbox id
     */
    Long enqueueSpuSync(Long spuId, String opType);

    /**
     * 文档变更时入队一条 outbox 记录。
     *
     * @param docId      文档业务 ID
     * @param docType    文档类型（DOC / POLICY / COUPON 等）
     * @param title      文档标题
     * @param contentMd  Markdown 正文
     * @param opType     操作类型
     * @param status     文档状态：1 启用 0 停用
     * @param tags       可选标签
     * @return outbox id
     */
    Long enqueueDocSync(String docId, String docType, String title,
                        String contentMd, String opType, int status, String tags);

    /**
     * 处理单条 outbox（测试常用，后台调度也会批量调）：
     *  1. 若已 synced=1 或 failed=1 → 直接跳过
     *  2. 调 RagSyncClient
     *     - 成功 → synced=1, retryCount++, lastTryTime
     *     - 异常 → retryCount++, lastTryTime, lastErrorMsg
     *                  retryCount >= maxAttempt → failed=1
     */
    void processPendingOne(Long outboxId);

    /** 后台调度：批量拉取 listPending 并逐条 processPendingOne。返回实际处理条数。 */
    int processPendingBatch(int limit);

    /** 管理端：分页查询未同步（synced=0）的 outbox。 */
    PageResult listPendingPage(int page, int pageSize);

    /** 管理端：手动重发某条（重置计数 + failed=0 → 再 processPendingOne）。 */
    void retryOne(Long outboxId);

    /**
     * 管理端：分页查询所有 outbox，支持条件过滤。
     *
     * @param page      页码（从 1 开始）
     * @param pageSize  每页条数
     * @param status    前端状态：0 待同步 / 1 处理中 / 2 成功 / 3 失败；null=全部
     * @param eventType 操作类型过滤（如 SAVE、DELETE），null=全部
     * @param bizId     业务 ID 过滤，null=全部
     */
    PageResult listAllPageFiltered(int page, int pageSize,
                                   Integer status, String eventType, Long bizId);

    /** 管理端：outbox 统计：total / synced / pending / failed。 */
    Map<String, Long> stats();
}
