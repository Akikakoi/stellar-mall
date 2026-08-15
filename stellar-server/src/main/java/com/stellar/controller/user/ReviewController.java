package com.stellar.controller.user;

import com.stellar.annotation.Idempotent;
import com.stellar.entity.Review;
import com.stellar.result.PageResult;
import com.stellar.result.Result;
import com.stellar.service.ReviewService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/user/review")
@RequiredArgsConstructor
@Api(tags = "C端：商品评价")
public class ReviewController {

    private final ReviewService reviewService;

    @Idempotent(keyPrefix = "review-submit", windowSeconds = 300)
    @PostMapping
    @ApiOperation("提交评价")
    public Result<Long> submit(@RequestBody Review review) {
        return Result.success(reviewService.submit(review));
    }

    @GetMapping("/spu/{spuId}")
    @ApiOperation("SPU评价列表")
    public Result<PageResult> listBySpu(@PathVariable Long spuId,
                                        @RequestParam(defaultValue = "1") Integer page,
                                        @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(reviewService.pageBySpuId(spuId, page, pageSize));
    }

    @GetMapping("/spu/{spuId}/rating")
    @ApiOperation("SPU平均评分")
    public Result<BigDecimal> avgRating(@PathVariable Long spuId) {
        return Result.success(reviewService.avgRating(spuId));
    }
}