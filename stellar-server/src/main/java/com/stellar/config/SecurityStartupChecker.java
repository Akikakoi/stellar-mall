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
 * <p>
 * dev 环境仅 warn，prod 环境检测到弱密钥将拒绝启动。
 */
@Slf4j
@Component
public class SecurityStartupChecker implements ApplicationRunner {

    private static final int MIN_KEY_LENGTH = 32;

    private static final Set<String> WEAK_PATTERNS = new HashSet<>();

    static {
        WEAK_PATTERNS.add("change_me");
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

    private static final String PROD_PROFILE = "prod";

    @Value("${spring.profiles.active:}")
    private String activeProfile;

    @Value("${stellar.jwt.admin-secret-key}")
    private String adminSecretKey;

    @Value("${stellar.jwt.user-secret-key}")
    private String userSecretKey;

    @Value("${stellar.rag.internal-sync-secret}")
    private String ragInternalSyncSecret;

    @Override
    public void run(ApplicationArguments args) {
        boolean isProd = PROD_PROFILE.equals(activeProfile);
        checkKey("stellar.jwt.admin-secret-key", adminSecretKey, isProd);
        checkKey("stellar.jwt.user-secret-key", userSecretKey, isProd);
        checkKey("stellar.rag.internal-sync-secret", ragInternalSyncSecret, isProd);
    }

    private void checkKey(String name, String value, boolean isProd) {
        if (value == null || value.isBlank()) {
            String msg = String.format("[安全自检] %s 未配置", name);
            if (isProd) {
                log.error(msg + "，生产环境禁止使用空密钥，请设置环境变量后重启！");
                throw new IllegalStateException(msg + " — 生产环境必须配置强密钥");
            }
            log.warn(msg + "，请立即设置强密钥！");
            return;
        }
        if (value.length() < MIN_KEY_LENGTH) {
            String msg = String.format("[安全自检] %s 长度过短（%d < %d），存在被暴力破解风险",
                    name, value.length(), MIN_KEY_LENGTH);
            if (isProd) {
                log.error(msg + "，生产环境拒绝启动！");
                throw new IllegalStateException(msg + " — 生产环境密钥长度必须 ≥ " + MIN_KEY_LENGTH);
            }
            log.warn(msg + "，请更换为随机强密钥。");
        }
        String lower = value.toLowerCase();
        for (String pattern : WEAK_PATTERNS) {
            if (lower.contains(pattern)) {
                String msg = String.format("[安全自检] %s 包含弱口令特征 '%s'，疑似默认密钥未修改",
                        name, pattern);
                if (isProd) {
                    log.error(msg + "，生产环境拒绝启动！");
                    throw new IllegalStateException(msg + " — 生产环境禁止使用默认/弱密钥，请通过环境变量注入强密钥");
                }
                log.warn(msg + "，请勿使用默认/常见词汇作为密钥。");
                break;
            }
        }
    }
}
