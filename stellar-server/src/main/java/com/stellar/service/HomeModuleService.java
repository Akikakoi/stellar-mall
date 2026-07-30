package com.stellar.service;

import com.stellar.dto.HomeModuleSaveDTO;
import com.stellar.entity.HomeModule;

import java.util.List;

public interface HomeModuleService {

    Long create(HomeModuleSaveDTO dto);

    void update(HomeModuleSaveDTO dto);

    void delete(Long id);

    /** 管理端获取全部模块列表 */
    List<HomeModule> listAll();

    /** C 端获取启用的模块列表 */
    List<HomeModule> listEnabled();

    /** 批量更新排序 */
    void batchSort(List<HomeModuleSaveDTO> items);
}
