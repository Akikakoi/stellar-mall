package com.stellar.controller.user;

import com.stellar.annotation.RateLimit;
import com.stellar.context.BaseContext;
import com.stellar.dto.CartAddDTO;
import com.stellar.dto.CartUpdateDTO;
import com.stellar.result.Result;
import com.stellar.service.CartService;
import com.stellar.vo.CartVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * C 端购物车 Controller：/user/cart。
 *   - GET    /user/cart        → 列表（按 id 倒序，每条含 SPU/SKU 展示信息）
 *   - POST   /user/cart        → 新增（同 SKU 合并 qty，默认 checked=1）
 *   - PUT    /user/cart        → 更新 qty 或 checked（传哪个改哪个）
 *   - DELETE /user/cart/{id}   → 删除单项
 */
@RestController
@RequestMapping("/user/cart")
@RequiredArgsConstructor
@Api(tags = "C端：购物车")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @ApiOperation("购物车列表")
    public Result<List<CartVO>> list() {
        return Result.success(cartService.list(BaseContext.getCurrentId()));
    }

    @RateLimit(key = "cart-add", maxRequests = 30, windowSeconds = 60)
    @PostMapping
    @ApiOperation("添加购物车：同 SKU 合并数量")
    public Result<String> add(@RequestBody CartAddDTO dto) {
        cartService.add(BaseContext.getCurrentId(), dto);
        return Result.success();
    }

    @PutMapping
    @ApiOperation("更新购物车单项：qty / checked，传哪个改哪个")
    public Result<String> update(@RequestBody CartUpdateDTO dto) {
        cartService.update(BaseContext.getCurrentId(), dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除指定购物车记录")
    public Result<String> delete(@PathVariable Long id) {
        cartService.delete(BaseContext.getCurrentId(), id);
        return Result.success();
    }

    @DeleteMapping("/clear")
    @ApiOperation("清空购物车")
    public Result<String> clear() {
        cartService.clear(BaseContext.getCurrentId());
        return Result.success();
    }
}
