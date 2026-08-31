package com.stellar.controller.admin;

import com.stellar.annotation.Idempotent;
import com.stellar.annotation.RequireRole;
import com.stellar.entity.Banner;
import com.stellar.result.PageResult;
import com.stellar.result.Result;
import com.stellar.service.BannerService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端：首页轮播图管理。
 * 提供轮播图的新增、更新、删除和分页查询功能。
 */
@RestController
@RequestMapping("/admin/banner")
@RequiredArgsConstructor
@Api(tags = "管理端：首页轮播图")
public class BannerAdminController {

    private final BannerService bannerService;

    @Idempotent(keyPrefix = "admin-banner-create", windowSeconds = 300)
@RequireRole({1, 2})
    @PostMapping
    @ApiOperation("新增轮播图")
    public Result<Long> create(@RequestBody Banner banner) {
        return Result.success(bannerService.create(banner));
    }

@RequireRole({1, 2})
    @PutMapping
    @ApiOperation("更新轮播图")
    public Result<String> update(@RequestBody Banner banner) {
        bannerService.update(banner);
        return Result.success();
    }

@RequireRole({1, 2})
    @DeleteMapping("/{id}")
    @ApiOperation("删除轮播图")
    public Result<String> delete(@PathVariable Long id) {
        bannerService.delete(id);
        return Result.success();
    }

@RequireRole({1, 2})
    @GetMapping("/page")
    @ApiOperation("轮播图分页")
    public Result<PageResult> page(@RequestParam(required = false) String title,
                                   @RequestParam(required = false) Integer status,
                                   @RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(bannerService.page(title, status, page, pageSize));
    }
}