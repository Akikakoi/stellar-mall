package com.stellar.controller.admin;

import com.stellar.dto.HomeModuleSaveDTO;
import com.stellar.entity.HomeModule;
import com.stellar.result.Result;
import com.stellar.service.HomeModuleService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/home-module")
@RequiredArgsConstructor
@Api(tags = "管理端：首页装修模块")
public class HomeModuleController {

    private final HomeModuleService homeModuleService;

    @PostMapping
    @ApiOperation("新增模块")
    public Result<Long> create(@Valid @RequestBody HomeModuleSaveDTO dto) {
        return Result.success(homeModuleService.create(dto));
    }

    @PutMapping("/{id}")
    @ApiOperation("更新模块")
    public Result<String> update(@PathVariable Long id, @Valid @RequestBody HomeModuleSaveDTO dto) {
        dto.setId(id);
        homeModuleService.update(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除模块")
    public Result<String> delete(@PathVariable Long id) {
        homeModuleService.delete(id);
        return Result.success();
    }

    @GetMapping("/list")
    @ApiOperation("获取全部模块列表")
    public Result<List<HomeModule>> list() {
        return Result.success(homeModuleService.listAll());
    }

    @PutMapping("/batch-sort")
    @ApiOperation("批量更新排序")
    public Result<String> batchSort(@RequestBody Map<String, List<HomeModuleSaveDTO>> body) {
        List<HomeModuleSaveDTO> items = body.get("items");
        if (items != null && !items.isEmpty()) {
            homeModuleService.batchSort(items);
        }
        return Result.success();
    }
}
