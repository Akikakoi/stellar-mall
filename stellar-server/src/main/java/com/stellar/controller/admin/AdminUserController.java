package com.stellar.controller.admin;

import com.stellar.annotation.RequireRole;
import com.stellar.context.BaseContext;
import com.stellar.result.PageResult;
import com.stellar.result.Result;
import com.stellar.service.AdminUserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端：C 端用户管理（只读列表 + 启用/锁定）。
 */
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
@Api(tags = "管理端：用户管理")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @RequireRole({1})
    @GetMapping("/page")
    @ApiOperation("分页查询 C 端用户")
    public Result<PageResult> page(@RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) Integer status,
                                   @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(adminUserService.page(keyword, status, page, pageSize));
    }

    @RequireRole({1})
    @PostMapping("/{id}/status")
    @ApiOperation("启用/锁定用户：status=1 正常，status=0 锁定")
    public Result<Void> status(@PathVariable Long id, @RequestParam Integer status) {
        adminUserService.setStatus(id, status, BaseContext.getCurrentId());
        return Result.success();
    }
}