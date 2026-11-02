package com.stellar.controller.user;

import com.stellar.entity.Category;
import com.stellar.result.Result;
import com.stellar.service.CategoryService;
import com.stellar.utils.CategoryVoUtils;
import com.stellar.vo.CategoryVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/user/category")
@RequiredArgsConstructor
@Api(tags = "C端：分类")
public class UserCategoryController {

    private final CategoryService categoryService;

    @GetMapping("/list")
    @ApiOperation("分类列表（嵌套 tree，最多 2 级，仅启用的）")
    public Result<List<CategoryVO>> list() {
        List<Category> tree = categoryService.tree(true);
        return Result.success(tree.stream()
                .map(CategoryVoUtils::toVoWithChildren)
                .collect(Collectors.toList()));
    }
}
