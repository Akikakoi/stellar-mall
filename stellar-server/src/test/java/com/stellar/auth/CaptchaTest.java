package com.stellar.auth;

import com.stellar.controller.user.EmailController;
import com.stellar.entity.EmailCode;
import com.stellar.exception.BaseException;
import com.stellar.service.CaptchaService;
import com.stellar.service.NotificationService;
import com.stellar.service.impl.CaptchaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * E3 图形验证码端到端集成测试。
 * <p>
 * 与 {@link CaptchaServiceTest}（纯 Service 层单元测试）不同，本测试类覆盖
 * {@link EmailController#sendCode} 与 {@link CaptchaService} 的协作，
 * 验证完整 send-code 流程中图形验证码校验行为。
 * <p>
 * 覆盖场景（对应 SPEC.md T10）：
 *   1. 无图形验证码（captchaId/captchaCode 缺失）→ 拒绝
 *   2. 图形验证码错误 → 拒绝
 *   3. 图形验证码正确 → 通过，调用 NotificationService.sendEmailCode
 *   4. 2 分钟后过期（模拟 TTL）→ 拒绝
 *   5. 一次性使用：正确校验后再次用相同 captchaId 校验失败
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("E3 图形验证码端到端流程")
class CaptchaTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private NotificationService notificationService;

    /** 真实的 CaptchaService（不 mock，验证端到端协作） */
    private CaptchaService captchaService;

    /** 被测对象：EmailController */
    private EmailController emailController;

    /** 模拟 Redis 存储：key → value */
    private final Map<String, String> redisStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        // 真实 CaptchaServiceImpl 与 EmailController 共享同一份 Redis mock
        captchaService = new CaptchaServiceImpl(stringRedisTemplate);
        emailController = new EmailController(notificationService, captchaService);
        // EmailController.mailEnabled 是 @Value 注入，测试中通过反射设值
        ReflectionTestUtils.setField(emailController, "mailEnabled", false);

        redisStore.clear();

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

        // mock NotificationService.sendEmailCode 返回一个 EmailCode（验证码已发送）
        lenient().when(notificationService.sendEmailCode(anyString(), anyString()))
                .thenAnswer(invocation -> EmailCode.builder()
                        .email(invocation.getArgument(0))
                        .code("123456")
                        .type(invocation.getArgument(1))
                        .used(0)
                        .expireTime(LocalDateTime.now().plusMinutes(5))
                        .createTime(LocalDateTime.now())
                        .build());
    }

    /**
     * 生成图形验证码，返回 captchaId 与 Redis 中存的 code（已大写）。
     */
    private String[] generateCaptcha() {
        Map<String, String> result = captchaService.generate();
        String captchaId = result.get("captchaId");
        String storedCode = redisStore.get("captcha:" + captchaId);
        return new String[]{captchaId, storedCode};
    }

    /**
     * 构造一个合法的 EmailSendDTO。
     */
    private EmailController.EmailSendDTO buildDto(String email, String captchaId, String captchaCode) {
        EmailController.EmailSendDTO dto = new EmailController.EmailSendDTO();
        dto.setEmail(email);
        dto.setType("LOGIN");
        dto.setCaptchaId(captchaId);
        dto.setCaptchaCode(captchaCode);
        return dto;
    }

    @Test
    @DisplayName("场景1：无图形验证码（captchaId 缺失）→ 抛 CAPTCHA_REQUIRED")
    void sendCode_shouldRejectWhenCaptchaIdMissing() {
        EmailController.EmailSendDTO dto = buildDto("user@example.com", null, null);

        BaseException ex = assertThrows(BaseException.class,
                () -> emailController.sendCode(dto));
        assertEquals("请先完成图形验证码校验", ex.getMessage(),
                "缺图形验证码应抛 CAPTCHA_REQUIRED 提示");

        // NotificationService 不应被调用
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("场景1b：仅 captchaCode 缺失 → 也拒绝")
    void sendCode_shouldRejectWhenCaptchaCodeMissing() {
        String[] cap = generateCaptcha();
        EmailController.EmailSendDTO dto = buildDto("user@example.com", cap[0], null);

        BaseException ex = assertThrows(BaseException.class,
                () -> emailController.sendCode(dto));
        assertEquals("请先完成图形验证码校验", ex.getMessage());

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("场景2：图形验证码错误 → 抛 CAPTCHA_INVALID")
    void sendCode_shouldRejectWhenCaptchaCodeWrong() {
        String[] cap = generateCaptcha();
        EmailController.EmailSendDTO dto = buildDto("user@example.com", cap[0], "WRONG_CODE");

        BaseException ex = assertThrows(BaseException.class,
                () -> emailController.sendCode(dto));
        assertEquals("图形验证码错误或已失效", ex.getMessage(),
                "错误验证码应抛 CAPTCHA_INVALID 提示");

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("场景3：图形验证码正确 → 通过，调用 sendEmailCode 并返回 devCode")
    void sendCode_shouldPassWhenCaptchaCorrect() {
        String[] cap = generateCaptcha();
        // 用 Redis 中实际存的 code（已转大写）
        EmailController.EmailSendDTO dto = buildDto("user@example.com", cap[0], cap[1]);

        var result = emailController.sendCode(dto);

        assertNotNull(result);
        assertNotNull(result.getData());
        // 开发模式下应返回 devCode
        assertEquals("123456", result.getData().get("devCode"),
                "开发模式（mailEnabled=false）应返回 devCode");
        // NotificationService 应被调用一次
        verify(notificationService, times(1)).sendEmailCode("user@example.com", "LOGIN");
    }

    @Test
    @DisplayName("场景3b：图形验证码大小写不敏感也能通过")
    void sendCode_shouldBeCaseInsensitive() {
        String[] cap = generateCaptcha();
        // 用小写形式提交（Redis 中存的是大写）
        EmailController.EmailSendDTO dto = buildDto(
                "user@example.com", cap[0], cap[1].toLowerCase());

        var result = emailController.sendCode(dto);
        assertNotNull(result);
        verify(notificationService, times(1)).sendEmailCode("user@example.com", "LOGIN");
    }

    @Test
    @DisplayName("场景4：图形验证码 2 分钟后过期（模拟 TTL）→ 拒绝")
    void sendCode_shouldRejectAfterTtlExpires() {
        String[] cap = generateCaptcha();
        // 模拟 Redis TTL 过期：删 key
        redisStore.remove("captcha:" + cap[0]);

        EmailController.EmailSendDTO dto = buildDto("user@example.com", cap[0], cap[1]);

        BaseException ex = assertThrows(BaseException.class,
                () -> emailController.sendCode(dto));
        assertEquals("图形验证码错误或已失效", ex.getMessage(),
                "TTL 过期后应抛 CAPTCHA_INVALID");

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("场景5：一次性使用 — 正确校验后再次用相同 captchaId 校验失败")
    void sendCode_captchaShouldBeOneTimeUse() {
        String[] cap = generateCaptcha();
        EmailController.EmailSendDTO dto1 = buildDto("user@example.com", cap[0], cap[1]);

        // 第一次：校验通过
        var result1 = emailController.sendCode(dto1);
        assertNotNull(result1);
        verify(notificationService, times(1)).sendEmailCode("user@example.com", "LOGIN");

        // 第二次：用相同 captchaId 应失败（已被删除）
        EmailController.EmailSendDTO dto2 = buildDto("user@example.com", cap[0], cap[1]);
        BaseException ex = assertThrows(BaseException.class,
                () -> emailController.sendCode(dto2));
        assertEquals("图形验证码错误或已失效", ex.getMessage(),
                "一次性使用：相同 captchaId 第二次校验应失败");

        // NotificationService 只被调用一次（第一次成功时）
        verify(notificationService, times(1)).sendEmailCode("user@example.com", "LOGIN");
    }

    @Test
    @DisplayName("场景6：captchaId 不存在（伪造的 ID）→ 拒绝")
    void sendCode_shouldRejectWhenCaptchaIdNotExists() {
        EmailController.EmailSendDTO dto = buildDto(
                "user@example.com", "fake-non-existent-id", "ANY");

        BaseException ex = assertThrows(BaseException.class,
                () -> emailController.sendCode(dto));
        assertEquals("图形验证码错误或已失效", ex.getMessage());

        verifyNoInteractions(notificationService);
    }
}
