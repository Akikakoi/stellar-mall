package com.stellar.controller.user;

import com.stellar.result.Result;
import com.stellar.service.CaptchaService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 图形验证码 Controller（E3）。
 * <p>
 * 路径前缀 /captcha，公开接口（WebMvc 白名单放行），无需登录。
 * 前端流程：
 *   1. GET /captcha/image 拿 {captchaId, imageBase64}
 *   2. 展示图片让用户识别
 *   3. 调 send-code 时把 captchaId + captchaCode 一起传过来
 */
@Slf4j
@RestController
@RequestMapping("/captcha")
@RequiredArgsConstructor
@Api(tags = "图形验证码")
public class CaptchaController {

    private final CaptchaService captchaService;

    @GetMapping("/image")
    @ApiOperation("获取图形验证码图片（返回 captchaId + imageBase64，2 分钟有效）")
    public Result<Map<String, String>> image() {
        return Result.success(captchaService.generate());
    }

    @PostMapping("/image")
    @ApiOperation("获取图形验证码图片（POST 版本，便于前端无参调用）")
    public Result<Map<String, String>> imagePost() {
        return Result.success(captchaService.generate());
    }
}
