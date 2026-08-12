package com.stellar.auth;

import com.stellar.dto.EmployeeLoginDTO;
import com.stellar.entity.Employee;
import com.stellar.exception.BaseException;
import com.stellar.exception.LoginFailedException;
import com.stellar.mapper.EmployeeMapper;
import com.stellar.properties.JwtProperties;
import com.stellar.service.LoginAttemptService;
import com.stellar.service.impl.EmployeeServiceImpl;
import com.stellar.service.impl.LoginAttemptServiceImpl;
import com.stellar.vo.EmployeeLoginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * E2 账号锁定端到端集成测试。
 * <p>
 * 与 {@link LoginAttemptServiceTest}（纯单元测试）不同，本测试类使用真实的
 * {@link LoginAttemptServiceImpl} 与 {@link EmployeeServiceImpl} 协作，
 * 验证完整登录流程中失败计数、锁定、解锁的端到端行为。
 * <p>
 * 覆盖场景（对应 SPEC.md T7）：
 *   1. 5 次失败登录后账号被锁定
 *   2. 锁定中即使密码正确也拒绝登录
 *   3. 15 分钟后自动恢复（模拟 TTL 过期）
 *   4. 成功登录后清零失败计数
 *   5. 手动解锁后可立即登录
 *   6. 锁定提示消息包含剩余分钟数
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("E2 账号锁定端到端流程")
class AccountLockTest {

    private static final String SECRET = "test_admin_secret_key_at_least_32_chars__";
    private static final long ACCESS_TTL = 60000L;
    private static final long REFRESH_TTL = 600000L;
    private static final String USERNAME = "admin";
    private static final String CORRECT_PWD = "correct-pwd-123";
    private static final String WRONG_PWD = "wrong-pwd-456";

    @Mock private EmployeeMapper employeeMapper;
    @Mock private JwtProperties jwtProperties;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    /** 真实的 LoginAttemptService 实现（不 mock，验证端到端协作） */
    private LoginAttemptService loginAttemptService;

    /** 被测对象：EmployeeServiceImpl */
    private EmployeeServiceImpl employeeService;

    /** 模拟 Redis 存储：key → value */
    private final Map<String, String> redisStore = new HashMap<>();

    /** 已哈希的正确密码（setUp 时初始化） */
    private String hashedCorrectPwd;

    @BeforeEach
    void setUp() {
        // 手动构造真实 LoginAttemptServiceImpl，让它与 EmployeeServiceImpl 共享同一份 Redis mock
        loginAttemptService = new LoginAttemptServiceImpl(stringRedisTemplate);
        employeeService = new EmployeeServiceImpl(
                employeeMapper, jwtProperties, stringRedisTemplate, loginAttemptService);

        redisStore.clear();
        hashedCorrectPwd = new BCryptPasswordEncoder(10).encode(CORRECT_PWD);

        lenient().when(jwtProperties.getAdminSecretKey()).thenReturn(SECRET);
        lenient().when(jwtProperties.getAdminTtl()).thenReturn(ACCESS_TTL);
        lenient().when(jwtProperties.getAdminRefreshTtl()).thenReturn(REFRESH_TTL);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        // 模拟 Redis set(key, value, ttl, unit) → 写入 map
        lenient().doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        // 模拟 Redis increment → 计数
        lenient().when(valueOps.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long cur = Long.parseLong(redisStore.getOrDefault(key, "0"));
            cur++;
            redisStore.put(key, String.valueOf(cur));
            return cur;
        });

        // 模拟 Redis get → 读取
        lenient().when(valueOps.get(anyString())).thenAnswer(invocation ->
                redisStore.get(invocation.getArgument(0)));

        // 模拟 getExpire → 返回固定 10 分钟（剩余 TTL）
        lenient().when(stringRedisTemplate.getExpire(anyString(), any(TimeUnit.class)))
                .thenReturn(10L);

        // 模拟 delete → 从 map 删除
        lenient().when(stringRedisTemplate.delete(anyString())).thenAnswer(invocation ->
                redisStore.remove(invocation.getArgument(0)) != null);

        // 模拟 expire → 返回 true（TTL 设置成功，测试通过 delete 模拟过期）
        lenient().when(stringRedisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
    }

    /**
     * 构造一个启用状态的员工，密码为 CORRECT_PWD。
     */
    private Employee buildEmployee() {
        return Employee.builder()
                .id(1L)
                .username(USERNAME)
                .name(USERNAME)
                .passwordHash(hashedCorrectPwd)
                .status(1)
                .role(1)
                .build();
    }

    /**
     * 用错误密码发起一次登录（触发 recordFailure）。
     * 注：用 lenient() 是因为账号被锁定时 checkLocked 先抛异常，mapper 不会被调用，
     * Mockito 严格模式会判为多余 stub。
     */
    private void attemptWrongLogin() {
        Employee emp = buildEmployee();
        lenient().when(employeeMapper.getByUsername(USERNAME)).thenReturn(emp);

        EmployeeLoginDTO dto = new EmployeeLoginDTO();
        dto.setUsername(USERNAME);
        dto.setPassword(WRONG_PWD);
        // 抛 LoginFailedException 是预期的
        assertThrows(LoginFailedException.class, () -> employeeService.login(dto));
    }

    /**
     * 用正确密码发起一次登录，返回 VO。
     * 注：账号被锁定时 checkLocked 先抛异常，mapper 不会被调用，故用 lenient()。
     */
    private EmployeeLoginVO attemptCorrectLogin() {
        Employee emp = buildEmployee();
        lenient().when(employeeMapper.getByUsername(USERNAME)).thenReturn(emp);

        EmployeeLoginDTO dto = new EmployeeLoginDTO();
        dto.setUsername(USERNAME);
        dto.setPassword(CORRECT_PWD);
        return employeeService.login(dto);
    }

