package com.stellar.ragsync;

import com.stellar.ragsync.client.RagSyncClient;
import com.stellar.service.impl.ChatBiServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatBI SQL 安全校验测试：表名白名单 + 列名黑名单 + 单条 SELECT 限制。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ChatBI SQL 安全校验 — validateAndSanitize")
class ChatBiSqlValidationTest {

    @Mock private RagSyncClient ragSyncClient;
    @Mock private DataSource dataSource;

    // 通过反射调用 package-private 方法，避免依赖 Spring 容器；异常解包为原始 RuntimeException
    private String sanitize(String sql) {
        try {
            var method = ChatBiServiceImpl.class.getDeclaredMethod("validateAndSanitize", String.class);
            method.setAccessible(true);
            return (String) method.invoke(new ChatBiServiceImpl(ragSyncClient, dataSource), sql);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test @DisplayName("正常统计 SQL 放行并自动补 LIMIT")
    void normalSelect_passAndAppendLimit() {
        String result = sanitize("SELECT COUNT(*) FROM stellar_mall_order");
        assertTrue(result.contains("LIMIT 100"), "应自动补 LIMIT 100，实际: " + result);
    }

    @Test @DisplayName("查询订单金额等业务列放行（非敏感列）")
    void businessColumns_pass() {
        String result = sanitize(
                "SELECT o.status, COUNT(*) AS cnt, SUM(oi.subtotal) FROM stellar_mall_order o JOIN stellar_mall_order_item oi ON oi.order_id = o.id WHERE o.status = 'PAID'");
        assertNotNull(result);
    }

    @Test @DisplayName("SELECT 手机号（敏感列）→ 拒绝")
    void selectPhone_rejected() {
        assertThrows(RuntimeException.class, () -> sanitize("SELECT phone FROM stellar_mall_user"));
    }

    @Test @DisplayName("SELECT 密码哈希（敏感列）→ 拒绝")
    void selectPasswordHash_rejected() {
        assertThrows(RuntimeException.class, () -> sanitize("SELECT password_hash FROM stellar_employee"));
    }

    @Test @DisplayName("带表前缀查询敏感列（u.phone）→ 拒绝")
    void selectPrefixedSensitiveColumn_rejected() {
        assertThrows(RuntimeException.class,
                () -> sanitize("SELECT u.phone, u.nickname FROM stellar_mall_user u"));
    }

    @Test @DisplayName("非 SELECT 语句 → 拒绝")
    void nonSelect_rejected() {
        assertThrows(RuntimeException.class, () -> sanitize("DELETE FROM stellar_mall_order"));
    }

    @Test @DisplayName("多语句（含分号）→ 拒绝")
    void multiStatement_rejected() {
        assertThrows(RuntimeException.class,
                () -> sanitize("SELECT * FROM stellar_spu; DROP TABLE stellar_spu"));
    }

    @Test @DisplayName("未授权表 → 拒绝")
    void unauthorizedTable_rejected() {
        assertThrows(RuntimeException.class, () -> sanitize("SELECT * FROM stellar_employee"));
    }

    @Test @DisplayName("危险函数 sleep → 拒绝")
    void dangerousFunction_rejected() {
        assertThrows(RuntimeException.class,
                () -> sanitize("SELECT SLEEP(5) FROM stellar_mall_order"));
    }
}
