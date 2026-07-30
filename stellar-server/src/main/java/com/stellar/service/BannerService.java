package com.stellar.service;

import com.stellar.entity.Banner;
import com.stellar.result.PageResult;

import java.util.List;

public interface BannerService {

    Long create(Banner banner);

    void update(Banner banner);

    void delete(Long id);

    PageResult page(String title, Integer status, Integer page, Integer pageSize);

    List<Banner> listEnabled();
}