    @Test
    @DisplayName("场景1：连续 5 次错误密码后账号被锁定")
    void fiveFailuresShouldLockAccount() {
        for (int i = 0; i < 5; i++) {
            attemptWrongLogin();
        }

        // 第 6 次尝试（哪怕此时换成正确密码）应在 checkLocked 阶段抛 BaseException
        BaseException ex = assertThrows(BaseException.class, this::attemptCorrectLogin);
        assertNotNull(ex.getMessage(), "锁定异常消息不应为空");
        // 消息应包含"锁定"或"分钟"
        assertTrue(ex.getMessage().contains("锁定") || ex.getMessage().contains("分钟"),
                "异常消息应含锁定提示，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("场景2：锁定中即使密码正确也拒绝登录")
    void lockedAccountShouldRejectEvenWithCorrectPassword() {
        // 触发 5 次失败锁定
        for (int i = 0; i < 5; i++) {
            attemptWrongLogin();
        }

        // 用正确密码登录，仍应被拒绝（checkLocked 先于密码校验）
        BaseException ex = assertThrows(BaseException.class, this::attemptCorrectLogin);
        // 应是 BaseException（锁定），不是 LoginFailedException（密码错误）
        assertNotEquals(LoginFailedException.class, ex.getClass(),
                "锁定中应抛 BaseException 而非 LoginFailedException，实际类型: " + ex.getClass().getSimpleName());
    }

    @Test
    @DisplayName("场景3：锁定 15 分钟后自动恢复（模拟 TTL 过期）")
    void accountShouldRecoverAfterTtlExpires() {
        // 触发 5 次失败锁定
        for (int i = 0; i < 5; i++) {
            attemptWrongLogin();
        }
        // 确认已锁定
        assertThrows(BaseException.class, this::attemptCorrectLogin);

        // 模拟 Redis TTL 过期：清空对应 key
        redisStore.remove("login_fail:employee:" + USERNAME);

        // 此时用正确密码登录应成功
        EmployeeLoginVO vo = attemptCorrectLogin();
        assertNotNull(vo.getToken(), "TTL 过期后应能正常登录拿到 token");
        assertNotNull(vo.getRefreshToken(), "TTL 过期后应能正常登录拿到 refresh token");
    }

    @Test
    @DisplayName("场景4：成功登录后清零失败计数（不残留）")
    void successfulLoginShouldResetFailureCounter() {
        // 失败 3 次（未到阈值）
        for (int i = 0; i < 3; i++) {
            attemptWrongLogin();
        }

        // 用正确密码登录成功（应触发 clearAttempts）
        EmployeeLoginVO vo = attemptCorrectLogin();
        assertNotNull(vo.getToken(), "登录成功应返回 token");

        // 计数应已清零，再失败 4 次不应锁定（如果计数未清零，3+4=7 ≥5 会锁定）
        for (int i = 0; i < 4; i++) {
            attemptWrongLogin();
        }
        // 第 5 次（清零后的第 5 次）才会锁定，4 次不应锁
        assertDoesNotThrow(() -> {
            // 这里只是验证 checkLocked 不抛异常，不实际登录
            // 用一次正确密码登录验证未锁定
        });
        // 用正确密码登录应仍可成功（说明 4 次失败未触发锁定）
        EmployeeLoginVO vo2 = attemptCorrectLogin();
        assertNotNull(vo2.getToken(), "成功登录清零后，再失败 4 次不应锁定，应能正常登录");
    }

    @Test
    @DisplayName("场景5：手动解锁后可立即登录")
    void manualUnlockShouldRestoreLoginImmediately() {
        // 触发 5 次失败锁定
        for (int i = 0; i < 5; i++) {
            attemptWrongLogin();
        }
        // 确认已锁定
        assertThrows(BaseException.class, this::attemptCorrectLogin);

        // 管理员手动解锁
        loginAttemptService.unlock("employee", USERNAME);

        // 立即用正确密码登录应成功
        EmployeeLoginVO vo = attemptCorrectLogin();
        assertNotNull(vo.getToken(), "手动解锁后应能立即登录");
        assertNotNull(vo.getRefreshToken(), "手动解锁后应能立即拿到 refresh token");
    }

    @Test
    @DisplayName("场景6：锁定提示消息包含剩余分钟数")
    void lockMessageShouldContainRemainingMinutes() {
        for (int i = 0; i < 5; i++) {
            attemptWrongLogin();
        }

        BaseException ex = assertThrows(BaseException.class, this::attemptCorrectLogin);
        String msg = ex.getMessage();
        assertNotNull(msg);
        // 消息格式："登录失败次数过多，账号已被临时锁定，请 X 分钟后重试"
        assertTrue(msg.contains("分钟"), "锁定消息应包含剩余分钟数，实际: " + msg);
        // 验证消息中含数字（分钟数）
        assertTrue(msg.replaceAll("[^0-9]", "").length() > 0,
                "锁定消息应包含数字分钟数，实际: " + msg);
    }

    @Test
    @DisplayName("场景7：未达到阈值（4 次失败）不锁定")
    void fourFailuresShouldNotLockAccount() {
        for (int i = 0; i < 4; i++) {
            attemptWrongLogin();
        }

        // 第 5 次用正确密码应能登录成功（计数未到阈值，未锁定）
        EmployeeLoginVO vo = attemptCorrectLogin();
        assertNotNull(vo.getToken(), "4 次失败后用正确密码应能登录");
    }
}
