package com.stellar.ragsync.mapper;

import com.stellar.annotation.AutoFill;
import com.stellar.entity.RagSyncOutbox;
import com.stellar.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RagSyncOutboxMapper {

    @AutoFill(OperationType.INSERT)
    int insert(RagSyncOutbox box);

    @AutoFill(OperationType.UPDATE)
    int update(RagSyncOutbox box);

    RagSyncOutbox getById(@Param("id") Long id);

    /** 拉取一批待处理的：未同步 & 未失败。 */
    List<RagSyncOutbox> listPending(@Param("limit") int limit);

    /** 标记 synced=1，尝试次数/时间更新。 */
    int markSynced(@Param("id") Long id,
                   @Param("retryCount") Integer retryCount,
                   @Param("lastTryTime") LocalDateTime lastTryTime);

    /** 失败一次：retryCount++, lastTryTime, lastErrorMsg。达 maxAttempt 时 failed=1。 */
    int markFailedOnce(@Param("id") Long id,
                       @Param("retryCount") Integer retryCount,
                       @Param("lastTryTime") LocalDateTime lastTryTime,
                       @Param("lastErrorMsg") String lastErrorMsg,
                       @Param("failed") Integer failed);

    /** 管理端统计未完成的 outbox 数量（synced=0 AND failed=0）。 */
    long countPending();

    /** 管理端分页拉取未完成的 outbox（synced=0 AND failed=0），按 id 升序。 */
    List<RagSyncOutbox> pagePending(@Param("offset") int offset, @Param("limit") int limit);

    /** 管理员手动重发：重置重试计数与失败标记。 */
    int resetForRetry(@Param("id") Long id);

    /** 管理端：统计全部 outbox 数量。 */
    long countAll();

    /** 管理端：统计已同步成功（synced=1）数量。 */
    long countSynced();

    /** 管理端：统计已失败（failed=1）数量。 */
    long countFailed();

    /** 管理端：按条件统计（支持 status/eventType/bizId 过滤）。 */
    long countAllFiltered(@Param("status") Integer status,
                          @Param("eventType") String eventType,
                          @Param("bizId") Long bizId);

    /** 管理端：分页拉取所有 outbox，支持条件过滤，按 id 倒序。 */
    List<RagSyncOutbox> pageAllFiltered(@Param("offset") int offset,
                                        @Param("limit") int limit,
                                        @Param("status") Integer status,
                                        @Param("eventType") String eventType,
                                        @Param("bizId") Long bizId);
}
