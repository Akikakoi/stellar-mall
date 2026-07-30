package com.stellar.controller.user;

import com.stellar.context.BaseContext;
import com.stellar.result.Result;
import com.stellar.service.FavoriteService;
import com.stellar.vo.FavoriteVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * C 端收藏夹 Controller：/user/favorite。
 *   - POST   /user/favorite/{spuId}      → 添加收藏
 *   - DELETE /user/favorite/{spuId}      → 取消收藏
 *   - GET    /user/favorite/{spuId}      → 是否已收藏
 *   - GET    /user/favorite              → 收藏列表
 *   - POST   /user/favorite/batch-check  → 批量查询收藏状态
 */
@RestController
@RequestMapping("/user/favorite")
@RequiredArgsConstructor
@Api(tags = "C端：收藏夹")
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping("/{spuId}")
    @ApiOperation("添加收藏")
    public Result<String> add(@PathVariable Long spuId) {
        favoriteService.add(BaseContext.getCurrentId(), spuId);
        return Result.success();
    }

    @DeleteMapping("/{spuId}")
    @ApiOperation("取消收藏")
    public Result<String> remove(@PathVariable Long spuId) {
        favoriteService.remove(BaseContext.getCurrentId(), spuId);
        return Result.success();
    }

    @GetMapping("/{spuId}")
    @ApiOperation("查询是否已收藏")
    public Result<Boolean> isFavorited(@PathVariable Long spuId) {
        boolean fav = favoriteService.isFavorited(BaseContext.getCurrentId(), spuId);
        return Result.success(fav);
    }

    @GetMapping
    @ApiOperation("收藏夹列表")
    public Result<List<FavoriteVO>> list() {
        return Result.success(favoriteService.list(BaseContext.getCurrentId()));
    }

    @PostMapping("/batch-check")
    @ApiOperation("批量查询收藏状态：传入 spuIds 数组，返回已收藏的 spuId 列表")
    public Result<List<Long>> batchCheck(@RequestBody Map<String, List<Long>> body) {
        List<Long> spuIds = body.get("spuIds");
        return Result.success(favoriteService.listFavoritedSpuIds(BaseContext.getCurrentId(), spuIds));
    }
}