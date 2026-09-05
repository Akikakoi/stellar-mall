package com.stellar.controller.admin;

import com.stellar.annotation.Idempotent;
import com.stellar.annotation.RequireRole;
import com.stellar.result.PageResult;
import com.stellar.result.Result;
import com.stellar.service.InventoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/inventory/outbound")
@RequiredArgsConstructor
@Api(tags = "管理端：出库管理")
public class OutboundController {

    private final InventoryService inventoryService;

    @Idempotent(keyPrefix = "admin-outbound", windowSeconds = 300)
    @RequireRole({1, 2})
    @PostMapping
    @ApiOperation("出库操作：支持销售出库 / 报废出库 / 盘亏出库，自动记录流水")
    public Result<String> outbound(@RequestBody Map<String, Object> body) {
        Long skuId = Long.valueOf(body.get("skuId").toString());
        int quantity = Integer.parseInt(body.get("quantity").toString());
        String businessType = body.get("businessType") != null ? body.get("businessType").toString() : "SALE_OUT";
        String businessNo = body.get("businessNo") != null ? body.get("businessNo").toString() : null;
        String remark = body.get("remark") != null ? body.get("remark").toString() : null;
        inventoryService.outbound(skuId, quantity, businessType, businessNo, remark);
        return Result.success();
    }

    @RequireRole({1, 2})
    @GetMapping("/page")
    @ApiOperation("查看出库记录")
    public Result<PageResult> page(@RequestParam(required = false) Long skuId,
                                   @RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(inventoryService.pageStockLog(skuId, page, pageSize));
    }
}