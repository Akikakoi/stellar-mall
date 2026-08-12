package com.stellar.auth;

import com.stellar.constant.JwtClaimsConstant;
import com.stellar.entity.MallUser;
import com.stellar.exception.BaseException;
import com.stellar.mapper.MallUserMapper;
import com.stellar.properties.JwtProperties;
import com.stellar.service.LoginAttemptService;
import com.stellar.service.impl.MallUserServiceImpl;
import com.stellar.vo.MallUserLoginVO;
import org.junit.jupiter.api.BeforeEach;
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
 * C 端用户 refresh token 单元测试（纯 Mockito）。
 * 覆盖：正常刷新、refresh 不匹配拒绝、access token 冒充拒绝、非法 token 拒绝。
 */
@ExtendWith(MockitoExtension.class)
class MallUserServiceRefreshTest {

    private static final String SECRET = "test_user_secret_key_at_least_32_chars___";
    private static final long ACCESS_TTL = 60000L;
    private static final long REFRESH_TTL = 600000L;

    @Mock private MallUserMapper mallUserMapper;
    @Mock private JwtProperties jwtProperties;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private LoginAttemptService loginAttemptService;

    @InjectMocks private MallUserServiceImpl userService;

    @BeforeEach
    void setUp() {
        lenient().when(jwtProperties.getUserSecretKey()).thenReturn(SECRET);
        lenient().when(jwtProperties.getUserTtl()).thenReturn(ACCESS_TTL);
        lenient().when(jwtProperties.getUserRefreshTtl()).thenReturn(REFRESH_TTL);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void refresh_validTokenShouldReturnNewTokens() {
        MallUser user = MallUser.builder().id(100L).email("a@b.com").status(1).build();
        when(mallUserMapper.getById(100L)).thenReturn(user);

        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, 100L);
        String oldRefresh = com.stellar.utils.JwtUtil.createRefreshJWT(SECRET, REFRESH_TTL, claims);

        when(valueOps.get("refresh:mall_user:100")).thenReturn(oldRefresh);

        MallUserLoginVO vo = userService.refresh(oldRefresh);

        assertNotNull(vo.getToken());
        assertNotNull(vo.getRefreshToken());
        assertNotEquals(oldRefresh, vo.getRefreshToken());
        verify(valueOps).set(eq("refresh:mall_user:100"), eq(vo.getRefreshToken()), eq(REFRESH_TTL), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void refresh_tokenNotInRedisShouldReject() {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, 101L);
        String refresh = com.stellar.utils.JwtUtil.createRefreshJWT(SECRET, REFRESH_TTL, claims);

        when(valueOps.get("refresh:mall_user:101")).thenReturn(null);

        assertThrows(BaseException.class, () -> userService.refresh(refresh));
    }

    @Test
    void refresh_tokenMismatchRedisShouldReject() {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, 102L);
        String refresh = com.stellar.utils.JwtUtil.createRefreshJWT(SECRET, REFRESH_TTL, claims);

        when(valueOps.get("refresh:mall_user:102")).thenReturn("another_token");

        assertThrows(BaseException.class, () -> userService.refresh(refresh));
    }

    @Test
    void refresh_accessTypeTokenShouldReject() {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, 103L);
        String accessToken = com.stellar.utils.JwtUtil.createJWT(SECRET, ACCESS_TTL, claims);

        assertThrows(BaseException.class, () -> userService.refresh(accessToken));
    }

    @Test
    void refresh_invalidTokenShouldReject() {
        assertThrows(Exception.class, () -> userService.refresh("invalid.token.here"));
    }
}
