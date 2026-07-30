package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Mall → RAG 同步 outbox 表（发件箱模式）。
 * 每次业务变更（SPU 保存/上下架、优惠券变化、政策更新等）先落一张 outbox 记录，
 * 再由后台定时任务 + 手动 processPendingOne() 推进。
 * <p>
 * 字段对齐测试：bizType, bizId, opType, synced(0/1), failed(0/1), retryCount, lastTryTime, lastErrorMsg.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagSyncOutbox implements Serializable {

    private Long id;

    /** 业务类型：SPU / POLICY / COUPON / CATEGORY / DOC */
    private String bizType;

    /** 业务 id：Long 型，兼容 SPU/优惠券/分类等数字主键。 */
    private Long bizId;

    /** 操作类型：SAVE / UPDATE / ONSHELF / OFFSHELF / DELETE / SYNC_POLICY 等。 */
    private String opType;

    /** 0 未同步 / 1 已同步成功。 */
    private Integer synced;

    /** 0 未失败（或仍在重试）/ 1 已达到最大重试次数，失败。 */
    private Integer failed;

    /** 已尝试次数：从 0 开始，每调一次 RAG 接口就 +1。成功时 retryCount 也会 +1。 */
    private Integer retryCount;

    /** 最大尝试次数：默认 3。0 表示无限制（不建议）。 */
    private Integer maxAttempt;

    /** 最后一次尝试时间。 */
    private LocalDateTime lastTryTime;

    /** 最后一次失败的异常信息。 */
    private String lastErrorMsg;

    /** 请求体 JSON（脱敏后存，便于手动重放 / 排查）。 */
    private String payloadJson;

    private LocalDateTime createTime;
    private Long          createUser;
    private LocalDateTime updateTime;
    private Long          updateUser;
}
