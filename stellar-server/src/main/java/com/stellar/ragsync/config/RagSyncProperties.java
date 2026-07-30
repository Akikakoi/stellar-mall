package com.stellar.ragsync.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 同步配置（和 application.yml 里 stellar.rag.* 绑定）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "stellar.rag")
public class RagSyncProperties {

    /** RAG 服务 base URL，例：http://127.0.0.1:8000 */
    private String baseUrl = "http://127.0.0.1:8000";

    /** 共享密钥，RAG 端通过 X-Stellar-Rag-Sync-Secret Header 校验���
     *  ⚠️ 必须和 RAG 端 .env 里 STELLAR_RAG_INTERNAL_SYNC_SECRET 完全一致！ */
    private String internalSyncSecret = "uEvV_FYrloC6T_R8vYNZcHlt07xL-K14Vh-2-VBpMqrzylSX4BbouNZQwv90QpJL";

    /** 读取超时（毫秒）。 */
    private int timeoutMs = 10000;

    /** 最大重试次数（outbox 兜底），默认 3。 */
    private int maxAttempt = 3;
}
