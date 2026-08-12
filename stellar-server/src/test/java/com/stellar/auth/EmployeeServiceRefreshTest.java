package com.stellar.auth;

import com.stellar.constant.JwtClaimsConstant;
import com.stellar.dto.EmployeeLoginDTO;
import com.stellar.entity.Employee;
import com.stellar.exception.BaseException;
import com.stellar.mapper.EmployeeMapper;
import com.stellar.properties.JwtProperties;
import com.stellar.service.LoginAttemptService;
import com.stellar.service.impl.EmployeeServiceImpl;
import com.stellar.vo.EmployeeLoginVO;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 员工 refresh token 单元测试（纯 Mockito，不依赖 Redis/MySQL）。
 * 覆盖：正常刷新、refresh token 一次性使用、Redis 不匹配拒绝、非法 token 拒绝。
 */
@ExtendWith(MockitoExtension.class)
class EmployeeServiceRefreshTest {

    private static final String SECRET = "test_admin_secret_key_at_least_32_chars__";
    private static final long ACCESS_TTL = 60000L;
    private static final long REFRESH_TTL = 600000L;

    @Mock private EmployeeMapper employeeMapper;
    @Mock private JwtProperties jwtProperties;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private LoginAttemptService loginAttemptService;

    @InjectMocks private EmployeeServiceImpl employeeService;

    @BeforeEach
    void setUp() {
        lenient().when(jwtProperties.getAdminSecretKey()).thenReturn(SECRET);
        lenient().when(jwtProperties.getAdminTtl()).thenReturn(ACCESS_TTL);
        lenient().when(jwtProperties.getAdminRefreshTtl()).thenReturn(REFRESH_TTL);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void login_shouldIssueAccessAndRefreshToken() {
        // 用真实 BCrypt hash，避免密码校验失败
        String hashed = new BCryptPasswordEncoder(10).encode("pwd");
        Employee emp = Employee.builder()
                .id(1L).username("admin").name("管理员")
                .passwordHash(hashed).status(1).role(1)
                .build();
        when(employeeMapper.getByUsername("admin")).thenReturn(emp);

        EmployeeLoginDTO dto = new EmployeeLoginDTO();
        dto.setUsername("admin");
        dto.setPassword("pwd");
        EmployeeLoginVO vo = employeeService.login(dto);

        assertNotNull(vo.getToken(), "access token 必须签发");
        assertNotNull(vo.getRefreshToken(), "refresh token 必须签发");
        assertNotEquals(vo.getToken(), vo.getRefreshToken());

        // refresh token 应写入 Redis（单设备覆盖）
        verify(valueOps).set(eq("refresh:employee:1"), eq(vo.getRefreshToken()), eq(REFRESH_TTL), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void refresh_validTokenShouldReturnNewTokens() {
        // 先签发一个 refresh token
        Employee emp = Employee.builder().id(2L).name("op").role(2).build();
        when(employeeMapper.getById(2L)).thenReturn(emp);

        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, 2L);
        String oldRefresh = com.stellar.utils.JwtUtil.createRefreshJWT(SECRET, REFRESH_TTL, claims);

        when(valueOps.get("refresh:employee:2")).thenReturn(oldRefresh);

        EmployeeLoginVO vo = employeeService.refresh(oldRefresh);

        assertNotNull(vo.getToken());
        assertNotNull(vo.getRefreshToken());
        assertNotEquals(oldRefresh, vo.getRefreshToken(), "refresh token 必须换新");

        // 新 refresh 应覆盖 Redis
        verify(valueOps).set(eq("refresh:employee:2"), eq(vo.getRefreshToken()), eq(REFRESH_TTL), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void refresh_tokenNotInRedisShouldReject() {
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, 3L);
        String refresh = com.stellar.utils.JwtUtil.createRefreshJWT(SECRET, REFRESH_TTL, claims);

        when(valueOps.get("refresh:employee:3")).thenReturn(null);

        BaseException ex = assertThrows(BaseException.class, () -> employeeService.refresh(refresh));
        assertTrue(ex.getMessage().contains("refresh") || ex.getMessage().contains("无效") || ex.getMessage().contains("过期"));
    }

    @Test
    void refresh_tokenMismatchRedisShouldReject() {
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, 4L);
        String refresh = com.stellar.utils.JwtUtil.createRefreshJWT(SECRET, REFRESH_TTL, claims);

        // Redis 存的是另一个 token（模拟被覆盖后的旧 token）
        when(valueOps.get("refresh:employee:4")).thenReturn("a_different_token");

        assertThrows(BaseException.class, () -> employeeService.refresh(refresh));
    }

    @Test
    void refresh_invalidTokenShouldReject() {
        assertThrows(Exception.class, () -> employeeService.refresh("not.a.valid.token"));
    }

    @Test
    void refresh_accessTypeTokenShouldReject() {
        // 用 access token 冒充 refresh token 应被拒绝
        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, 5L);
        String accessToken = com.stellar.utils.JwtUtil.createJWT(SECRET, ACCESS_TTL, claims);

        assertThrows(BaseException.class, () -> employeeService.refresh(accessToken));
    }
}
