package com.stellar.mapper;

import com.stellar.dto.HomeModuleSaveDTO;
import com.stellar.entity.HomeModule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 首页模块 Mapper。表：stellar_home_module。
 */
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

    /** 单条更新排序 */
    int updateSortOrder(@Param("id") Long id, @Param("sortOrder") Integer sortOrder);

    /** 批量更新排序：一条 CASE WHEN SQL 完成，替代循环单条 UPDATE。items 不可为空。 */
    int batchUpdateSortOrder(@Param("items") List<HomeModuleSaveDTO> items);
}
