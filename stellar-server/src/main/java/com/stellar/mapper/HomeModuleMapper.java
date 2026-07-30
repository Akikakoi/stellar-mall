package com.stellar.mapper;

import com.stellar.entity.HomeModule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HomeModuleMapper {

    int insert(HomeModule module);

    int update(HomeModule module);

    int deleteById(@Param("id") Long id);

    HomeModule getById(@Param("id") Long id);

    /** 查询全部模块（管理端），按 sort_order 升序 */
    List<HomeModule> listAll();

    /** C 端查询所有启用的模块，按 sort_order 升序 */
    List<HomeModule> listEnabled();

    /** 批量更新排序 */
    int updateSortOrder(@Param("id") Long id, @Param("sortOrder") Integer sortOrder);
}
