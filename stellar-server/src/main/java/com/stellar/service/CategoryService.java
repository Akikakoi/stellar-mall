package com.stellar.service;

import com.stellar.dto.CategoryPageQueryDTO;
import com.stellar.dto.CategorySaveDTO;
import com.stellar.dto.CategoryUpdateDTO;
import com.stellar.entity.Category;
import com.stellar.result.PageResult;
import com.stellar.vo.CategoryDeletableVO;

import java.util.List;

public interface CategoryService {

    Long save(CategorySaveDTO dto);

    Category getById(Long id);

    void update(CategoryUpdateDTO dto);

    /** 分类删除：有子分类 → 拒绝；若还有 SPU → 拒绝。 */
    void deleteById(Long id);

    /**
     * 删除前预校验（给前端 handleDelete 在进入确认框之前调用）。
     * @return 是否允许删除 + 关联商品/子分类数量 + 禁止原因
     */
    CategoryDeletableVO checkDeletable(Long id);

    /** 启停用。 */
    void startOrStop(Long id, Integer status);

    PageResult pageQuery(CategoryPageQueryDTO dto);

    /** 组装嵌套 tree：最多 2 级。onlyEnabled=true 时只取启用的。 */
    List<Category> tree(boolean onlyEnabled);
}
