package com.stellar.controller.user;

import com.stellar.annotation.Idempotent;
import com.stellar.context.BaseContext;
import com.stellar.entity.Coupon;
import com.stellar.entity.UserCoupon;
import com.stellar.result.Result;
import com.stellar.service.CouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/coupon")
@RequiredArgsConstructor
@Api(tags = "C端：优惠券")
public class CouponUserController {

    private final CouponService couponService;

    @GetMapping("/available")
    @ApiOperation("可领取的优惠券列表")
    public Result<List<Coupon>> listAvailable() {
        return Result.success(couponService.listAvailable(BaseContext.getCurrentId()));
    }

    @Idempotent(keyPrefix = "coupon-claim", windowSeconds = 300)
    @PostMapping("/claim/{couponId}")
    @ApiOperation("领取优惠券")
    public Result<Long> claim(@PathVariable Long couponId) {
        return Result.success(couponService.claim(couponId, BaseContext.getCurrentId()));
    }

    @GetMapping("/my")
    @ApiOperation("我的优惠券")
    public Result<List<UserCoupon>> myCoupons(@RequestParam(required = false) Integer status) {
        return Result.success(couponService.listUserCoupons(BaseContext.getCurrentId(), status));
    }
}