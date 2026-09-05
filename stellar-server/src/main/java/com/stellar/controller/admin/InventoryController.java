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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/inventory")
@RequiredArgsConstructor
@Api(tags = "管理端：库存管理")
public class InventoryController {

    private final InventoryService inventoryService;

    @RequireRole({1, 2})
    @GetMapping("/page")
    @ApiOperation("SKU 库存分页（支持 name 模糊搜索和 lowStock 低库存过滤）")
    public Result<PageResult> page(@RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "20") Integer pageSize,
                                   @RequestParam(required = false) String name,
                                   @RequestParam(required = false) Integer lowStock) {
        return Result.success(inventoryService.pageInventory(page, pageSize, name, lowStock));
    }

    @Idempotent(keyPrefix = "admin-inventory-update", windowSeconds = 300)
    @RequireRole({1, 2})
    @PutMapping("/stock")
    @ApiOperation("调整单个 SKU 库存（自动记录流水）")
    public Result<String> updateStock(@RequestBody Map<String, Object> body) {
        Long skuId = Long.valueOf(body.get("skuId").toString());
        Integer delta = body.get("delta") != null ? Integer.valueOf(body.get("delta").toString()) : null;
        Integer warnStock = body.get("warnStock") != null ? Integer.valueOf(body.get("warnStock").toString()) : null;
        String remark = body.get("remark") != null ? body.get("remark").toString() : null;
        inventoryService.updateStock(skuId, delta, warnStock, remark);
        return Result.success();
    }

    @RequireRole({1, 2})
    @PostMapping("/batch-stock")
    @ApiOperation("批量调整库存（自动记录流水）")
    public Result<String> batchUpdateStock(@RequestBody List<Map<String, Object>> items) {
        inventoryService.batchUpdateStock(items);
        return Result.success();
    }

    @RequireRole({1, 2})
    @GetMapping("/log")
    @ApiOperation("查询库存变动流水（可按 SKU ID 筛选）")
    public Result<PageResult> stockLog(@RequestParam(required = false) Long skuId,
                                       @RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "20") Integer pageSize) {
        if (skuId != null) {
            return Result.success(inventoryService.pageStockLog(skuId, page, pageSize));
        }
        return Result.success(inventoryService.pageAllStockLog(page, pageSize));
    }
}