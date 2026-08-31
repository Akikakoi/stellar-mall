package com.stellar.controller.admin;

import com.stellar.annotation.Idempotent;
import com.stellar.annotation.RequireRole;
import com.stellar.context.BaseContext;
import com.stellar.dto.AfterSaleAuditDTO;
import com.stellar.result.PageResult;
import com.stellar.result.Result;
import com.stellar.service.AfterSaleService;
import com.stellar.vo.AfterSaleVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端售后接口。
 */
@Slf4j
@RestController
@RequestMapping("/admin/aftersale")
@RequiredArgsConstructor
@Api(tags = "管理端：售后管理")
public class AfterSaleAdminController {

    private final AfterSaleService afterSaleService;

@RequireRole({1, 2, 3})
    @GetMapping("/page")
    @ApiOperation("售后列表（分页）")
    public Result<PageResult> page(@RequestParam(defaultValue = "1") Integer page,
                                   @RequestParam(defaultValue = "10") Integer pageSize,
                                   @RequestParam(required = false) Integer status,
                                   @RequestParam(required = false) Integer type) {
        return Result.success(afterSaleService.pageAll(page, pageSize, status, type));
    }

@RequireRole({1, 2, 3})
    @GetMapping("/{id}")
    @ApiOperation("售后详情")
    public Result<AfterSaleVO> detail(@PathVariable Long id) {
        return Result.success(afterSaleService.getDetailById(id));
    }

    @Idempotent(keyPrefix = "admin-aftersale-audit", windowSeconds = 300)
@RequireRole({1, 2, 3})
    @PostMapping("/audit")
    @ApiOperation("审核售后单")
    public Result<Void> audit(@RequestBody AfterSaleAuditDTO dto) {
        Long empId = BaseContext.getCurrentId();
        afterSaleService.audit(empId, dto);
        return Result.success();
    }

    @Idempotent(keyPrefix = "admin-aftersale-refund", windowSeconds = 300)
@RequireRole({1, 2, 4})
    @PostMapping("/{id}/confirm-refund")
    @ApiOperation("确认退款完成")
    public Result<Void> confirmRefund(@PathVariable Long id) {
        Long empId = BaseContext.getCurrentId();
        try {
            afterSaleService.confirmRefund(empId, id);
            return Result.success();
        } catch (Exception e) {
            log.error("[AfterSaleAdmin] 确认退款失败 id={}: {}", id, e.getMessage(), e);
            return Result.error("退款失败：" + e.getMessage());
        }
    }
}
