package com.stellar.auth;

import com.stellar.controller.user.EmailController;
import com.stellar.controller.user.UserController;
import com.stellar.entity.EmailCode;
import com.stellar.exception.BaseException;
import com.stellar.service.CaptchaService;
import com.stellar.service.MallUserService;
import com.stellar.service.NotificationService;
import com.stellar.service.TokenBlacklistService;
import com.stellar.service.impl.CaptchaServiceImpl;
import com.stellar.vo.MallUserLoginVO;
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
 * 图形验证码端到端集成测试。
 * <p>
 * 验证流程：发送验证码无需图形验证码；图形验证码在登录/注册提交时校验。
 * <p>
 * 覆盖场景：
 *   1. sendCode 无需图形验证码即可发送
 *   2. emailLogin 缺图形验证码 → 拒绝
 *   3. emailLogin 图形验证码错误 → 拒绝
 *   4. emailLogin 图形验证码正确 → 通过
 *   5. emailLogin 大小写不敏感
 *   6. emailLogin TTL 过期 → 拒绝
 *   7. emailLogin 一次性使用
 *   8. emailLogin captchaId 不存在 → 拒绝
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("图形验证码端到端流程")
class CaptchaTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private NotificationService notificationService;
    @Mock private MallUserService mallUserService;
    @Mock private TokenBlacklistService tokenBlacklistService;

    /** 真实的 CaptchaService（不 mock，验证端到端协作） */
    private CaptchaService captchaService;

    /** 被测对象 */
    private EmailController emailController;
    private UserController userController;

    /** 模拟 Redis 存储：key → value */
    private final Map<String, String> redisStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        captchaService = new CaptchaServiceImpl(stringRedisTemplate);
        emailController = new EmailController(notificationService);
        ReflectionTestUtils.setField(emailController, "mailEnabled", false);

        userController = new UserController(mallUserService, notificationService,
                tokenBlacklistService, captchaService);

        redisStore.clear();

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);

        lenient().doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOps).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        lenient().when(valueOps.get(anyString())).thenAnswer(invocation ->
                redisStore.get(invocation.getArgument(0)));

        lenient().when(stringRedisTemplate.delete(anyString())).thenAnswer(invocation ->
                redisStore.remove(invocation.getArgument(0)) != null);

        lenient().when(notificationService.sendEmailCode(anyString(), anyString()))
                .thenAnswer(invocation -> EmailCode.builder()
                        .email(invocation.getArgument(0))
                        .code("123456")
                        .type(invocation.getArgument(1))
                        .used(0)
                        .expireTime(LocalDateTime.now().plusMinutes(5))
                        .createTime(LocalDateTime.now())
                        .build());

        lenient().when(notificationService.verifyEmailCode(anyString(), eq("LOGIN"), eq("123456")))
                .thenReturn(true);

        lenient().when(mallUserService.loginOrRegisterByEmail(anyString()))
                .thenReturn(MallUserLoginVO.builder()
                        .token("fake-token")
                        .refreshToken("fake-refresh")
                        .userId(1L)
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
    private EmailController.EmailSendDTO buildSendDto(String email) {
        EmailController.EmailSendDTO dto = new EmailController.EmailSendDTO();
        dto.setEmail(email);
        dto.setType("LOGIN");
        return dto;
    }

    /**
     * 构造 EmailLoginDTO。
     */
    private UserController.EmailLoginDTO buildLoginDto(String email, String captchaId, String captchaCode) {
        UserController.EmailLoginDTO dto = new UserController.EmailLoginDTO();
        dto.setEmail(email);
        dto.setType("LOGIN");
        dto.setCode("123456");
        dto.setCaptchaId(captchaId);
        dto.setCaptchaCode(captchaCode);
        return dto;
    }

    // ======================== sendCode：无需图形验证码 ========================

    @Test
    @DisplayName("sendCode：无需图形验证码即可发送")
    void sendCode_shouldWorkWithoutCaptcha() {
        EmailController.EmailSendDTO dto = buildSendDto("user@example.com");

        var result = emailController.sendCode(dto);

        assertNotNull(result);
        assertNotNull(result.getData());
        assertEquals("123456", result.getData().get("devCode"),
                "开发模式（mailEnabled=false）应返回 devCode");
        verify(notificationService, times(1)).sendEmailCode("user@example.com", "LOGIN");
    }

    // ======================== emailLogin：图形验证码校验 ========================

    @Test
    @DisplayName("emailLogin：缺图形验证码 → 抛 CAPTCHA_REQUIRED")
    void emailLogin_shouldRejectWhenCaptchaMissing() {
        UserController.EmailLoginDTO dto = buildLoginDto("user@example.com", null, null);

        BaseException ex = assertThrows(BaseException.class,
                () -> userController.emailLogin(dto));
        assertEquals("请先完成图形验证码校验", ex.getMessage());

        verifyNoInteractions(notificationService);
        verifyNoInteractions(mallUserService);
    }

    @Test
    @DisplayName("emailLogin：图形验证码错误 → 抛 CAPTCHA_INVALID")
    void emailLogin_shouldRejectWhenCaptchaCodeWrong() {
        String[] cap = generateCaptcha();
        UserController.EmailLoginDTO dto = buildLoginDto("user@example.com", cap[0], "WRONG_CODE");

        BaseException ex = assertThrows(BaseException.class,
                () -> userController.emailLogin(dto));
        assertEquals("图形验证码错误或已失效", ex.getMessage());

        verifyNoInteractions(notificationService);
        verifyNoInteractions(mallUserService);
    }

    @Test
    @DisplayName("emailLogin：图形验证码正确 → 通过，校验邮箱验证码并登录")
    void emailLogin_shouldPassWhenCaptchaCorrect() {
        String[] cap = generateCaptcha();
        UserController.EmailLoginDTO dto = buildLoginDto("user@example.com", cap[0], cap[1]);

        var result = userController.emailLogin(dto);

        assertNotNull(result);
        assertNotNull(result.getData());
        assertEquals("fake-token", result.getData().getToken());
        verify(notificationService, times(1)).verifyEmailCode("user@example.com", "LOGIN", "123456");
        verify(mallUserService, times(1)).loginOrRegisterByEmail("user@example.com");
    }

    @Test
    @DisplayName("emailLogin：图形验证码大小写不敏感")
    void emailLogin_shouldBeCaseInsensitive() {
        String[] cap = generateCaptcha();
        UserController.EmailLoginDTO dto = buildLoginDto(
                "user@example.com", cap[0], cap[1].toLowerCase());

        var result = userController.emailLogin(dto);
        assertNotNull(result);
        verify(mallUserService, times(1)).loginOrRegisterByEmail("user@example.com");
    }

    @Test
    @DisplayName("emailLogin：图形验证码 TTL 过期 → 拒绝")
    void emailLogin_shouldRejectAfterTtlExpires() {
        String[] cap = generateCaptcha();
        redisStore.remove("captcha:" + cap[0]);

        UserController.EmailLoginDTO dto = buildLoginDto("user@example.com", cap[0], cap[1]);

        BaseException ex = assertThrows(BaseException.class,
                () -> userController.emailLogin(dto));
        assertEquals("图形验证码错误或已失效", ex.getMessage());

        verifyNoInteractions(notificationService);
        verifyNoInteractions(mallUserService);
    }

    @Test
    @DisplayName("emailLogin：一次性使用 — 正确校验后再次用相同 captchaId 校验失败")
    void emailLogin_captchaShouldBeOneTimeUse() {
        String[] cap = generateCaptcha();
        UserController.EmailLoginDTO dto1 = buildLoginDto("user@example.com", cap[0], cap[1]);

        // 第一次：校验通过
        var result1 = userController.emailLogin(dto1);
        assertNotNull(result1);
        verify(mallUserService, times(1)).loginOrRegisterByEmail("user@example.com");

        // 第二次：用相同 captchaId 应失败（已被删除）
        UserController.EmailLoginDTO dto2 = buildLoginDto("user@example.com", cap[0], cap[1]);
        BaseException ex = assertThrows(BaseException.class,
                () -> userController.emailLogin(dto2));
        assertEquals("图形验证码错误或已失效", ex.getMessage());

        // mallUserService 只被调用一次（第一次成功时）
        verify(mallUserService, times(1)).loginOrRegisterByEmail("user@example.com");
    }

    @Test
    @DisplayName("emailLogin：captchaId 不存在（伪造的 ID）→ 拒绝")
    void emailLogin_shouldRejectWhenCaptchaIdNotExists() {
        UserController.EmailLoginDTO dto = buildLoginDto(
                "user@example.com", "fake-non-existent-id", "ANY");

        BaseException ex = assertThrows(BaseException.class,
                () -> userController.emailLogin(dto));
        assertEquals("图形验证码错误或已失效", ex.getMessage());

        verifyNoInteractions(notificationService);
        verifyNoInteractions(mallUserService);
    }
}
