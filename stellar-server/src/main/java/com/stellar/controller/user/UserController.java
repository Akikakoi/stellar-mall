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
import com.stellar.vo.MallUserLoginVO;
import com.stellar.vo.MallUserVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;

@RestController
@RequestMapping("/user/user")
@RequiredArgsConstructor
@Api(tags = "C端：用户")
public class UserController {

    private final MallUserService mallUserService;
    private final NotificationService notificationService;

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