package com.stellar;

import com.stellar.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0 RED 阶段测试（Java 端）。
 * 验证 Stellar JwtUtil 生成/解析 JWT 的约定 100% 与 Python RAG 端对齐：
 *   - HS256
 *   - 密钥 = UTF-8 原始字节（不解 Base64）
 *   - claim 字段名：EMP_ID / USER_ID / ROLE / EXP（大写，两边约定一致）
 *   - ttl 单位：毫秒（与 sky 的 JwtUtil.createJWT 一致）
 *
 * RED 阶段表现：项目未搭建，直接跑会编译失败（找不到 JwtUtil 类）。
 * 失败原因是「功能不存在」——这正是 TDD 要求的 RED。
 */
class JwtCrossPlatformTest {

    // ======= 与 Python 端约定的两套共享密钥（P0 硬编码，实现后读取配置） =======
    private static final String STELLAR_ADMIN_SECRET =
            "StellarMall_Admin_SecretKey_2024_Strong_32bytes_!@#";
    private static final String STELLAR_USER_SECRET =
            "StellarMall_User_SecretKey_2024_Strong_32bytes_$%^";
    private static final long TTL_MILLIS = 7_200_000L; // 2 小时

    // ---------------------------------------------------------------
    // 用例 1：管理员 JWT 生成 → 解析，能拿到 EMP_ID / ROLE
    // ---------------------------------------------------------------
    @Test
    void adminToken_containsEmpIdAndRole_and_canBeParsed() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("EMP_ID", 1L);
        claims.put("ROLE", "admin");
        claims.put("NAME", "超级管理员");

        String token = JwtUtil.createJWT(STELLAR_ADMIN_SECRET, TTL_MILLIS, claims);
        assertNotNull(token, "RED 失败：JwtUtil.createJWT 未实现");
        assertFalse(token.isEmpty());
        // JWT 三段式
        assertEquals(3, token.split("\\.").length);

        Claims parsed = JwtUtil.parseJWT(STELLAR_ADMIN_SECRET, token);
        assertNotNull(parsed);
        assertEquals(1L, ((Number) parsed.get("EMP_ID")).longValue());
        assertEquals("admin", parsed.get("ROLE"));
        // exp 必须大于当前时间
        assertNotNull(parsed.getExpiration());
        assertTrue(parsed.getExpiration().getTime() > System.currentTimeMillis());
    }

    // ---------------------------------------------------------------
    // 用例 2：C 端用户 JWT 生成 → 解析，能拿到 USER_ID
    // ---------------------------------------------------------------
    @Test
    void userToken_containsUserIdAndRole_and_canBeParsed() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("USER_ID", 42L);
        claims.put("ROLE", "user");
        claims.put("NICKNAME", "买家小王");

        String token = JwtUtil.createJWT(STELLAR_USER_SECRET, TTL_MILLIS, claims);
        Claims parsed = JwtUtil.parseJWT(STELLAR_USER_SECRET, token);

        assertEquals(42L, ((Number) parsed.get("USER_ID")).longValue());
        assertEquals("user", parsed.get("ROLE"));
    }

    // ---------------------------------------------------------------
    // 用例 3：密钥不一致 → 解析抛异常（不能静默通过）
    // ---------------------------------------------------------------
    @Test
    void wrongSecret_throwsException_doesNotSilentPass() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("EMP_ID", 999L);
        String token = JwtUtil.createJWT(STELLAR_ADMIN_SECRET, TTL_MILLIS, claims);

        // 用错误密钥解 → 必抛 SignatureException / ExpiredJwtException 等
        assertThrows(Exception.class,
                () -> JwtUtil.parseJWT("wrong-secret-12345678901234567890", token));
    }

    // ---------------------------------------------------------------
    // 用例 4：过期 JWT → 解析抛 ExpiredJwtException（不能静默通过）
    // ---------------------------------------------------------------
    @Test
    void expiredToken_throwsExpiredException() throws InterruptedException {
        Map<String, Object> claims = new HashMap<>();
        claims.put("USER_ID", 1L);
        // 1ms 过期，等待 50ms 后必过期
        String token = JwtUtil.createJWT(STELLAR_USER_SECRET, 1L, claims);
        Thread.sleep(50L);

        assertThrows(Exception.class,
                () -> JwtUtil.parseJWT(STELLAR_USER_SECRET, token));
    }

    // ---------------------------------------------------------------
    // 用例 5：密钥字节必须是 UTF-8 原始字节（不能 Base64 解码）——与 Python PyJWT 默认等价
    // ---------------------------------------------------------------
    @Test
    void secretKey_isUsedAsRawUtf8Bytes_notBase64Decoded() {
        // 用一段包含非 ASCII 的密钥验证：如果是 Base64 解码会失败；如果是 UTF-8 原始字节就 OK
        String nonAsciiSecret = "星耀密钥-2024-✓🚀中文测试!!";
        byte[] expectedBytes = nonAsciiSecret.getBytes(StandardCharsets.UTF_8);
        assertTrue(expectedBytes.length > nonAsciiSecret.length()); // UTF-8 变长编码，中文字节 > 字符数

        Map<String, Object> claims = new HashMap<>();
        claims.put("EMP_ID", 7L);
        String token = JwtUtil.createJWT(nonAsciiSecret, TTL_MILLIS, claims);
        Claims parsed = JwtUtil.parseJWT(nonAsciiSecret, token);
        assertEquals(7L, ((Number) parsed.get("EMP_ID")).longValue());
    }
}
