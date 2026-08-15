package com.stellar.controller.user;

import com.stellar.annotation.Idempotent;
import com.stellar.context.BaseContext;
import com.stellar.dto.PointsRedeemDTO;
import com.stellar.result.PageResult;
import com.stellar.result.Result;
import com.stellar.service.PointsService;
import com.stellar.vo.CheckinVO;
import com.stellar.vo.PointsProductVO;
import com.stellar.vo.PointsRedeemVO;
import com.stellar.vo.UserPointsVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * C 端：积分接口。
 */
@RestController
@RequestMapping("/user/points")
@RequiredArgsConstructor
@Api(tags = "C端：积分管理")
public class PointsController {

    private final PointsService pointsService;

    // ===== 积分查询 =====

    @GetMapping
    @ApiOperation("获取我的积分")
    public Result<UserPointsVO> getPoints() {
        Long userId = BaseContext.getCurrentId();
        return Result.success(pointsService.getOrCreateUserPoints(userId));
    }

    @GetMapping("/records")
    @ApiOperation("积分流水")
    public Result<PageResult> records(@RequestParam(defaultValue = "1") Integer page,
                                      @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(pointsService.pageRecords(userId, page, pageSize));
    }

    @GetMapping("/redemptions")
    @ApiOperation("兑换记录")
    public Result<PageResult> redemptions(@RequestParam(defaultValue = "1") Integer page,
                                          @RequestParam(defaultValue = "20") Integer pageSize) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(pointsService.pageRedemptions(userId, page, pageSize));
    }

    // ===== 签到 =====

    @Idempotent(keyPrefix = "points-checkin", windowSeconds = 300)
    @PostMapping("/checkin")
    @ApiOperation("每日签到")
    public Result<CheckinVO> checkin() {
        Long userId = BaseContext.getCurrentId();
        return Result.success(pointsService.checkin(userId));
    }

    @GetMapping("/checkin-dates")
    @ApiOperation("本月签到日期列表")
    public Result<List<String>> checkinDates() {
        Long userId = BaseContext.getCurrentId();
        return Result.success(pointsService.getCheckinDates(userId));
    }

    // ===== 积分商城 =====

    @GetMapping("/products")
    @ApiOperation("积分商城商品列表")
    public Result<List<PointsProductVO>> listProducts() {
        return Result.success(pointsService.listProducts());
    }

    @Idempotent(keyPrefix = "points-redeem", windowSeconds = 300)
    @PostMapping("/redeem")
    @ApiOperation("积分兑换")
    public Result<PointsRedeemVO> redeem(@RequestBody @Valid PointsRedeemDTO dto) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(pointsService.redeem(userId, dto));
    }
}
