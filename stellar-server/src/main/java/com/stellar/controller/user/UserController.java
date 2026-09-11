package com.stellar.controller.user;

import com.stellar.annotation.RateLimit;
import com.stellar.constant.MessageConstant;
import com.stellar.context.BaseContext;
import com.stellar.dto.MallUserLoginDTO;
import com.stellar.dto.MallUserPasswordUpdateDTO;
import com.stellar.dto.MallUserProfileUpdateDTO;
import com.stellar.entity.MallUser;
import com.stellar.exception.BaseException;
import com.stellar.result.Result;
import com.stellar.service.CaptchaService;
import com.stellar.service.MallUserService;
import com.stellar.service.NotificationService;
import com.stellar.service.TokenBlacklistService;
import com.stellar.vo.MallUserLoginVO;
import com.stellar.vo.MallUserVO;
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

@RestController
@RequestMapping("/user/user")
@RequiredArgsConstructor
@Api(tags = "C端：用户")
public class UserController {

    private final MallUserService mallUserService;
    private final NotificationService notificationService;
    private final TokenBlacklistService tokenBlacklistService;
    private final CaptchaService captchaService;

    @RateLimit(key = "login", maxRequests = 10, windowSeconds = 60)
    @PostMapping("/login")
    @ApiOperation("C 端用户登录：邮箱 + 密码。首次登录自动注册")
    public Result<MallUserLoginVO> login(@RequestBody MallUserLoginDTO dto) {
        return Result.success(mallUserService.login(dto));
    }

    @RateLimit(key = "email-login", maxRequests = 10, windowSeconds = 60)
    @PostMapping("/email-login")
    @ApiOperation("C 端用户邮箱验证码登录/注册（需图形验证码）")
    public Result<MallUserLoginVO> emailLogin(@RequestBody @Valid EmailLoginDTO dto) {
        // 图形验证码校验
        if (dto.getCaptchaId() == null || dto.getCaptchaCode() == null) {
            throw new BaseException(MessageConstant.CAPTCHA_REQUIRED);
        }
        if (!captchaService.validate(dto.getCaptchaId(), dto.getCaptchaCode())) {
            throw new BaseException(MessageConstant.CAPTCHA_INVALID);
        }
        // 邮箱验证码校验
        boolean ok = notificationService.verifyEmailCode(dto.getEmail(), dto.getType(), dto.getCode());
        if (!ok) {
            throw new BaseException("验证码错误或已过期");
        }
        return Result.success(mallUserService.loginOrRegisterByEmail(dto.getEmail()));
    }

    @PostMapping("/refresh")
    @ApiOperation("用 refresh token 换新的 access + refresh token")
    public Result<MallUserLoginVO> refresh(@RequestBody RefreshRequest req) {
        return Result.success(mallUserService.refresh(req.getRefreshToken()));
    }

    @PostMapping("/logout")
    @ApiOperation("C 端用户登出（E4：access+refresh 写黑名单）")
    public Result<Void> logout(@RequestHeader(value = "authentication", required = false) String tokenHeader,
                               @RequestHeader(value = "Authorization", required = false) String authHeader,
                               @RequestBody(required = false) RefreshRequest req) {
        // E4: access token 写黑名单（从 authentication 或 Authorization header 提取）
        String accessToken = extractToken(tokenHeader, authHeader);
        if (accessToken != null) {
            tokenBlacklistService.blacklist(accessToken);
        }
        // E4: refresh token 写黑名单
        if (req != null && req.getRefreshToken() != null && !req.getRefreshToken().isEmpty()) {
            tokenBlacklistService.blacklist(req.getRefreshToken());
        }
        return Result.success();
    }

