package com.stellar.service.impl;

import com.stellar.context.BaseContext;
import com.stellar.entity.Banner;
import com.stellar.mapper.BannerMapper;
import com.stellar.result.PageResult;
import com.stellar.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 轮播图（Banner）服务实现类。
 * <p>提供轮播图的增删改查功能，包括创建、更新、删除、分页查询以及获取启用的轮播图列表。</p>
 */
@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private final BannerMapper bannerMapper;

    /**
     * 创建新的轮播图。
     * <p>自动填充创建时间、创建人、更新时间、更新人，若未设置排序字段默认值为0，若未设置状态默认启用（1）。</p>
     *
     * @param banner 轮播图实体
     * @return 创建后的轮播图ID
     */
    @Override
    @Transactional
    @CacheEvict(value = "banner:listEnabled", allEntries = true)
    public Long create(Banner banner) {
        Long userId = BaseContext.getCurrentId();
        banner.setCreateTime(LocalDateTime.now());
        banner.setCreateUser(userId);
        banner.setUpdateTime(LocalDateTime.now());
        banner.setUpdateUser(userId);
        if (banner.getSort() == null) banner.setSort(0);
        if (banner.getStatus() == null) banner.setStatus(1);
        bannerMapper.insert(banner);
        return banner.getId();
    }

    /**
     * 更新轮播图信息。
     * <p>自动填充更新时间和更新人，更新完成后清除启用列表缓存。</p>
     *
     * @param banner 包含更新字段的轮播图实体
     */
    @Override
    @Transactional
    @CacheEvict(value = "banner:listEnabled", allEntries = true)
    public void update(Banner banner) {
        Long userId = BaseContext.getCurrentId();
        banner.setUpdateTime(LocalDateTime.now());
        banner.setUpdateUser(userId);
        bannerMapper.update(banner);
    }

    /**
     * 根据ID删除轮播图。
     * <p>删除完成后清除启用列表缓存。</p>
     *
     * @param id 轮播图ID
     */
    @Override
    @Transactional
    @CacheEvict(value = "banner:listEnabled", allEntries = true)
    public void delete(Long id) {
        bannerMapper.deleteById(id);
    }

    /**
     * 分页查询轮播图列表。
     * <p>支持按标题和状态进行筛选，若页码或每页条数无效则使用默认值（page=1, pageSize=10）。</p>
     *
     * @param title    标题关键词（可为空）
     * @param status   状态筛选（可为空）
     * @param page     当前页码
     * @param pageSize 每页条数
     * @return 分页结果，包含总记录数和轮播图列表
     */
    @Override
    public PageResult page(String title, Integer status, Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int ps = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        List<Banner> list = bannerMapper.page(title, status, (p - 1) * ps, ps);
        long total = bannerMapper.count(title, status);
        return new PageResult(total, list == null ? new ArrayList<>() : list);
    }

    /**
     * 获取所有启用的轮播图列表。
     * <p>结果会缓存到 {@code banner:listEnabled} 缓存中，在创建、更新、删除操作时自动清除缓存。</p>
     *
     * @return 启用的轮播图列表
     */
    @Override
    @Cacheable(value = "banner:listEnabled", key = "'enabled'")
    public List<Banner> listEnabled() {
        return bannerMapper.listEnabled();
    }
}