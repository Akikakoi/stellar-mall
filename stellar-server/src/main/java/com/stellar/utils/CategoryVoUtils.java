package com.stellar.utils;

import com.stellar.entity.Category;
import com.stellar.vo.CategoryVO;
import org.springframework.beans.BeanUtils;

import java.util.stream.Collectors;

/** 分类实体 → 分类 VO 的统一转换（管理端与 C 端共用）。 */
public final class CategoryVoUtils {

    private CategoryVoUtils() {
    }

    public static CategoryVO toVo(Category category) {
        if (category == null) return null;
        CategoryVO vo = new CategoryVO();
        BeanUtils.copyProperties(category, vo);
        return vo;
    }

    public static CategoryVO toVoWithChildren(Category category) {
        if (category == null) return null;
        CategoryVO vo = toVo(category);
        if (category.getChildren() != null) {
            vo.setChildren(category.getChildren().stream()
                    .map(CategoryVoUtils::toVoWithChildren)
                    .collect(Collectors.toList()));
        }
        return vo;
    }
}
