package com.stellar.controller.admin;

import com.stellar.result.PageResult;
import com.stellar.result.Result;
import com.stellar.service.ReviewService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/review")
@RequiredArgsConstructor
@Api(tags = "管理端：评价管理")
public class ReviewAdminController {

    private final ReviewService reviewService;

    @GetMapping("/page")
    @ApiOperation("评价分页，支持按 spuId / 商品名称 / status 筛选")
    public Result<PageResult> page(@RequestParam(required = false) Long spuId,
                                   @RequestParam(required = false) Integer status,
                                   @RequestParam(required = false) String spuName,
                                   @RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(reviewService.pageAll(spuId, status, spuName, page, pageSize));
    }

    @PostMapping("/{id}/reply")
    @ApiOperation("回复评价")
    public Result<String> reply(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        reviewService.reply(id, body.get("reply"));
        return Result.success();
    }

    @PostMapping("/{id}/hide")
    @ApiOperation("隐藏评价")
    public Result<String> hide(@PathVariable Long id) {
        reviewService.hide(id);
        return Result.success();
    }

    @PostMapping("/{id}/show")
    @ApiOperation("显示评价")
    public Result<String> show(@PathVariable Long id) {
        reviewService.show(id);
        return Result.success();
    }
}