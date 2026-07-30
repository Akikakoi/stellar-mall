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

@Service
@RequiredArgsConstructor
public class HomeModuleServiceImpl implements HomeModuleService {

    private final HomeModuleMapper homeModuleMapper;

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

    @Override
    @Transactional
    @CacheEvict(value = "homeModule:listEnabled", allEntries = true)
    public void delete(Long id) {
        homeModuleMapper.deleteById(id);
    }

    @Override
    public List<HomeModule> listAll() {
        return homeModuleMapper.listAll();
    }

    @Override
    @Cacheable(value = "homeModule:listEnabled", key = "'enabled'")
    public List<HomeModule> listEnabled() {
        return homeModuleMapper.listEnabled();
    }

    @Override
    @Transactional
    @CacheEvict(value = "homeModule:listEnabled", allEntries = true)
    public void batchSort(List<HomeModuleSaveDTO> items) {
        for (HomeModuleSaveDTO item : items) {
            homeModuleMapper.updateSortOrder(item.getId(), item.getSortOrder());
        }
    }
}
