package com.stellar.properties;

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
}
