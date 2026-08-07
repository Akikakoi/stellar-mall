package com.stellar.mapper;

import com.stellar.annotation.AutoFill;
import com.stellar.entity.Category;
import com.stellar.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品分类 Mapper，支持多级分类树结构的增删改查及分页。
 * 表：stellar_category。
 */
@Mapper
public interface CategoryMapper {

    @AutoFill(OperationType.INSERT)
    int insert(Category category);

    @AutoFill(OperationType.UPDATE)
    int update(Category category);

    int deleteById(@Param("id") Long id);

    Category getById(@Param("id") Long id);

    /** 唯一键 (name, type) 查询。 */
    Category getByNameType(@Param("name") String name,
                           @Param("type") Integer type);

    long count(@Param("name") String name,
               @Param("type") Integer type,
               @Param("status") Integer status);

    List<Category> page(@Param("offset") int offset, @Param("pageSize") int pageSize,
                        @Param("name") String name,
                        @Param("type") Integer type,
                        @Param("status") Integer status,
                        @Param("sortBy") String sortBy,
                        @Param("sortOrder") String sortOrder);

    /** flat 查询：status/type 过滤。 */
    List<Category> list(@Param("status") Integer status,
                        @Param("type") Integer type);
}
