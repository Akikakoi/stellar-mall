package com.stellar.controller.user;

import com.stellar.annotation.RateLimit;
import com.stellar.annotation.Idempotent;
import com.stellar.context.BaseContext;
import com.stellar.dto.OrderSubmitDTO;
import com.stellar.entity.MallOrder;
import com.stellar.result.Result;
import com.stellar.service.OrderService;
import com.stellar.vo.MallOrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C 端订单 Controller：/user/order。
 *   - POST /user/order/submit → 提交订单（读 checked 购物车 → 扣库存 → 写订单 → 清购物车）
 *   - POST /user/order/{id}/pay → 模拟支付 PENDING → PAID
 *   - POST /user/order/{id}/cancel → 取消：逐条 rollback 库存 + 改状态 CANCELLED
 *   - GET  /user/order/{id} → 详情
 *   - GET  /user/order/list → 我的订单列表
 */
@Slf4j
@RestController
@RequestMapping("/user/order")
@RequiredArgsConstructor
@Api(tags = "C端：订单")
public class OrderController {

    private final OrderService orderService;

    @Idempotent(keyPrefix = "order", windowSeconds = 300)
    @RateLimit(key = "order-submit", maxRequests = 10, windowSeconds = 60)
    @PostMapping("/submit")
    @ApiOperation("提交订单：读购物车 checked 项或前端直传商品，乐观锁扣库存，写主单+明细，清已下单购物车")
    public Result<Map<String, Object>> submit(@RequestBody(required = false) OrderSubmitDTO dto) {
        Long userId = BaseContext.getCurrentId();
        log.info("[OrderController] submit order, userId={}, dto={}, items={}", 
                userId, dto, dto != null ? dto.getItems() : null);
        if (dto == null) dto = new OrderSubmitDTO();
        MallOrder order;
        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            log.info("[OrderController] using submitDirect, items size={}", dto.getItems().size());
            order = orderService.submitDirect(userId, dto);
        } else {
            log.info("[OrderController] using submit from cart");
            order = orderService.submit(userId, dto);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", order.getId());
        data.put("orderNo", order.getOrderNo());
        data.put("totalAmount", order.getTotalAmount());
        data.put("payAmount", order.getPayAmount());
        data.put("status", order.getStatus());
        data.put("pointsDeducted", order.getPointsDeducted());
        data.put("pointsAmount", order.getPointsAmount());
        return Result.success(data);
    }

    @Idempotent(keyPrefix = "order-pay", windowSeconds = 300)
    @PostMapping("/{id}/pay")
    @ApiOperation("模拟支付：状态 PENDING → PAID。payMethod: 1微信 2支付宝 4钱包")
    public Result<String> pay(@PathVariable Long id,
                              @RequestBody(required = false) java.util.Map<String, Object> body) {
        Integer payMethod = null;
        if (body != null && body.get("payMethod") != null) {
            payMethod = Integer.valueOf(body.get("payMethod").toString());
        }
        orderService.pay(id, BaseContext.getCurrentId(), payMethod);
        return Result.success();
    }

    @Idempotent(keyPrefix = "order-cancel", windowSeconds = 300)
    @PostMapping("/{id}/cancel")
    @ApiOperation("取消订单：逐条回滚库存 + 改状态 CANCELLED")
    public Result<String> cancel(@PathVariable Long id,
                                 @RequestBody(required = false) java.util.Map<String, Object> body) {
        // reason 参数暂仅预留用于后续审计日志；当前取消逻辑无需区分原因
        orderService.cancel(id, BaseContext.getCurrentId());
        return Result.success();
    }

    @GetMapping("/{id}")
    @ApiOperation("订单详情（含明细）")
    public Result<MallOrderVO> detail(@PathVariable Long id) {
        return Result.success(orderService.getDetail(id, BaseContext.getCurrentId()));
    }

    @GetMapping("/list")
    @ApiOperation("我的订单列表（按 id 倒序，可选按前端数字 status 过滤：1待付款/3待收货/5已完成/0已取消）")
    public Result<List<MallOrderVO>> list(
            @RequestParam(required = false) Integer status) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(orderService.listByUser(userId, status));
    }

    @Idempotent(keyPrefix = "order-confirm", windowSeconds = 300)
    @PostMapping("/{id}/confirm")
    @ApiOperation("确认收货：状态 SHIPPED → COMPLETED")
    public Result<String> confirm(@PathVariable Long id) {
        orderService.confirm(id, BaseContext.getCurrentId());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除订单（仅 已完成/已取消 可删除）")
    public Result<String> delete(@PathVariable Long id) {
        orderService.deleteOrder(id, BaseContext.getCurrentId());
        return Result.success();
    }
}
