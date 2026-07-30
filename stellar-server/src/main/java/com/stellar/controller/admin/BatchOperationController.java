package com.stellar.controller.admin;

import com.stellar.result.Result;
import com.stellar.service.OrderService;
import com.stellar.service.SpuService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/batch")
@RequiredArgsConstructor
@Api(tags = "管理端：批量操作")
public class BatchOperationController {

    private final SpuService spuService;
    private final OrderService orderService;

    @PostMapping("/spu/shelf")
    @ApiOperation("批量上架")
    public Result<String> batchShelf(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("ids");
        if (ids != null) {
            for (Integer id : ids) {
                spuService.onOffShelf(id.longValue(), 1);
            }
        }
        return Result.success();
    }

    @PostMapping("/spu/unshelf")
    @ApiOperation("批量下架")
    public Result<String> batchUnshelf(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("ids");
        if (ids != null) {
            for (Integer id : ids) {
                spuService.onOffShelf(id.longValue(), 0);
            }
        }
        return Result.success();
    }

    @PostMapping("/order/ship")
    @ApiOperation("批量发货")
    public Result<String> batchShip(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("ids");
        if (ids != null) {
            for (Integer id : ids) {
                orderService.ship(id.longValue(), null, null);
            }
        }
        return Result.success();
    }
}