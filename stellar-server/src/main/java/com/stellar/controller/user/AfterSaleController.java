package com.stellar.controller.user;

import com.stellar.annotation.Idempotent;
import com.stellar.context.BaseContext;
import com.stellar.dto.AfterSaleReturnDTO;
import com.stellar.dto.AfterSaleSubmitDTO;
import com.stellar.entity.AfterSale;
import com.stellar.result.PageResult;
import com.stellar.result.Result;
import com.stellar.service.AfterSaleService;
import com.stellar.vo.AfterSaleVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * C 端售后接口。
 */
@RestController
@RequestMapping("/user/aftersale")
@RequiredArgsConstructor
@Api(tags = "C端：售后管理")
public class AfterSaleController {

    private final AfterSaleService afterSaleService;

    @Idempotent(keyPrefix = "aftersale-submit", windowSeconds = 300)
    @PostMapping
    @ApiOperation("提交售后申请")
    public Result<AfterSale> submit(@RequestBody AfterSaleSubmitDTO dto) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(afterSaleService.submit(userId, dto));
    }

    @PostMapping("/{id}/cancel")
    @ApiOperation("取消售后申请")
    public Result<Void> cancel(@PathVariable Long id) {
        Long userId = BaseContext.getCurrentId();
        afterSaleService.cancel(id, userId);
        return Result.success();
    }

    @PutMapping("/return-tracking")
    @ApiOperation("提交退货物流单号")
    public Result<Void> submitReturnTracking(@RequestBody AfterSaleReturnDTO dto) {
        Long userId = BaseContext.getCurrentId();
        afterSaleService.submitReturnTracking(userId, dto);
        return Result.success();
    }

    @GetMapping
    @ApiOperation("售后列表")
    public Result<PageResult> list(@RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(afterSaleService.pageByUser(userId, page, pageSize));
    }

    @GetMapping("/{id}")
    @ApiOperation("售后详情")
    public Result<AfterSaleVO> detail(@PathVariable Long id) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(afterSaleService.getDetail(id, userId));
    }

    @GetMapping("/by-order/{orderId}")
    @ApiOperation("根据订单ID查询售后单（用于订单页展示售后状态）")
    public Result<AfterSaleVO> getByOrderId(@PathVariable Long orderId) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(afterSaleService.getByOrderId(orderId, userId));
    }
}
