package com.stellar.controller.admin;

import com.stellar.annotation.Idempotent;
import com.stellar.annotation.RequireRole;
import com.stellar.exception.BaseException;
import com.stellar.result.PageResult;
import com.stellar.result.Result;
import com.stellar.service.InventoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
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
        Object skuIdVal = body.get("skuId");
        if (skuIdVal == null) {
            throw new BaseException("skuId 不能为空");
        }
        Long skuId = Long.valueOf(skuIdVal.toString());
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
    @ApiOperation("出入库日志分页（支持商品模糊/操作类型/时间范围筛选，按时间倒序）")
    public Result<PageResult> stockLog(@RequestParam(required = false) Long skuId,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) Integer type,
                                       @RequestParam(required = false) String begin,
                                       @RequestParam(required = false) String end,
                                       @RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "20") Integer pageSize) {
        // 时间范围：前端传 yyyy-MM-dd，起止分别取当天 00:00:00 / 23:59:59
        LocalDateTime beginTime = parseDate(begin, true);
        LocalDateTime endTime = parseDate(end, false);
        return Result.success(inventoryService.pageStockLog(skuId, keyword, type, beginTime, endTime, page, pageSize));
    }

    /** 解析 yyyy-MM-dd 日期串为 LocalDateTime；begin=true 取当天起点，否则取当天终点。 */
    private LocalDateTime parseDate(String date, boolean isBegin) {
        if (date == null || date.isBlank()) {
            return null;
        }
        LocalDate localDate;
        try {
            localDate = LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw new BaseException("日期格式错误，应为 yyyy-MM-dd：" + date);
        }
        return isBegin ? localDate.atStartOfDay() : localDate.atTime(LocalTime.MAX);
    }
}