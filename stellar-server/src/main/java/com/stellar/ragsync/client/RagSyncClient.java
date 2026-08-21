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

    /**
     * 生成 AI 经营日报：把 Mall 端汇总的经营统计数据发给 RAG 端，
     * 由 RAG 端调用 LLM（qwen-plus）生成自然语言日报。
     * <p>
     * POST {baseUrl}/api/internal/daily_report (Header X-Stellar-Rag-Sync-Secret)
     * <p>
     * 注意：LLM 生成耗时较长（通常 5~30s），超时走 reportTimeoutMs 而非 timeoutMs。
     *
     * @param statsPayload 统计数据字段 Map（date/today_orders/today_sales/...）
     * @return LLM 生成的日报文本；失败抛 RuntimeException（含原因）。
     */
    String generateDailyReport(Map<String, Object> statsPayload);

    /**
     * ChatBI 第一步：把"自然语言问题 + 表结构说明"发给 RAG 端，由 LLM 生成
     * SELECT 分析 SQL 和图表配置（chart_type/x_field/y_field）。
     * <p>
     * POST {baseUrl}/api/internal/chat_bi/sql（共享密钥头，超时走 reportTimeoutMs）
     *
     * @param payload {question, schema_ddl}
     * @return RAG 端 data 部分：{sql, title, chart_type, x_field, y_field}
     */
    Map<String, Object> chatBiGenerateSql(Map<String, Object> payload);

    /**
     * ChatBI 第三步：把"问题 + 已执行的查询结果 JSON"发给 RAG 端，
     * 由 LLM 生成自然语言回答。
     * <p>
     * POST {baseUrl}/api/internal/chat_bi/summary（共享密钥头，超时走 reportTimeoutMs）
     *
     * @param payload {question, result_json}
     * @return LLM 生成的回答文本
     */
    String chatBiSummary(Map<String, Object> payload);
}
