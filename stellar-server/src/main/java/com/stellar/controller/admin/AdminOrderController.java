package com.stellar.controller.admin;

import com.stellar.annotation.Idempotent;
import com.stellar.annotation.RequireRole;
import com.stellar.result.PageResult;
import com.stellar.result.Result;
import com.stellar.service.OrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理端订单 Controller：/admin/order。
 *   - GET  /admin/order/page → 分页查询所有订单
 *   - POST /admin/order/{id}/ship → 发货（PAID → SHIPPED）
 */
@Slf4j
@RestController
@RequestMapping("/admin/order")
@RequiredArgsConstructor
@Api(tags = "管理端：订单管理")
public class AdminOrderController {

    private final OrderService orderService;

@RequireRole({1, 2})
    @GetMapping("/page")
    @ApiOperation("分页查询所有订单（支持按状态、订单号和日期范围筛选）")
    public Result<PageResult> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return Result.success(orderService.pageOrders(page, pageSize, status, orderNo, startTime, endTime));
    }

    @Idempotent(keyPrefix = "admin-order-ship", windowSeconds = 300)
@RequireRole({1, 2})
    @PostMapping("/{id}/ship")
    @ApiOperation("发货：PAID → SHIPPED，可传入物流信息并自动通知用户")
    public Result<String> ship(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String trackingNo = body != null ? body.get("trackingNo") : null;
        String deliveryCompany = body != null ? body.get("deliveryCompany") : null;
        orderService.ship(id, trackingNo, deliveryCompany);
        return Result.success();
    }

@RequireRole({1, 2})
    @DeleteMapping("/{id}")
    @ApiOperation("删除订单（仅 已完成/已取消 可删除）")
    public Result<String> delete(@PathVariable Long id) {
        orderService.deleteOrder(id);
        return Result.success();
    }
}