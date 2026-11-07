package com.stellar.ragsync.config;

import jakarta.annotation.PostConstruct;
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

    /** 共享密钥，RAG 端通过 X-Stellar-Rag-Sync-Secret Header 校验。
     *  ⚠️ 必须和 RAG 端 .env 里 STELLAR_RAG_INTERNAL_SYNC_SECRET 完全一致！ */
    private String internalSyncSecret;

    /** 仅测试/本地显式开启；生产必须为 false。 */
    private boolean allowInsecureDefaults = false;

    /** 读取超时（毫秒）。 */
    private int timeoutMs = 10000;

    /** AI 日报生成读取超时（毫秒）。LLM 生成耗时远长于普通同步，默认 90s。 */
    private int reportTimeoutMs = 90000;

    /** 最大重试次数（outbox 兜底），默认 3。 */
    private int maxAttempt = 3;

    @PostConstruct
    void validateSecret() {
        if (allowInsecureDefaults) {
            return;
        }
        if (internalSyncSecret == null || internalSyncSecret.isBlank()
                || internalSyncSecret.startsWith("CHANGE_ME")
                || internalSyncSecret.startsWith("<")
                || internalSyncSecret.contains("替换")
                || internalSyncSecret.length() < 16) {
            throw new IllegalStateException(
                    "Insecure RAG sync secret: stellar.rag.internal-sync-secret must be >=16 chars "
                            + "and set via STELLAR_RAG_INTERNAL_SYNC_SECRET "
                            + "(or explicitly set stellar.rag.allow-insecure-defaults=true for tests)");
        }
    }
}
