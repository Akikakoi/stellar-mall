package com.stellar.ragsync.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("RagSyncProperties — 同步密钥 fail-fast")
class RagSyncPropertiesTest {

    @Test
    @DisplayName("默认占位符 → 校验失败")
    void placeholderSecret_rejected() {
        RagSyncProperties props = new RagSyncProperties();
        props.setInternalSyncSecret("CHANGE_ME_RAG_SYNC_SECRET");
        assertThrows(IllegalStateException.class, props::validateSecret);
    }

    @Test
    @DisplayName("足够长的密钥 → 通过")
    void strongSecret_accepted() {
        RagSyncProperties props = new RagSyncProperties();
        props.setInternalSyncSecret("0123456789abcdef");
        assertDoesNotThrow(props::validateSecret);
    }

    @Test
    @DisplayName("测试显式允许兜底 → 跳过校验")
    void allowInsecureDefaults_skips() {
        RagSyncProperties props = new RagSyncProperties();
        props.setAllowInsecureDefaults(true);
        props.setInternalSyncSecret("CHANGE_ME");
        assertDoesNotThrow(props::validateSecret);
    }
}