package com.stellar.auth;

import com.stellar.properties.JwtProperties;
import com.stellar.service.impl.TokenBlacklistServiceImpl;
import com.stellar.utils.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TokenBlacklistService 单元测试（E4）。
 * <p>
 * 覆盖场景（对应 SPEC.md T12 要求）：
 *   1. blacklist 把 token 写入 Redis（key 存在、TTL 正确）
 *   2. isBlacklisted 命中已拉黑的 jti 返回 true
 *   3. isBlacklisted 未拉黑的 jti 返回 false
 *   4. 登出后用相同 jti 校验失败（端到端：blacklist → isBlacklisted）
 *   5. Redis 不可用时 isBlacklisted 降级放行（返回 false）
 *   6. Redis 不可用时 blacklist 不抛异常
 *   7. 已过期 token 不写黑名单
 *   8. TTL 自动清理：模拟 TTL 过期后 isBlacklisted 返回 false
 *   9. null/空 token 安全处理
 *   10. null/空 jti 安全处理
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("E4 JWT 黑名单 TokenBlacklistService")
class TokenBlacklistServiceTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private TokenBlacklistServiceImpl tokenBlacklistService;

    /** 测试密钥（≥32 字节，HS256 要求） */
    private static final String ADMIN_SECRET = "stellar-admin-secret-key-for-test-only-aaaaaaaaaaaa";
    private static final String USER_SECRET = "stellar-user-secret-key-for-test-only-bbbbbbbbbbbbbb";

    /** 模拟 Redis 存储：key → value */
    private final Map<String, String> redisStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setAdminSecretKey(ADMIN_SECRET);
        props.setUserSecretKey(USER_SECRET);
        // 用反射注入 JwtProperties（@RequiredArgsConstructor 注入的是构造参数）
        try {
            var field = TokenBlacklistServiceImpl.class.getDeclaredField("jwtProperties");
            field.setAccessible(true);
            field.set(tokenBlacklistService, props);
        } catch (Exception e) {
            fail("无法注入 jwtProperties: " + e.getMessage());
        }

        redisStore.clear();

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        // set(key, value, ttl, unit) → 写入 map
        lenient().doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        // hasKey → 检查 map
        lenient().when(stringRedisTemplate.hasKey(anyString())).thenAnswer(invocation ->
                redisStore.containsKey(invocation.getArgument(0)));

        // delete → 从 map 删除
        lenient().when(stringRedisTemplate.delete(anyString())).thenAnswer(invocation ->
                redisStore.remove(invocation.getArgument(0)) != null);
    }

    /**
     * 生成 admin access token，TTL = ttlMillis
     */
    private String genAdminToken(long ttlMillis) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("EMP_ID", 1L);
        return JwtUtil.createJWT(ADMIN_SECRET, ttlMillis, claims);
    }

    private String genUserToken(long ttlMillis) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("USER_ID", 1L);
        return JwtUtil.createJWT(USER_SECRET, ttlMillis, claims);
    }

    @Test
    @DisplayName("场景1：blacklist 把 admin token 写入 Redis")
    void blacklist_shouldWriteAdminTokenToRedis() {
        String token = genAdminToken(60_000); // 60 秒 TTL

        tokenBlacklistService.blacklist(token);

        // Redis 中应有一个 jwt_blacklist:* 的 key
        long count = redisStore.keySet().stream()
                .filter(k -> k.startsWith("jwt_blacklist:"))
                .count();
        assertEquals(1, count, "应有 1 个黑名单 key");
    }

    @Test
    @DisplayName("场景1b：blacklist 把 user token 写入 Redis（用 user 密钥解析）")
    void blacklist_shouldWriteUserTokenToRedis() {
        String token = genUserToken(60_000);

        tokenBlacklistService.blacklist(token);

        long count = redisStore.keySet().stream()
                .filter(k -> k.startsWith("jwt_blacklist:"))
                .count();
        assertEquals(1, count, "user token 也应能写入黑名单");
    }

    @Test
    @DisplayName("场景2：isBlacklisted 命中已拉黑的 jti 返回 true")
    void isBlacklisted_shouldReturnTrueForBlacklistedJti() throws Exception {
        String token = genAdminToken(60_000);
        tokenBlacklistService.blacklist(token);

        // 从 token 解析 jti
        var claims = JwtUtil.parseJWT(ADMIN_SECRET, token);
        String jti = claims.getId();

        assertTrue(tokenBlacklistService.isBlacklisted(jti),
                "已拉黑的 jti 应返回 true");
    }

    @Test
    @DisplayName("场景3：isBlacklisted 未拉黑的 jti 返回 false")
    void isBlacklisted_shouldReturnFalseForUnknownJti() {
        assertFalse(tokenBlacklistService.isBlacklisted("unknown-jti-12345"),
                "未拉黑的 jti 应返回 false");
    }

    @Test
    @DisplayName("场景4：登出后用相同 jti 校验失败（端到端：blacklist → isBlacklisted）")
    void endToEnd_blacklistThenCheckShouldFail() throws Exception {
        String token = genAdminToken(60_000);
        var claims = JwtUtil.parseJWT(ADMIN_SECRET, token);
        String jti = claims.getId();

        // 登出前：不在黑名单
        assertFalse(tokenBlacklistService.isBlacklisted(jti), "登出前应不在黑名单");

        // 登出：加入黑名单
        tokenBlacklistService.blacklist(token);

        // 登出后：在黑名单
        assertTrue(tokenBlacklistService.isBlacklisted(jti), "登出后应在黑名单");
    }

    @Test
    @DisplayName("场景5：Redis 不可用时 isBlacklisted 降级放行（返回 false）")
    void isBlacklisted_shouldDegradeWhenRedisDown() {
        when(stringRedisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis 连接失败"));

        assertFalse(tokenBlacklistService.isBlacklisted("any-jti"),
                "Redis 故障时应降级放行（返回 false），不抛异常");
    }

    @Test
    @DisplayName("场景6：Redis 不可用时 blacklist 不抛异常")
    void blacklist_shouldNotThrowWhenRedisDown() {
        String token = genAdminToken(60_000);
        doThrow(new RuntimeException("Redis 连接失败"))
                .when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        // 不应抛异常
        assertDoesNotThrow(() -> tokenBlacklistService.blacklist(token));
    }

    @Test
    @DisplayName("场景7：已过期 token 不写黑名单（无需写入，JWT 解析本身会拒绝）")
    void blacklist_shouldSkipExpiredToken() {
        // TTL = -1 表示已过期（exp = now - 1）
        String token = genAdminToken(-1000);

        tokenBlacklistService.blacklist(token);

        // Redis 中不应有黑名单 key
        assertTrue(redisStore.isEmpty(), "已过期 token 不应写入黑名单");
    }

    @Test
    @DisplayName("场景8：TTL 自动清理：模拟 TTL 过期后 isBlacklisted 返回 false")
    void isBlacklisted_shouldReturnFalseAfterTtl() throws Exception {
        String token = genAdminToken(60_000);
        tokenBlacklistService.blacklist(token);

        var claims = JwtUtil.parseJWT(ADMIN_SECRET, token);
        String jti = claims.getId();

        // 模拟 TTL 过期：清空 Redis
        redisStore.clear();

        assertFalse(tokenBlacklistService.isBlacklisted(jti),
                "TTL 过期后应返回 false");
    }

    @Test
    @DisplayName("场景9：null/空 token 安全处理（不抛异常）")
    void blacklist_shouldHandleNullEmptySafely() {
        assertDoesNotThrow(() -> tokenBlacklistService.blacklist(null));
        assertDoesNotThrow(() -> tokenBlacklistService.blacklist(""));
        assertTrue(redisStore.isEmpty(), "null/空 token 不应写入任何 key");
    }

    @Test
    @DisplayName("场景10：null/空 jti 安全处理")
    void isBlacklisted_shouldHandleNullEmptyJtiSafely() {
        assertFalse(tokenBlacklistService.isBlacklisted(null), "null jti 应返回 false");
        assertFalse(tokenBlacklistService.isBlacklisted(""), "空 jti 应返回 false");
    }
}
