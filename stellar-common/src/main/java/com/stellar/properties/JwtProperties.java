package com.stellar.properties;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 密钥配置（读取 stellar.jwt.*）。
 * <p>
 * ⚠️ 生产必须用 ≥32 位强随机字符串，且和 RAG Python 端 .env 里的 STELLAR_ADMIN_SECRET_KEY / STELLAR_USER_SECRET_KEY 100% 一致。
 */
@Data
@Component
@ConfigurationProperties(prefix = "stellar.jwt")
public class JwtProperties {
    private String adminSecretKey;
    private long   adminTtl;
    private String adminTokenName;
    /** 管理端 refresh token TTL（毫秒） */
    private long   adminRefreshTtl;

    private String userSecretKey;
    private long   userTtl;
    private String userTokenName;
    /** C 端 refresh token TTL（毫秒） */
    private long   userRefreshTtl;

    /** 仅测试/本地显式开启；生产必须为 false。 */
    private boolean allowInsecureDefaults = false;

    @PostConstruct
    void validateSecrets() {
        if (allowInsecureDefaults) {
            return;
        }
        requireStrong("adminSecretKey", adminSecretKey, "STELLAR_ADMIN_SECRET_KEY");
        requireStrong("userSecretKey", userSecretKey, "STELLAR_USER_SECRET_KEY");
    }

    private static void requireStrong(String field, String value, String envName) {
        if (value == null || value.isBlank() || value.startsWith("CHANGE_ME")
                || value.startsWith("<") || value.contains("替换") || value.length() < 32) {
            throw new IllegalStateException(
                    "Insecure JWT secret: stellar.jwt." + field
                            + " must be >=32 chars and set via " + envName
                            + " (or explicitly set stellar.jwt.allow-insecure-defaults=true for tests)");
        }
    }
}
