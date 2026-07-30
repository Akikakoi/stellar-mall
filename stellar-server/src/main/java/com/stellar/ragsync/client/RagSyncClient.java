package com.stellar.ragsync.client;

import com.stellar.entity.Spu;

import java.util.Map;

/**
 * Mall → RAG 同步 HTTP 客户端接口。
 * <p>
 * 真实实现会发 HTTP POST 到 RAG 端的：
 *   POST {baseUrl}/api/internal/sync_spu   (Header X-Stellar-Rag-Sync-Secret)
 *   POST {baseUrl}/api/internal/sync_doc   (Header X-Stellar-Rag-Sync-Secret)
 * Mock 实现用于测试。
 */
public interface RagSyncClient {

    /**
     * 同步 SPU（保存/上下架/更新统一走此方法；内部会把 description_md 切分后上传）。
     * @param spu 完整 SPU（含 SKU 列表）
     * @return true 成功；失败则抛 RuntimeException（由上层落 failed）。
     */
    boolean syncSpu(Spu spu);

    /**
     * 同步文档（政策/公告/帮助文档等）。
     * @param docPayload 文档字段 Map，必须包含 doc_id、title、content_md，
     *                   可选 doc_type、tags、status。
     * @return true 成功；失败则抛 RuntimeException。
     */
    boolean syncDoc(Map<String, Object> docPayload);
}
