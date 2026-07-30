package com.stellar.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 启动时安全自检：校验 JWT / 内部同步密钥强度，防止使用默认弱密钥上线生产。
 */
@Slf4j
@Component
public class SecurityStartupChecker implements ApplicationRunner {

    private static final int MIN_KEY_LENGTH = 32;

    private static final Set<String> WEAK_PATTERNS = new HashSet<>();

    static {
        WEAK_PATTERNS.add("secret");
        WEAK_PATTERNS.add("password");
        WEAK_PATTERNS.add("123456");
        WEAK_PATTERNS.add("admin");
        WEAK_PATTERNS.add("user");
        WEAK_PATTERNS.add("stellar");
        WEAK_PATTERNS.add("2024");
        WEAK_PATTERNS.add("test");
        WEAK_PATTERNS.add("default");
    }

    @Value("${stellar.jwt.admin-secret-key}")
    private String adminSecretKey;

    @Value("${stellar.jwt.user-secret-key}")
    private String userSecretKey;

    @Value("${stellar.rag.internal-sync-secret}")
    private String ragInternalSyncSecret;

    @Override
    public void run(ApplicationArguments args) {
        checkKey("stellar.jwt.admin-secret-key", adminSecretKey);
        checkKey("stellar.jwt.user-secret-key", userSecretKey);
        checkKey("stellar.rag.internal-sync-secret", ragInternalSyncSecret);
    }

    private void checkKey(String name, String value) {
        if (value == null || value.isBlank()) {
            log.warn("[安全自检] {} 未配置，请立即设置强密钥！", name);
            return;
        }
        if (value.length() < MIN_KEY_LENGTH) {
            log.warn("[安全自检] {} 长度过短（{} < {}），存在被暴力破解风险，请更换为随机强密钥。",
                    name, value.length(), MIN_KEY_LENGTH);
        }
        String lower = value.toLowerCase();
        for (String pattern : WEAK_PATTERNS) {
            if (lower.contains(pattern)) {
                log.warn("[安全自检] {} 包含弱口令特征 '{}'，请勿使用默认/常见词汇作为密钥。",
                        name, pattern);
                break;
            }
        }
    }
}
