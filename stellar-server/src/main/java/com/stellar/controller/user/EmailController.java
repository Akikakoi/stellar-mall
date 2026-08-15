package com.stellar.controller.user;

import com.stellar.annotation.RateLimit;
import com.stellar.entity.EmailCode;
import com.stellar.result.Result;
import com.stellar.service.NotificationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;

/**
 * C 端：邮箱验证码接口（无需登录）
 * <p>
 * 发送验证码无需图形验证码；图形验证码在登录/注册提交时校验。
 */
@RestController
@RequestMapping("/user/email-code")
@RequiredArgsConstructor
@Api(tags = "C端：邮箱验证码")
public class EmailController {

    private final NotificationService notificationService;

    /** 是否启用真实 SMTP 发送；false 时（开发模式）接口直接返回验证码 */
    @Value("${stellar.mail.enabled:false}")
    private boolean mailEnabled;

    @RateLimit(key = "send-code", maxRequests = 5, windowSeconds = 60)
    @PostMapping("/send")
    @ApiOperation("发送邮箱验证码（直接发送，图形验证码在登录时校验）")
    public Result<Map<String, Object>> sendCode(@RequestBody @Valid EmailSendDTO dto) {
        EmailCode emailCode = notificationService.sendEmailCode(dto.getEmail(), dto.getType());
        Map<String, Object> data = new HashMap<>();
        data.put("sent", mailEnabled);
        if (!mailEnabled) {
            // 开发模式：未配置 SMTP，直接把验证码返回给前端展示
            data.put("devCode", emailCode.getCode());
        }
        return Result.success(data);
    }

    @PostMapping("/verify")
    @ApiOperation("校验邮箱验证码")
    public Result<Map<String, Boolean>> verifyCode(@RequestBody @Valid EmailVerifyDTO dto) {
        boolean ok = notificationService.verifyEmailCode(dto.getEmail(), dto.getType(), dto.getCode());
        Map<String, Boolean> data = new HashMap<>();
        data.put("valid", ok);
        return Result.success(data);
    }

    @Data
    public static class EmailSendDTO {
        @NotBlank @Email(message = "邮箱格式不正确")
        private String email;
        @NotBlank
        private String type; // LOGIN / REGISTER
    }

    @Data
    public static class EmailVerifyDTO {
        @NotBlank @Email(message = "邮箱格式不正确")
        private String email;
        @NotBlank
        private String type;
        @NotBlank
        private String code;
    }
}