package com.stellar.controller.user;

import com.stellar.entity.HomeModule;
import com.stellar.result.Result;
import com.stellar.service.HomeModuleService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端：首页模块数据，不需要登录即可访问。
 */
@RestController
@RequestMapping("/user/home-module")
@RequiredArgsConstructor
@Api(tags = "C端：首页装修模块")
public class HomeModuleUserController {

    private final HomeModuleService homeModuleService;

    @GetMapping("/list")
    @ApiOperation("获取启用的首页模块列表")
    public Result<List<HomeModule>> listEnabled() {
        return Result.success(homeModuleService.listEnabled());
    }
}
