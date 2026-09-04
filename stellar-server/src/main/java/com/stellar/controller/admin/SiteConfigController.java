package com.stellar.controller.admin;

import com.stellar.annotation.RequireRole;
import com.stellar.dto.SiteBgDTO;
import com.stellar.result.Result;
import com.stellar.service.SiteConfigService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端：站点级配置（当前为商城主页背景图）。
 */
@RestController
@RequestMapping("/admin/site-config")
@RequiredArgsConstructor
@Api(tags = "管理端：站点级配置")
public class SiteConfigController {

    private final SiteConfigService siteConfigService;

    @RequireRole({1, 2})
    @GetMapping("/bg")
    @ApiOperation("获取商城主页背景配置（bgImage 空串 = 使用默认背景）")
    public Result<Map<String, String>> getBg() {
        return Result.success(Map.of("bgImage", siteConfigService.getBgImage()));
    }

    @RequireRole({1, 2})
    @PutMapping("/bg")
    @ApiOperation("保存商城主页背景（bgImage null/空 = 恢复默认）")
    public Result<String> saveBg(@RequestBody SiteBgDTO dto) {
        siteConfigService.saveBgImage(dto != null ? dto.getBgImage() : null);
        return Result.success();
    }
}
