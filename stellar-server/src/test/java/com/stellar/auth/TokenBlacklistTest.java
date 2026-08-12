package com.stellar.auth;

import com.stellar.constant.JwtClaimsConstant;
import com.stellar.controller.admin.EmployeeController;
import com.stellar.exception.UnauthorizedException;
import com.stellar.interceptor.JwtTokenAdminInterceptor;
import com.stellar.properties.JwtProperties;
import com.stellar.service.EmployeeService;
import com.stellar.service.LoginAttemptService;
import com.stellar.service.TokenBlacklistService;
import com.stellar.service.impl.TokenBlacklistServiceImpl;
import com.stellar.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.method.HandlerMethod;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * E4 JWT 黑名单端到端集成测试。
 * <p>
 * 与 {@link TokenBlacklistServiceTest}（纯 Service 层单元测试）不同，本测试类覆盖
 * {@link EmployeeController#logout} 与 {@link JwtTokenAdminInterceptor} 的协作，
 * 验证完整登出→后续请求被拒的端到端行为。
 * <p>
 * 覆盖场景（对应 SPEC.md T12 要求）：
 *   1. 登出后用相同 access token 请求 → 拦截器抛 401
 *   2. refresh token 也被加入黑名单（登出时一并拉黑）
 *   3. 未登出的 token 正常通过拦截器
 *   4. TTL 自动清理：模拟 Redis key 过期后，相同 token 可正常通过
 *   5. Redis 不可用时拦截器降级放行（不抛 401）
 *   6. 登出时 header 无 token 也安全处理（不抛异常）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("E4 JWT 黑名单端到端流程")
class TokenBlacklistTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private EmployeeService employeeService;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private HandlerMethod handlerMethod;

    /** 测试密钥（≥32 字节，HS256 要求） */
    private static final String ADMIN_SECRET = "stellar-admin-secret-key-for-test-only-aaaaaaaaaaaa";

    private JwtProperties jwtProperties;
    private TokenBlacklistService tokenBlacklistService;
    private JwtTokenAdminInterceptor interceptor;
    private EmployeeController employeeController;

    /** 模拟 Redis 存储：key → value */
    private final Map<String, String> redisStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        // 真实 JwtProperties
        jwtProperties = new JwtProperties();
        jwtProperties.setAdminSecretKey(ADMIN_SECRET);
        jwtProperties.setAdminTokenName("token");

        // 真实 TokenBlacklistService（不 mock，验证端到端协作）
        tokenBlacklistService = new TokenBlacklistServiceImpl(stringRedisTemplate, jwtProperties);

        // 真实拦截器（不 mock，验证端到端协作）
        interceptor = new JwtTokenAdminInterceptor(jwtProperties, tokenBlacklistService);

        // EmployeeController 注入真实 tokenBlacklistService
        employeeController = new EmployeeController(employeeService, loginAttemptService, tokenBlacklistService);

        redisStore.clear();

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        lenient().when(stringRedisTemplate.hasKey(anyString())).thenAnswer(invocation ->
                redisStore.containsKey(invocation.getArgument(0)));
        lenient().when(stringRedisTemplate.delete(anyString())).thenAnswer(invocation ->
                redisStore.remove(invocation.getArgument(0)) != null);
    }

    /**
     * 生成 admin access token，TTL = ttlMillis
     */
    private String genAccessToken(long ttlMillis) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, 1L);
        return JwtUtil.createJWT(ADMIN_SECRET, ttlMillis, claims);
    }

    /**
     * 生成 admin refresh token，TTL = ttlMillis
     */
    private String genRefreshToken(long ttlMillis) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, 1L);
        return JwtUtil.createRefreshJWT(ADMIN_SECRET, ttlMillis, claims);
    }

    /**
     * 模拟拦截器 preHandle：用 token header 调用
     */
    private boolean preHandle(String token) throws Exception {
        lenient().when(request.getHeader("token")).thenReturn(token);
        lenient().when(request.getHeader("Authorization")).thenReturn(null);
        return interceptor.preHandle(request, response, handlerMethod);
    }

    @Test
    @DisplayName("场景1：登出后用相同 access token 请求 → 拦截器抛 401")
    void afterLogout_sameAccessTokenShouldBeRejected() throws Exception {
        String accessToken = genAccessToken(60_000);

        // 登出前：拦截器应放行
        assertTrue(preHandle(accessToken), "登出前 token 应通过拦截器");

        // 登出：直接传 accessToken 给 controller.logout（单元测试不经过 Spring MVC）
        employeeController.logout(accessToken, null, null);

        // 登出后：相同 token 应被拦截器拒绝（jti 已在黑名单）
        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> preHandle(accessToken));
        assertEquals("未登录或登录已过期", ex.getMessage());
    }

    @Test
    @DisplayName("场景2：登出时 refresh token 也被加入黑名单")
    void logout_shouldAlsoBlacklistRefreshToken() throws Exception {
        String accessToken = genAccessToken(60_000);
        String refreshToken = genRefreshToken(120_000);

        // 登出：access 从 header，refresh 从 body
        EmployeeController.LogoutRequest req = new EmployeeController.LogoutRequest();
        req.setRefreshToken(refreshToken);
        employeeController.logout(accessToken, null, req);

        // 两个 token 的 jti 都应在 Redis 黑名单中
        Claims accessClaims = JwtUtil.parseJWT(ADMIN_SECRET, accessToken);
        Claims refreshClaims = JwtUtil.parseJWT(ADMIN_SECRET, refreshToken);

        assertTrue(tokenBlacklistService.isBlacklisted(accessClaims.getId()),
                "access token 的 jti 应在黑名单");
        assertTrue(tokenBlacklistService.isBlacklisted(refreshClaims.getId()),
                "refresh token 的 jti 也应在黑名单（同步失效）");
    }

    @Test
    @DisplayName("场景3：未登出的 token 正常通过拦截器")
    void notLoggedOut_tokenShouldPass() throws Exception {
        String token = genAccessToken(60_000);

        assertTrue(preHandle(token), "未登出的 token 应通过拦截器");
    }

    @Test
    @DisplayName("场景4：TTL 自动清理 — 模拟 Redis key 过期后 token 可正常通过")
    void afterTtlExpires_tokenShouldPassAgain() throws Exception {
        String accessToken = genAccessToken(60_000);

        // 登出：加入黑名单
        employeeController.logout(accessToken, null, null);

        // 登出后：应被拒绝
        assertThrows(UnauthorizedException.class, () -> preHandle(accessToken),
                "登出后 token 应被拒绝");

        // 模拟 TTL 过期：清空 Redis
        redisStore.clear();

        // TTL 过期后：相同 token 应能通过（黑名单已清理）
        assertTrue(preHandle(accessToken), "TTL 过期后 token 应能通过");
    }

    @Test
    @DisplayName("场景5：Redis 不可用时拦截器降级放行（不抛 401）")
    void redisDown_interceptorShouldDegradeAndPass() throws Exception {
        String accessToken = genAccessToken(60_000);

        // 登出：尝试加入黑名单（Redis 写入会成功，因为 mock）
        employeeController.logout(accessToken, null, null);

        // 模拟 Redis 故障：hasKey 抛异常
        when(stringRedisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis 连接失败"));

        // 拦截器应降级放行（不抛 401），由 JWT 签名/过期校验兜底
        assertTrue(preHandle(accessToken), "Redis 不可用时应降级放行");
    }

    @Test
    @DisplayName("场景5b：Redis 不可用时 blacklist 也不抛异常（降级写入）")
    void redisDown_blacklistShouldNotThrow() {
        String accessToken = genAccessToken(60_000);

        // 模拟 Redis 写入故障
        doThrow(new RuntimeException("Redis 连接失败"))
                .when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        // 不应抛异常
        assertDoesNotThrow(() -> tokenBlacklistService.blacklist(accessToken));
    }

    @Test
    @DisplayName("场景6：登出时 header 无 token 也安全处理（不抛异常）")
    void logout_withoutTokenHeaderShouldBeSafe() {
        // 不传任何 token，body 也为 null
        assertDoesNotThrow(() -> employeeController.logout(null, null, null));

        // employeeService.logout 仍被调用
        verify(employeeService, times(1)).logout();
    }

    @Test
    @DisplayName("场景7：Authorization=Bearer xxx header 也能正确提取并加入黑名单")
    void logout_shouldExtractTokenFromAuthorizationHeader() throws Exception {
        String accessToken = genAccessToken(60_000);

        // 用 Authorization=Bearer xxx 形式登出
        employeeController.logout(null, "Bearer " + accessToken, null);

        // jti 应在黑名单
        Claims claims = JwtUtil.parseJWT(ADMIN_SECRET, accessToken);
        assertTrue(tokenBlacklistService.isBlacklisted(claims.getId()),
                "从 Authorization header 提取的 token 也应加入黑名单");
    }

    @Test
    @DisplayName("场景8：登出后 refresh token 也不能再用于换新 token（jti 在黑名单）")
    void afterLogout_refreshTokenJtiShouldBeBlacklisted() {
        String refreshToken = genRefreshToken(120_000);

        // 仅拉黑 refresh token（模拟只传 refresh 的场景）
        tokenBlacklistService.blacklist(refreshToken);

        Claims claims = JwtUtil.parseJWT(ADMIN_SECRET, refreshToken);
        assertTrue(tokenBlacklistService.isBlacklisted(claims.getId()),
                "refresh token 的 jti 应在黑名单，不能再用");
    }
}
