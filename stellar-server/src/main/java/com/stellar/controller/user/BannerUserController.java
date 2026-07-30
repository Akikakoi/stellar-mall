package com.stellar.controller.user;

import com.stellar.entity.Banner;
import com.stellar.result.Result;
import com.stellar.service.BannerService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user/banner")
@RequiredArgsConstructor
@Api(tags = "C端：首页轮播图")
public class BannerUserController {

    private final BannerService bannerService;

    @GetMapping("/list")
    @ApiOperation("获取启用的轮播图列表")
    public Result<List<Banner>> list() {
        return Result.success(bannerService.listEnabled());
    }
}