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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * E1 Refresh Token 端到端流程测试。
 * <p>
 * 补充 T3 单次 refresh 之外的流程场景：
 *   1. refresh 一次性使用：refresh 成功后旧 refresh 失效
 *   2. 单设备覆盖：新登录踢掉旧设备（旧 refresh 失效）
 *   3. 链式刷新：refresh → 新 refresh → 再 refresh（连续多次刷新都正常）
 * <p>
 * 使用 AtomicReference 模拟 Redis 存储，让 set/get 语义与真实 Redis 一致。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("E1 Refresh Token 端到端流程")
class RefreshTokenTest {

    private static final String SECRET = "test_admin_secret_key_at_least_32_chars__";
    private static final long ACCESS_TTL = 60000L;
    private static final long REFRESH_TTL = 600000L;

    @Mock private EmployeeMapper employeeMapper;
    @Mock private JwtProperties jwtProperties;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private LoginAttemptService loginAttemptService;

    @InjectMocks private EmployeeServiceImpl employeeService;

    /** 模拟 Redis 存储：key → value，让 set/get 语义与真实 Redis 一致 */
    private final Map<String, String> redisStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(jwtProperties.getAdminSecretKey()).thenReturn(SECRET);
        lenient().when(jwtProperties.getAdminTtl()).thenReturn(ACCESS_TTL);
        lenient().when(jwtProperties.getAdminRefreshTtl()).thenReturn(REFRESH_TTL);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        // 模拟 Redis set：写入内存 map
        lenient().doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String value = invocation.getArgument(1);
            redisStore.put(key, value);
            return null;
        }).when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        // 模拟 Redis get：从内存 map 读取
        lenient().when(valueOps.get(anyString())).thenAnswer(invocation ->
                redisStore.get(invocation.getArgument(0)));
    }

    private EmployeeLoginVO doLogin(String username, Long empId) {
        String hashed = new BCryptPasswordEncoder(10).encode("pwd");
        Employee emp = Employee.builder()
                .id(empId).username(username).name(username)
                .passwordHash(hashed).status(1).role(1)
                .build();
        when(employeeMapper.getByUsername(username)).thenReturn(emp);
        when(employeeMapper.getById(empId)).thenReturn(emp);

        EmployeeLoginDTO dto = new EmployeeLoginDTO();
        dto.setUsername(username);
        dto.setPassword("pwd");
        return employeeService.login(dto);
    }

    @Test
    @DisplayName("refresh 一次性使用：refresh 成功后旧 refresh 失效")
    void refresh_oldTokenShouldFailAfterRefresh() {
        // 1. 登录，拿到 refresh token
        EmployeeLoginVO loginVO = doLogin("admin", 1L);
        String oldRefresh = loginVO.getRefreshToken();

        // 2. 用 refresh 换新 token
        EmployeeLoginVO refreshed = employeeService.refresh(oldRefresh);
        String newRefresh = refreshed.getRefreshToken();
        assertNotEquals(oldRefresh, newRefresh, "refresh token 必须换新");

        // 3. 用旧 refresh 再调一次，应该失败（Redis 里已是新 refresh）
        assertThrows(BaseException.class, () -> employeeService.refresh(oldRefresh),
                "旧 refresh token 必须失效");
    }

    @Test
    @DisplayName("单设备覆盖：新登录后旧 refresh 失效")
    void login_newLoginShouldInvalidateOldRefresh() {
        // 1. 第一次登录
        EmployeeLoginVO firstLogin = doLogin("admin", 1L);
        String firstRefresh = firstLogin.getRefreshToken();

        // 2. 第二次登录（同账号，模拟另一设备登录）
        EmployeeLoginVO secondLogin = doLogin("admin", 1L);
        String secondRefresh = secondLogin.getRefreshToken();
        assertNotEquals(firstRefresh, secondRefresh, "两次登录的 refresh token 必须不同");

        // 3. 用第一次的 refresh 调 refresh，应该失败（Redis 已被第二次覆盖）
        assertThrows(BaseException.class, () -> employeeService.refresh(firstRefresh),
                "新登录后旧设备的 refresh token 必须失效");

        // 4. 用第二次的 refresh 调 refresh，应该成功
        EmployeeLoginVO refreshed = employeeService.refresh(secondRefresh);
        assertNotNull(refreshed.getToken());
        assertNotNull(refreshed.getRefreshToken());
    }

    @Test
    @DisplayName("链式刷新：连续多次 refresh 都正常")
    void refresh_chainedRefreshShouldWork() {
        EmployeeLoginVO loginVO = doLogin("admin", 1L);
        String currentRefresh = loginVO.getRefreshToken();

        // 连续 refresh 3 次，每次都应成功且换新
        for (int i = 0; i < 3; i++) {
            EmployeeLoginVO refreshed = employeeService.refresh(currentRefresh);
            assertNotNull(refreshed.getToken(), "第 " + (i + 1) + " 次 refresh 的 access token 不能为空");
            assertNotNull(refreshed.getRefreshToken(), "第 " + (i + 1) + " 次 refresh 的 refresh token 不能为空");
            assertNotEquals(currentRefresh, refreshed.getRefreshToken(),
                    "第 " + (i + 1) + " 次 refresh 的 token 必须换新");
            currentRefresh = refreshed.getRefreshToken();
        }
    }

    @Test
    @DisplayName("完整流程：login → refresh → 旧 access 仍可解析（无状态 JWT）")
    void refresh_oldAccessTokenStillParsableAfterRefresh() {
        // JWT 是无状态的，refresh 不会让旧 access token 解析失败（只是后端拦截器会用黑名单拒绝，E4 实现）
        EmployeeLoginVO loginVO = doLogin("admin", 1L);
        String oldAccess = loginVO.getToken();

        employeeService.refresh(loginVO.getRefreshToken());

        // 旧 access token 仍能被 JwtUtil 解析（黑名单逻辑在 E4）
        io.jsonwebtoken.Claims claims = com.stellar.utils.JwtUtil.parseJWT(SECRET, oldAccess);
        assertEquals("access", claims.get(JwtClaimsConstant.TOKEN_TYPE));
        assertEquals(1L, ((Number) claims.get(JwtClaimsConstant.EMP_ID)).longValue());
    }

    @Test
    @DisplayName("多用户并发：不同用户的 refresh 互不影响")
    void refresh_differentUsersShouldNotInterfere() {
        EmployeeLoginVO user1 = doLogin("admin", 1L);
        EmployeeLoginVO user2 = doLogin("operator", 2L);

        // user1 refresh 不影响 user2
        EmployeeLoginVO user1Refreshed = employeeService.refresh(user1.getRefreshToken());
        assertNotNull(user1Refreshed.getToken());

        // user2 的 refresh 仍有效
        EmployeeLoginVO user2Refreshed = employeeService.refresh(user2.getRefreshToken());
        assertNotNull(user2Refreshed.getToken());

        // user1 旧 refresh 失效，但 user2 旧 refresh 也失效（各自独立）
        assertThrows(BaseException.class, () -> employeeService.refresh(user1.getRefreshToken()));
        assertThrows(BaseException.class, () -> employeeService.refresh(user2.getRefreshToken()));
    }
}
