package com.stellar.controller.user;

import com.stellar.annotation.RateLimit;
import com.stellar.context.BaseContext;
import com.stellar.dto.MallUserLoginDTO;
import com.stellar.dto.MallUserProfileUpdateDTO;
import com.stellar.entity.MallUser;
import com.stellar.exception.BaseException;
import com.stellar.result.Result;
import com.stellar.service.MallUserService;
import com.stellar.service.NotificationService;
import com.stellar.service.TokenBlacklistService;
import com.stellar.vo.MallUserLoginVO;
import com.stellar.vo.MallUserVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/user/user")
@RequiredArgsConstructor
@Api(tags = "C端：用户")
public class UserController {

    private final MallUserService mallUserService;
    private final NotificationService notificationService;
    private final TokenBlacklistService tokenBlacklistService;

    @RateLimit(key = "login", maxRequests = 10, windowSeconds = 60)
    @PostMapping("/login")
    @ApiOperation("C 端用户登录：邮箱 + 密码。首次登录自动注册")
    public Result<MallUserLoginVO> login(@RequestBody MallUserLoginDTO dto) {
        return Result.success(mallUserService.login(dto));
    }

    @RateLimit(key = "email-login", maxRequests = 10, windowSeconds = 60)
    @PostMapping("/email-login")
    @ApiOperation("C 端用户邮箱验证码登录/注册")
    public Result<MallUserLoginVO> emailLogin(@RequestBody @Valid EmailLoginDTO dto) {
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
                .phone(u.getPhone())
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

    // ======================== DTO ========================

    @Data
    public static class EmailLoginDTO {
        @NotBlank @Email(message = "邮箱格式不正确")
        private String email;
        @NotBlank
        private String type;    // LOGIN / REGISTER
        @NotBlank
        private String code;
    }
}