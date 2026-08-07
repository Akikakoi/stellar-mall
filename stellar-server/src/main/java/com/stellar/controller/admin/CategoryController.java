package com.stellar.controller.admin;

import com.stellar.dto.CategoryPageQueryDTO;
import com.stellar.dto.CategorySaveDTO;
import com.stellar.dto.CategoryUpdateDTO;
import com.stellar.entity.Category;
import com.stellar.result.PageResult;
import com.stellar.result.Result;
import com.stellar.service.CategoryService;
import com.stellar.vo.CategoryDeletableVO;
import com.stellar.vo.CategoryVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 管理端：分类管理。
 * 提供分类的增删改查、启停用、嵌套树查询以及删除前预校验等功能。
 */
@RestController
@RequestMapping("/admin/category")
@RequiredArgsConstructor
@Api(tags = "管理端：分类管理")
public class CategoryController {

    private final CategoryService categoryService;

    private static CategoryVO toVo(Category c) {
        if (c == null) return null;
        CategoryVO vo = new CategoryVO();
        BeanUtils.copyProperties(c, vo);
        return vo;
    }

    private static CategoryVO toVoWithChildren(Category c) {
        if (c == null) return null;
        CategoryVO vo = toVo(c);
        if (c.getChildren() != null) {
            vo.setChildren(c.getChildren().stream()
                    .map(CategoryController::toVoWithChildren)
                    .collect(Collectors.toList()));
        }
        return vo;
    }

    @PostMapping
    @ApiOperation("新增分类")
    public Result<Map<String, Long>> save(@RequestBody @Valid CategorySaveDTO dto) {
        Long id = categoryService.save(dto);
        Map<String, Long> data = new HashMap<>();
        data.put("id", id);
        return Result.success(data);
    }

    @GetMapping("/{id}")
    @ApiOperation("按 id 查询分类")
    public Result<CategoryVO> getById(@PathVariable Long id) {
        return Result.success(toVo(categoryService.getById(id)));
    }

    @PutMapping
    @ApiOperation("更新分类")
    public Result<String> update(@RequestBody @Valid CategoryUpdateDTO dto) {
        categoryService.update(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除分类")
    public Result<String> delete(@PathVariable Long id) {
        categoryService.deleteById(id);
        return Result.success();
    }

    @GetMapping("/{id}/deletable")
    @ApiOperation("删除分类前预校验：返回是否允许删除 + 关联商品/子分类数量 + 禁止原因。前端在弹出确认框前先调用。")
    public Result<CategoryDeletableVO> getDeletable(@PathVariable Long id) {
        return Result.success(categoryService.checkDeletable(id));
    }

    @PostMapping("/status/{status}")
    @ApiOperation("启停用分类")
    public Result<String> startOrStop(@RequestParam Long id, @PathVariable Integer status) {
        categoryService.startOrStop(id, status);
        return Result.success();
    }

    @GetMapping("/page")
    @ApiOperation("分页查询分类")
    public Result<PageResult> page(CategoryPageQueryDTO dto) {
        PageResult pr = categoryService.pageQuery(dto);
        List<Category> records = (List<Category>) pr.getRecords();
        List<CategoryVO> vos = records == null ? new ArrayList<>() :
                records.stream().map(CategoryController::toVo).collect(Collectors.toList());
        pr.setRecords(vos);
        return Result.success(pr);
    }

    @GetMapping("/tree")
    @ApiOperation("分类嵌套树（最多 2 级）。onlyEnabled=true 只返回启用的")
    public Result<List<CategoryVO>> tree(
            @RequestParam(required = false, defaultValue = "false") boolean onlyEnabled) {
        List<Category> tree = categoryService.tree(onlyEnabled);
        return Result.success(tree.stream()
                .map(CategoryController::toVoWithChildren)
                .collect(Collectors.toList()));
    }

    @GetMapping("/list")
    @ApiOperation("分类列表（嵌套 tree，包含启用/禁用的全部）")
    public Result<List<CategoryVO>> list() {
        List<Category> tree = categoryService.tree(false);
        return Result.success(tree.stream()
                .map(CategoryController::toVoWithChildren)
                .collect(Collectors.toList()));
    }
}
