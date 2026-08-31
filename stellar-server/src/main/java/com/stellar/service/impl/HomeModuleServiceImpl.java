package com.stellar.service.impl;

import com.stellar.context.BaseContext;
import com.stellar.dto.HomeModuleSaveDTO;
import com.stellar.entity.HomeModule;
import com.stellar.mapper.HomeModuleMapper;
import com.stellar.service.HomeModuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 首页模块服务实现。
 * <p>
 * 提供首页模块的创建、更新、删除、查询和批量排序等操作。
 * 启用状态的模块列表会被缓存，写操作会清除缓存。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class HomeModuleServiceImpl implements HomeModuleService {

    private final HomeModuleMapper homeModuleMapper;

    /**
     * 创建首页模块。
     *
     * @param dto 模块保存参数
     * @return 新创建的模块ID
     */
    @Override
    @Transactional
    @CacheEvict(value = "homeModule:listEnabled", allEntries = true)
    public Long create(HomeModuleSaveDTO dto) {
        Long userId = BaseContext.getCurrentId();
        HomeModule module = HomeModule.builder()
                .type(dto.getType())
                .title(dto.getTitle() != null ? dto.getTitle() : "")
                .config(dto.getConfig())
                .sortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0)
                .status(dto.getStatus() != null ? dto.getStatus() : 1)
                .createTime(LocalDateTime.now())
                .createUser(userId)
                .updateTime(LocalDateTime.now())
                .updateUser(userId)
                .build();
        homeModuleMapper.insert(module);
        return module.getId();
    }

    /**
     * 更新首页模块。
     *
     * @param dto 模块更新参数
     */
    @Override
    @Transactional
    @CacheEvict(value = "homeModule:listEnabled", allEntries = true)
    public void update(HomeModuleSaveDTO dto) {
        Long userId = BaseContext.getCurrentId();
        HomeModule module = HomeModule.builder()
                .id(dto.getId())
                .type(dto.getType())
                .title(dto.getTitle())
                .config(dto.getConfig())
                .sortOrder(dto.getSortOrder())
                .status(dto.getStatus())
                .updateTime(LocalDateTime.now())
                .updateUser(userId)
                .build();
        homeModuleMapper.update(module);
    }

    /**
     * 删除首页模块。
     *
     * @param id 模块ID
     */
    @Override
    @Transactional
    @CacheEvict(value = "homeModule:listEnabled", allEntries = true)
    public void delete(Long id) {
        homeModuleMapper.deleteById(id);
    }

    /**
     * 查询所有首页模块（含禁用）。
     *
     * @return 所有模块列表
     */
    @Override
    public List<HomeModule> listAll() {
        return homeModuleMapper.listAll();
    }

    /**
     * 查询所有已启用的首页模块，结果会被缓存。
     *
     * @return 已启用模块列表
     */
    @Override
    @Cacheable(value = "homeModule:listEnabled", key = "'enabled'")
    public List<HomeModule> listEnabled() {
        return homeModuleMapper.listEnabled();
    }

    /**
     * 批量更新模块排序。
     *
     * @param items 模块排序参数列表
     */
    @Override
    @Transactional
    @CacheEvict(value = "homeModule:listEnabled", allEntries = true)
    public void batchSort(List<HomeModuleSaveDTO> items) {
        if (items == null || items.isEmpty()) return;
        homeModuleMapper.batchUpdateSortOrder(items);
    }
}
