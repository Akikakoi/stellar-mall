package com.stellar.controller.admin;

import com.stellar.annotation.RequireRole;
import com.stellar.entity.ServiceItem;
import com.stellar.result.Result;
import com.stellar.service.ServiceItemService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理端：保障服务管理。
 */
@RestController
@RequestMapping("/admin/service")
@RequiredArgsConstructor
@Api(tags = "管理端：保障服务管理")
public class ServiceAdminController {

    private final ServiceItemService serviceItemService;

    @GetMapping("/list")
    @ApiOperation("保障服务列表（全量，含停用）")
    @RequireRole({1, 2})
    public Result<List<ServiceItem>> list() {
        return Result.success(serviceItemService.listAll());
    }

    @GetMapping("/enabled")
    @ApiOperation("启用中的保障服务列表（商品编辑下拉用）")
    @RequireRole({1, 2})
    public Result<List<ServiceItem>> listEnabled() {
        return Result.success(serviceItemService.listEnabled());
    }

    @PostMapping
    @ApiOperation("新增/更新保障服务")
    @RequireRole({1, 2})
    public Result<Long> save(@RequestBody ServiceItem item) {
        return Result.success(serviceItemService.save(item));
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除保障服务")
    @RequireRole({1})
    public Result<String> delete(@PathVariable Long id) {
        serviceItemService.delete(id);
        return Result.success();
    }
}
