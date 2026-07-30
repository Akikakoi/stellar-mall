package com.stellar.controller.admin;

import com.stellar.annotation.RequireRole;
import com.stellar.dto.PointsAdjustDTO;
import com.stellar.dto.PointsProductSaveDTO;
import com.stellar.entity.PointsRule;
import com.stellar.result.PageResult;
import com.stellar.result.Result;
import com.stellar.service.PointsService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 管理端：积分管理。
 */
@RestController
@RequestMapping("/admin/points")
@RequiredArgsConstructor
@Api(tags = "管理端：积分管理")
public class PointsAdminController {

    private final PointsService pointsService;

    // ===== 积分规则管理 =====

    @GetMapping("/rules")
    @ApiOperation("积分规则列表")
    @RequireRole({1, 2})
    public Result<List<PointsRule>> listRules() {
        return Result.success(pointsService.listRules());
    }

    @PostMapping("/rules")
    @ApiOperation("保存积分规则")
    @RequireRole({1, 2})
    public Result<String> saveRule(@RequestBody PointsRule rule) {
        pointsService.saveRule(rule);
        return Result.success();
    }

    @DeleteMapping("/rules/{id}")
    @ApiOperation("删除积分规则")
    @RequireRole({1, 2})
    public Result<String> deleteRule(@PathVariable Long id) {
        pointsService.deleteRule(id);
        return Result.success();
    }

    // ===== 积分调整 =====

    @PostMapping("/adjust")
    @ApiOperation("管理员调整用户积分")
    @RequireRole({1, 2})
    public Result<String> adjustPoints(@RequestBody PointsAdjustDTO dto) {
        pointsService.adjustPoints(dto);
        return Result.success();
    }

    // ===== 积分商城商品管理 =====

    @GetMapping("/products")
    @ApiOperation("积分商品分页")
    @RequireRole({1, 2})
    public Result<PageResult> pageProducts(@RequestParam(required = false) String name,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(pointsService.pageProducts(name, status, page, pageSize));
    }

    @GetMapping("/products/{id}")
    @ApiOperation("积分商品详情")
    @RequireRole({1, 2})
    public Result<?> getProduct(@PathVariable Long id) {
        return Result.success(pointsService.getProductById(id));
    }

    @PostMapping("/products")
    @ApiOperation("创建积分商品")
    @RequireRole({1, 2})
    public Result<String> createProduct(@RequestBody @Valid PointsProductSaveDTO dto) {
        pointsService.saveProduct(dto);
        return Result.success();
    }

    @PutMapping("/products")
    @ApiOperation("更新积分商品")
    @RequireRole({1, 2})
    public Result<String> updateProduct(@RequestBody @Valid PointsProductSaveDTO dto) {
        pointsService.saveProduct(dto);
        return Result.success();
    }

    @DeleteMapping("/products/{id}")
    @ApiOperation("删除积分商品")
    @RequireRole({1, 2})
    public Result<String> deleteProduct(@PathVariable Long id) {
        pointsService.deleteProduct(id);
        return Result.success();
    }
}
