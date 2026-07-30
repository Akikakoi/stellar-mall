package com.stellar.mapper;

import com.stellar.annotation.AutoFill;
import com.stellar.entity.Category;
import com.stellar.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CategoryMapper {

    @AutoFill(OperationType.INSERT)
    int insert(Category category);

    @AutoFill(OperationType.UPDATE)
    int update(Category category);

    int deleteById(@Param("id") Long id);

    Category getById(@Param("id") Long id);

    /** 唯一键 (parent_id, name, type) 查询。 */
    Category getByParentNameType(@Param("parentId") Long parentId,
                                 @Param("name") String name,
                                 @Param("type") Integer type);

    long countByParentId(@Param("parentId") Long parentId);

    long count(@Param("name") String name,
               @Param("type") Integer type,
               @Param("status") Integer status);

    List<Category> page(@Param("offset") int offset, @Param("pageSize") int pageSize,
                        @Param("name") String name,
                        @Param("type") Integer type,
                        @Param("status") Integer status,
                        @Param("sortBy") String sortBy,
                        @Param("sortOrder") String sortOrder);

    /** flat 查询：level/status/type 过滤。 */
    List<Category> list(@Param("level") Integer level,
                        @Param("status") Integer status,
                        @Param("type") Integer type);
}
