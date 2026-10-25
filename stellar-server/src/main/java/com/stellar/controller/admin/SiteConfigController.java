package com.stellar.controller.admin;

import com.stellar.annotation.RequireRole;
import com.stellar.dto.SiteBgDTO;
import com.stellar.dto.SiteOrbitDTO;
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
 * 管理端：站点级配置（商城主页背景图、首屏环绕商品）。
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

    @RequireRole({1, 2})
    @GetMapping("/orbit")
    @ApiOperation("获取首屏环绕商品候选池（空列表 = 前台从商品库随机抽取）")
    public Result<Map<String, Object>> getOrbit() {
        return Result.success(Map.of("spuIds", siteConfigService.getHomeOrbitSpuIds()));
    }

    @RequireRole({1, 2})
    @PutMapping("/orbit")
    @ApiOperation("保存首屏环绕商品候选池（空列表 = 前台从商品库随机抽取）")
    public Result<String> saveOrbit(@RequestBody SiteOrbitDTO dto) {
        siteConfigService.saveHomeOrbitSpuIds(dto != null ? dto.getSpuIds() : null);
        return Result.success();
    }
}
