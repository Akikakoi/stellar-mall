package com.stellar.controller.user;

import com.stellar.result.Result;
import com.stellar.service.NotificationService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.util.HashMap;
import java.util.Map;

/**
 * C 端：短信验证码接口（无需登录）
 */
@RestController
@RequestMapping("/user/sms")
@RequiredArgsConstructor
@Api(tags = "C端：短信验证码")
public class SmsController {

    private final NotificationService notificationService;

    @PostMapping("/send")
    @ApiOperation("发送短信验证码")
    public Result<String> sendCode(@RequestBody @Valid SmsSendDTO dto) {
        notificationService.sendSmsCode(dto.getPhone(), dto.getType());
        return Result.success("验证码已发送（开发环境请查看后端日志）");
    }

    @PostMapping("/verify")
    @ApiOperation("校验短信验证码")
    public Result<Map<String, Boolean>> verifyCode(@RequestBody @Valid SmsVerifyDTO dto) {
        boolean ok = notificationService.verifySmsCode(dto.getPhone(), dto.getType(), dto.getCode());
        Map<String, Boolean> data = new HashMap<>();
        data.put("valid", ok);
        return Result.success(data);
    }

    @Data
    public static class SmsSendDTO {
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String phone;
        @NotBlank
        private String type; // LOGIN / REGISTER
    }

    @Data
    public static class SmsVerifyDTO {
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String phone;
        @NotBlank
        private String type;
        @NotBlank
        private String code;
    }
}
