package com.stellar.utils;

import com.stellar.constant.JwtClaimsConstant;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试。
 * 覆盖：jti 自动生成、access/refresh token 区分、解析可取回 jti 与 claims。
 */
class JwtUtilTest {

    private static final String SECRET = "test_secret_key_at_least_32_chars_long__";

    @Test
    void createJWT_shouldAutoGenerateJti() {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, 1L);

        String token = JwtUtil.createJWT(SECRET, 60000L, claims);

        Claims parsed = JwtUtil.parseJWT(SECRET, token);
        String jti = parsed.getId();
        assertNotNull(jti, "jti 应自动生成");
        assertFalse(jti.isBlank(), "jti 不能为空字符串");
        assertEquals(1L, ((Number) parsed.get(JwtClaimsConstant.EMP_ID)).longValue());
    }

    @Test
    void createJWT_twoTokensShouldHaveDifferentJti() {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, 100L);

        String t1 = JwtUtil.createJWT(SECRET, 60000L, claims);
        String t2 = JwtUtil.createJWT(SECRET, 60000L, claims);

        String jti1 = JwtUtil.parseJWT(SECRET, t1).getId();
        String jti2 = JwtUtil.parseJWT(SECRET, t2).getId();
        assertNotEquals(jti1, jti2, "两次签发的 token jti 必须不同");
    }

    @Test
    void createRefreshJWT_shouldMarkTypeAsRefresh() {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, 100L);

        String refresh = JwtUtil.createRefreshJWT(SECRET, 60000L, claims);

        Claims parsed = JwtUtil.parseJWT(SECRET, refresh);
        assertNotNull(parsed.getId(), "refresh token 也应有 jti");
        assertEquals("refresh", parsed.get(JwtClaimsConstant.TOKEN_TYPE),
                "refresh token 的 type claim 必须为 refresh");
        assertEquals(100L, ((Number) parsed.get(JwtClaimsConstant.USER_ID)).longValue());
    }

    @Test
    void createJWT_accessTokenShouldHaveAccessType() {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, 1L);

        String access = JwtUtil.createJWT(SECRET, 60000L, claims);

        Claims parsed = JwtUtil.parseJWT(SECRET, access);
        assertEquals("access", parsed.get(JwtClaimsConstant.TOKEN_TYPE),
                "access token 的 type claim 必须为 access");
    }

    @Test
    void parseJWT_invalidTokenShouldThrow() {
        assertThrows(Exception.class, () -> JwtUtil.parseJWT(SECRET, "not.a.valid.token"));
    }

    @Test
    void parseJWT_expiredTokenShouldThrow() throws InterruptedException {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, 1L);
        // TTL = 1ms，签发后稍等即过期
        String token = JwtUtil.createJWT(SECRET, 1L, claims);
        Thread.sleep(50L);
        assertThrows(Exception.class, () -> JwtUtil.parseJWT(SECRET, token));
    }
}
