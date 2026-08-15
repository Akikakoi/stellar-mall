package com.stellar.controller.admin;

import com.stellar.annotation.Idempotent;
import com.stellar.dto.SpuPageQueryDTO;
import com.stellar.dto.SpuSaveDTO;
import com.stellar.elasticsearch.service.SpuSearchService;
import com.stellar.entity.Spu;
import com.stellar.result.PageResult;
import com.stellar.result.Result;
import com.stellar.service.SpuService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/spu")
@RequiredArgsConstructor
@Api(tags = "管理端：SPU 商品管理")
public class SpuController {

    private final SpuService spuService;
    private final SpuSearchService spuSearchService;

    @Idempotent(keyPrefix = "admin-spu-save", windowSeconds = 300)
    @PostMapping
    @ApiOperation("新增 SPU（含嵌套 SKU）")
    public Result<Map<String, Long>> save(@RequestBody @Valid SpuSaveDTO dto) {
        Long id = spuService.saveWithSkus(dto);
        Map<String, Long> data = new HashMap<>();
        data.put("id", id);
        return Result.success(data);
    }

    @GetMapping("/{id}")
    @ApiOperation("根据 id 查询 SPU 详情（含 SKU 列表）")
    public Result<Spu> getById(@PathVariable Long id) {
        return Result.success(spuService.getById(id));
    }

    @Idempotent(keyPrefix = "admin-spu-update", windowSeconds = 300)
    @PutMapping
    @ApiOperation("更新 SPU（传 SKU 则覆盖原 SKU，不传则保留）")
    public Result<String> update(@RequestBody @Valid SpuSaveDTO dto) {
        spuService.updateWithSkus(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除 SPU（同步删除关联 SKU）")
    public Result<String> delete(@PathVariable Long id) {
        spuService.deleteById(id);
        return Result.success();
    }

    @PostMapping("/status/{status}")
    @ApiOperation("上下架：status=1 上架 / 0 下架")
    public Result<String> onOffShelf(@RequestParam Long id, @PathVariable Integer status) {
        spuService.onOffShelf(id, status);
        return Result.success();
    }

    @PostMapping("/batch-status/{status}")
    @ApiOperation("批量上下架：status=1 上架 / 0 下架")
    public Result<String> batchOnOffShelf(@RequestBody List<Long> ids, @PathVariable Integer status) {
        spuService.batchOnOffShelf(ids, status);
        return Result.success();
    }

    @GetMapping("/page")
    @ApiOperation("分页查询 SPU（优先 ES 中文分词搜索，支持名称模糊/分类/状态/价格区间）")
    public Result<PageResult> page(SpuPageQueryDTO dto) {
        return Result.success(spuSearchService.search(dto));
    }
}
