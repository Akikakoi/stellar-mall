package com.stellar.properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("JwtProperties — 默认密钥 fail-fast")
class JwtPropertiesTest {

    private static final String STRONG = "0123456789abcdef0123456789abcdef";

    @Test
    @DisplayName("占位符/短密钥 → 启动校验失败")
    void weakSecret_rejected() {
        JwtProperties props = new JwtProperties();
        props.setAdminSecretKey("CHANGE_ME_ADMIN_JWT_SECRET_AT_LEAST_32_CHARS");
        props.setUserSecretKey(STRONG);
        assertThrows(IllegalStateException.class, props::validateSecrets);

        props.setAdminSecretKey(STRONG);
        props.setUserSecretKey("short");
        assertThrows(IllegalStateException.class, props::validateSecrets);
    }

    @Test
    @DisplayName("足够强的密钥 → 通过")
    void strongSecret_accepted() {
        JwtProperties props = new JwtProperties();
        props.setAdminSecretKey(STRONG);
        props.setUserSecretKey(STRONG);
        assertDoesNotThrow(props::validateSecrets);
    }

    @Test
    @DisplayName("测试显式允许兜底 → 跳过校验")
    void allowInsecureDefaults_skips() {
        JwtProperties props = new JwtProperties();
        props.setAllowInsecureDefaults(true);
        props.setAdminSecretKey("CHANGE_ME");
        props.setUserSecretKey("CHANGE_ME");
        assertDoesNotThrow(props::validateSecrets);
    }
}