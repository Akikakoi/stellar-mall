package com.stellar.auth;

import com.stellar.service.impl.CaptchaServiceImpl;
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
 * CaptchaService 单元测试（E3）。
 * <p>
 * 用 HashMap 模拟 Redis，验证生成、校验、过期、一次性使用、大小写不敏感等行为。
 * T10 的 CaptchaTest 会做端到端覆盖，本类聚焦 Service 层逻辑。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("E3 图形验证码 CaptchaService")
class CaptchaServiceTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks private CaptchaServiceImpl captchaService;

    /** 模拟 Redis 存储：key → value */
    private final Map<String, String> redisStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        // set(key, value, ttl, unit) → 写入 map
        lenient().doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        // get → 读取
        lenient().when(valueOps.get(anyString())).thenAnswer(invocation ->
                redisStore.get(invocation.getArgument(0)));

        // delete → 从 map 删除
        lenient().when(stringRedisTemplate.delete(anyString())).thenAnswer(invocation ->
                redisStore.remove(invocation.getArgument(0)) != null);
    }

    @Test
    @DisplayName("generate 返回 captchaId + imageBase64，且写入 Redis")
    void generateShouldReturnCaptchaIdAndImage() {
        Map<String, String> result = captchaService.generate();

        assertNotNull(result.get("captchaId"), "captchaId 不能为空");
        assertNotNull(result.get("imageBase64"), "imageBase64 不能为空");
        assertTrue(result.get("imageBase64").startsWith("data:image/png;base64,"),
                "imageBase64 应为 data URI 格式，实际: " + result.get("imageBase64").substring(0, Math.min(30, result.get("imageBase64").length())));

        // Redis 中应存在对应 key
        String redisKey = "captcha:" + result.get("captchaId");
        assertNotNull(redisStore.get(redisKey), "Redis 中应存有验证码");
        assertTrue(redisStore.get(redisKey).length() > 0, "验证码值不能为空");
    }

    @Test
    @DisplayName("每次 generate 生成不同的 captchaId")
    void generateShouldProduceUniqueIds() {
        Map<String, String> r1 = captchaService.generate();
        Map<String, String> r2 = captchaService.generate();
        assertNotEquals(r1.get("captchaId"), r2.get("captchaId"),
                "两次 generate 的 captchaId 必须不同");
    }

    @Test
    @DisplayName("正确验证码校验通过")
    void validateShouldPassWithCorrectCode() {
        // 1. 生成
        Map<String, String> gen = captchaService.generate();
        String captchaId = gen.get("captchaId");
        String storedCode = redisStore.get("captcha:" + captchaId);

        // 2. 校验（用 Redis 中存的码）
        boolean ok = captchaService.validate(captchaId, storedCode);
        assertTrue(ok, "正确验证码应校验通过");
    }

    @Test
    @DisplayName("错误验证码校验失败")
    void validateShouldFailWithWrongCode() {
        Map<String, String> gen = captchaService.generate();
        boolean ok = captchaService.validate(gen.get("captchaId"), "WRONG_CODE");
        assertFalse(ok, "错误验证码应校验失败");
    }

    @Test
    @DisplayName("验证码大小写不敏感")
    void validateShouldBeCaseInsensitive() {
        Map<String, String> gen = captchaService.generate();
        String captchaId = gen.get("captchaId");
        String storedCode = redisStore.get("captcha:" + captchaId);

        // 转小写后校验应通过（存储时已转大写）
        boolean ok = captchaService.validate(captchaId, storedCode.toLowerCase());
        assertTrue(ok, "验证码大小写不敏感，小写应通过");
    }

    @Test
    @DisplayName("校验后立即失效（一次性使用，防重放）")
    void validateShouldDeleteAfterUse() {
        Map<String, String> gen = captchaService.generate();
        String captchaId = gen.get("captchaId");
        String storedCode = redisStore.get("captcha:" + captchaId);

        // 第一次校验通过
        assertTrue(captchaService.validate(captchaId, storedCode), "首次校验应通过");

        // 第二次用相同 code 应失败（key 已删除）
        assertFalse(captchaService.validate(captchaId, storedCode),
                "一次性使用：第二次校验应失败（已删除）");
    }

    @Test
    @DisplayName("错误验证码也会删除 key（防暴力枚举）")
    void validateShouldDeleteEvenIfWrong() {
        Map<String, String> gen = captchaService.generate();
        String captchaId = gen.get("captchaId");

        // 错误校验
        assertFalse(captchaService.validate(captchaId, "WRONG"));

        // key 应已被删除（防暴力枚举）
        assertNull(redisStore.get("captcha:" + captchaId),
                "错误校验也应删除 key，防止暴力枚举");
    }

    @Test
    @DisplayName("captchaId 不存在（已过期）校验失败")
    void validateShouldFailIfExpired() {
        boolean ok = captchaService.validate("non-existent-id", "ANY");
        assertFalse(ok, "captchaId 不存在应校验失败");
    }

    @Test
    @DisplayName("captchaId 或 code 为空校验失败")
    void validateShouldFailWithNullArgs() {
        assertFalse(captchaService.validate(null, "code"), "captchaId=null 应失败");
        assertFalse(captchaService.validate("id", null), "captchaCode=null 应失败");
        assertFalse(captchaService.validate("", "code"), "captchaId=空字符串应失败");
        assertFalse(captchaService.validate("id", ""), "captchaCode=空字符串应失败");
    }

    @Test
    @DisplayName("模拟 TTL 过期：Redis 删除 key 后校验失败")
    void validateShouldFailAfterTtl() {
        Map<String, String> gen = captchaService.generate();
        String captchaId = gen.get("captchaId");
        String storedCode = redisStore.get("captcha:" + captchaId);

        // 模拟 TTL 过期
        redisStore.clear();

        assertFalse(captchaService.validate(captchaId, storedCode),
                "TTL 过期后应校验失败");
    }
}
