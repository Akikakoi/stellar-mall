package com.stellar.controller.user;

import com.stellar.result.Result;
import com.stellar.service.SiteConfigService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * C 端：站点级配置（当前为商城主页背景图，匿名放行供前台启动时拉取）。
 */
@RestController
@RequestMapping("/user/site-config")
@RequiredArgsConstructor
@Api(tags = "C端：站点级配置")
public class SiteConfigUserController {

    private final SiteConfigService siteConfigService;

    @GetMapping("/bg")
    @ApiOperation("获取商城主页背景配置（bgImage 空串 = 使用默认背景）")
    public Result<Map<String, String>> getBg() {
        return Result.success(Map.of("bgImage", siteConfigService.getBgImage()));
    }
}
