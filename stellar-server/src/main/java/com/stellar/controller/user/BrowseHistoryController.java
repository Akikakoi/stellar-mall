package com.stellar.controller.user;

import com.stellar.annotation.RateLimit;
import com.stellar.context.BaseContext;
import com.stellar.result.PageResult;
import com.stellar.result.Result;
import com.stellar.service.BrowseHistoryService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * C 端商品浏览历史 Controller：/user/browse（需登录）。
 *   - POST   /user/browse/{spuId}  → 记录一次浏览（详情页调用，可选 skuId）
 *   - GET    /user/browse/page     → 分页查询浏览历史（倒序，含商品快照）
 *   - DELETE /user/browse/{id}     → 删除单条浏览记录
 *   - DELETE /user/browse/clear    → 清空我的全部浏览记录
 */
@RestController
@RequestMapping("/user/browse")
@RequiredArgsConstructor
@Api(tags = "C端：商品浏览历史")
public class BrowseHistoryController {

    private final BrowseHistoryService browseHistoryService;

    @RateLimit(key = "browse", maxRequests = 30, windowSeconds = 60)
    @PostMapping("/{spuId}")
    @ApiOperation("记录一次浏览（需登录）")
    public Result<String> record(@PathVariable Long spuId,
                                 @RequestParam(required = false) Long skuId) {
        browseHistoryService.record(BaseContext.getCurrentId(), spuId, skuId);
        return Result.success();
    }

    @GetMapping("/page")
    @ApiOperation("分页查询当前用户浏览历史")
    public Result<PageResult> page(@RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(browseHistoryService.page(BaseContext.getCurrentId(), page, pageSize));
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除单条浏览记录")
    public Result<String> delete(@PathVariable Long id) {
        browseHistoryService.deleteById(BaseContext.getCurrentId(), id);
        return Result.success();
    }

    @DeleteMapping("/clear")
    @ApiOperation("清空我的全部浏览记录")
    public Result<String> clear() {
        browseHistoryService.clear(BaseContext.getCurrentId());
        return Result.success();
    }
}