    /**
     * 从多种 header 形式中提取 JWT。
     */
    private String extractToken(String tokenHeader, String authHeader) {
        if (tokenHeader != null && !tokenHeader.isEmpty()) {
            return tokenHeader;
        }
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authHeader.substring(7);
        }
        return null;
    }

    @lombok.Data
    public static class RefreshRequest {
        private String refreshToken;
    }

    @GetMapping("/me")
    @ApiOperation("当前登录用户信息")
    public Result<MallUserVO> me() {
        Long uid = BaseContext.getCurrentId();
        MallUser u = mallUserService.getById(uid);
        if (u == null) return Result.success(null);
        return Result.success(MallUserVO.builder()
                .id(u.getId())
                .email(u.getEmail())
                .nickname(u.getNickname())
                .status(u.getStatus())
                .build());
    }

    @GetMapping("/profile")
    @ApiOperation("获取当前用户资料")
    public Result<MallUserVO> getProfile() {
        return Result.success(mallUserService.getProfile(BaseContext.getCurrentId()));
    }

    @PutMapping("/profile")
    @ApiOperation("更新当前用户资料")
    public Result<String> updateProfile(@RequestBody MallUserProfileUpdateDTO dto) {
        mallUserService.updateProfile(BaseContext.getCurrentId(), dto);
        return Result.success();
    }

    @PostMapping("/deactivate")
    @ApiOperation("注销当前账号")
    public Result<String> deactivate() {
        mallUserService.deactivateAccount(BaseContext.getCurrentId());
        return Result.success();
    }

    @Value("${stellar.mail.enabled:false}")
    private boolean mailEnabled;

    @RateLimit(key = "email-change-code", maxRequests = 5, windowSeconds = 60)
    @PostMapping("/email/change-code")
    @ApiOperation("发送换绑邮箱验证码到新邮箱（需登录，校验新邮箱未被注册）")
    public Result<Map<String, Object>> sendEmailChangeCode(@RequestBody @Valid EmailChangeCodeDTO dto) {
        com.stellar.entity.EmailCode emailCode =
                mallUserService.sendEmailChangeCode(BaseContext.getCurrentId(), dto.getEmail());
        Map<String, Object> data = new HashMap<>();
        data.put("sent", mailEnabled);
        if (!mailEnabled) {
            // 开发模式：未配置 SMTP，直接把验证码返回给前端展示
            data.put("devCode", emailCode.getCode());
        }
        return Result.success(data);
    }

    @RateLimit(key = "email-change", maxRequests = 10, windowSeconds = 60)
    @PostMapping("/email/change")
    @ApiOperation("校验验证码并更换登录邮箱（最终校验新邮箱未被占用）")
    public Result<String> changeEmail(@RequestBody @Valid EmailChangeDTO dto) {
        mallUserService.changeEmail(BaseContext.getCurrentId(), dto.getEmail(), dto.getCode());
        return Result.success();
    }

    @RateLimit(key = "password-change-code", maxRequests = 5, windowSeconds = 60)
    @PostMapping("/password/code")
    @ApiOperation("发送修改密码验证码到当前登录邮箱（供从未设置过密码的账号自助设置密码）")
    public Result<Map<String, Object>> sendPasswordChangeCode() {
        com.stellar.entity.EmailCode emailCode =
                mallUserService.sendPasswordChangeCode(BaseContext.getCurrentId());
        Map<String, Object> data = new HashMap<>();
        data.put("sent", mailEnabled);
        if (!mailEnabled) {
            // 开发模式：未配置 SMTP，直接把验证码返回给前端展示
            data.put("devCode", emailCode.getCode());
        }
        return Result.success(data);
    }

    @RateLimit(key = "password-change", maxRequests = 10, windowSeconds = 60)
    @PostMapping("/password")
    @ApiOperation("修改登录密码（原密码验证 / 邮箱验证码验证 二选一）")
    public Result<String> updatePassword(@RequestBody @Valid MallUserPasswordUpdateDTO dto) {
        mallUserService.updatePassword(BaseContext.getCurrentId(), dto);
        return Result.success();
    }

    // ======================== DTO ========================

    @Data
    public static class EmailLoginDTO {
        @NotBlank @Email(message = "邮箱格式不正确")
        private String email;
        @NotBlank
        private String type;    // LOGIN / REGISTER
        @NotBlank
        private String code;
        /** 图形验证码 ID（来自 /captcha/image 返回） */
        private String captchaId;
        /** 用户识别出的图形验证码 */
        private String captchaCode;
    }

    @Data
    public static class EmailChangeCodeDTO {
        @NotBlank @Email(message = "邮箱格式不正确")
        private String email;
    }

    @Data
    public static class EmailChangeDTO {
        @NotBlank @Email(message = "邮箱格式不正确")
        private String email;
        @NotBlank(message = "验证码不能为空")
        private String code;
    }
}