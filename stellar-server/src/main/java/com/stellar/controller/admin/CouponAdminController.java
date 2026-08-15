package com.stellar.controller.admin;

import com.stellar.annotation.Idempotent;
import com.stellar.entity.Coupon;
import com.stellar.result.PageResult;
import com.stellar.result.Result;
import com.stellar.service.CouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端：优惠券管理。
 * 提供优惠券的创建、更新、删除和分页查询功能。
 */
@RestController
@RequestMapping("/admin/coupon")
@RequiredArgsConstructor
@Api(tags = "管理端：优惠券管理")
public class CouponAdminController {

    private final CouponService couponService;

    @Idempotent(keyPrefix = "admin-coupon-create", windowSeconds = 300)
    @PostMapping
    @ApiOperation("创建优惠券")
    public Result<Long> create(@RequestBody Coupon coupon) {
        return Result.success(couponService.create(coupon));
    }

    @Idempotent(keyPrefix = "admin-coupon-update", windowSeconds = 300)
    @PutMapping
    @ApiOperation("更新优惠券")
    public Result<String> update(@RequestBody Coupon coupon) {
        couponService.update(coupon);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除优惠券")
    public Result<String> delete(@PathVariable Long id) {
        couponService.delete(id);
        return Result.success();
    }

    @GetMapping("/page")
    @ApiOperation("优惠券分页")
    public Result<PageResult> page(@RequestParam(required = false) String name,
                                   @RequestParam(required = false) Integer status,
                                   @RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(couponService.pageCoupon(name, status, page, pageSize));
    }
}