package com.stellar.auth;

import com.stellar.exception.BaseException;
import com.stellar.service.impl.LoginAttemptServiceImpl;
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
 * LoginAttemptService 单元测试。
 * 用 HashMap 模拟 Redis，验证计数、锁定、解锁、清零逻辑。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("E2 登录失败计数与账号锁定")
class LoginAttemptServiceTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private LoginAttemptServiceImpl loginAttemptService;

    /** 模拟 Redis 存储 */
    private final Map<String, String> redisStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        // set(key, value, ttl, unit) → 写入 map（不模拟 TTL 过期，测试手动删 key 模拟）
        lenient().doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        // increment → 模拟计数
        lenient().when(valueOps.increment(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            long cur = Long.parseLong(redisStore.getOrDefault(key, "0"));
            cur++;
            redisStore.put(key, String.valueOf(cur));
            return cur;
        });

        // get → 读取
        lenient().when(valueOps.get(anyString())).thenAnswer(invocation ->
                redisStore.get(invocation.getArgument(0)));

        // getExpire → 返回固定 10 分钟（模拟剩余 TTL）
        lenient().when(stringRedisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(10L);

        // delete → 从 map 删除
        lenient().when(stringRedisTemplate.delete(anyString())).thenAnswer(invocation -> {
            return redisStore.remove(invocation.getArgument(0)) != null;
        });

        // expire → 设置 TTL（返回 true，测试通过 delete 模拟过期）
        lenient().when(stringRedisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
    }

    @Test
    @DisplayName("前 4 次失败不锁定")
    void checkLocked_firstFourFailuresShouldNotLock() {
        for (int i = 1; i <= 4; i++) {
            loginAttemptService.recordFailure("employee", "admin");
            // 第 4 次失败后应不抛异常（未到阈值 5）
            assertDoesNotThrow(() -> loginAttemptService.checkLocked("employee", "admin"));
        }
    }

    @Test
    @DisplayName("第 5 次失败后锁定，checkLocked 抛异常含剩余分钟数")
    void checkLocked_fifthFailureShouldLock() {
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure("employee", "admin");
        }
        BaseException ex = assertThrows(BaseException.class,
                () -> loginAttemptService.checkLocked("employee", "admin"));
        assertTrue(ex.getMessage().contains("锁定") || ex.getMessage().contains("分钟"),
                "异常消息应含锁定提示，实际: " + ex.getMessage());
    }

    @Test
    @DisplayName("锁定后 15 分钟自动恢复（模拟 TTL 过期）")
    void checkLocked_shouldRecoverAfterTtl() {
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure("employee", "admin");
        }
        // 确认已锁定
        assertThrows(BaseException.class, () -> loginAttemptService.checkLocked("employee", "admin"));

        // 模拟 TTL 过期：删 key
        redisStore.clear();

        // 应恢复
        assertDoesNotThrow(() -> loginAttemptService.checkLocked("employee", "admin"));
    }

    @Test
    @DisplayName("成功登录清零计数")
    void clearAttempts_shouldResetCounter() {
        for (int i = 0; i < 3; i++) {
            loginAttemptService.recordFailure("employee", "admin");
        }
        loginAttemptService.clearAttempts("employee", "admin");

        // 清零后再失败 4 次不应锁定
        for (int i = 0; i < 4; i++) {
            loginAttemptService.recordFailure("employee", "admin");
        }
        assertDoesNotThrow(() -> loginAttemptService.checkLocked("employee", "admin"));
    }

    @Test
    @DisplayName("手动解锁清除锁定状态")
    void unlock_shouldClearLock() {
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure("employee", "admin");
        }
        assertThrows(BaseException.class, () -> loginAttemptService.checkLocked("employee", "admin"));

        loginAttemptService.unlock("employee", "admin");

        assertDoesNotThrow(() -> loginAttemptService.checkLocked("employee", "admin"));
    }

    @Test
    @DisplayName("不同账号的计数互不影响")
    void differentAccountsShouldNotInterfere() {
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure("employee", "admin");
        }
        // admin 已锁定
        assertThrows(BaseException.class, () -> loginAttemptService.checkLocked("employee", "admin"));

        // operator 未锁定
        assertDoesNotThrow(() -> loginAttemptService.checkLocked("employee", "operator"));
    }

    @Test
    @DisplayName("不同类型的计数互不影响")
    void differentTypesShouldNotInterfere() {
        for (int i = 0; i < 5; i++) {
            loginAttemptService.recordFailure("employee", "admin");
        }
        // employee:admin 已锁定
        assertThrows(BaseException.class, () -> loginAttemptService.checkLocked("employee", "admin"));

        // mall_user:admin 未锁定（同 account 不同 type）
        assertDoesNotThrow(() -> loginAttemptService.checkLocked("mall_user", "admin"));
    }
}
