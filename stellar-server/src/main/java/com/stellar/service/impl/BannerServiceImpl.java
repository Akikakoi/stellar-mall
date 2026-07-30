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

@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private final BannerMapper bannerMapper;

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

    @Override
    @Transactional
    @CacheEvict(value = "banner:listEnabled", allEntries = true)
    public void update(Banner banner) {
        Long userId = BaseContext.getCurrentId();
        banner.setUpdateTime(LocalDateTime.now());
        banner.setUpdateUser(userId);
        bannerMapper.update(banner);
    }

    @Override
    @Transactional
    @CacheEvict(value = "banner:listEnabled", allEntries = true)
    public void delete(Long id) {
        bannerMapper.deleteById(id);
    }

    @Override
    public PageResult page(String title, Integer status, Integer page, Integer pageSize) {
        int p = (page == null || page < 1) ? 1 : page;
        int ps = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        List<Banner> list = bannerMapper.page(title, status, (p - 1) * ps, ps);
        long total = bannerMapper.count(title, status);
        return new PageResult(total, list == null ? new ArrayList<>() : list);
    }

    @Override
    @Cacheable(value = "banner:listEnabled", key = "'enabled'")
    public List<Banner> listEnabled() {
        return bannerMapper.listEnabled();
    }
}