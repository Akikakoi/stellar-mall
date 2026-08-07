package com.stellar.mapper;

import com.stellar.entity.Banner;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Banner 轮播图 Mapper，管理首页轮播图的增删改查及启用状态。
 * 表：stellar_banner。
 */
@Mapper
public interface BannerMapper {

    int insert(Banner banner);

    int update(Banner banner);

    int deleteById(@Param("id") Long id);

    Banner getById(@Param("id") Long id);

    /** 管理端分页 */
    List<Banner> page(@Param("title") String title,
                      @Param("status") Integer status,
                      @Param("offset") int offset,
                      @Param("pageSize") int pageSize);

    long count(@Param("title") String title,
               @Param("status") Integer status);

    /** C 端：查询所有启用的 Banner，按 sort 降序 */
    List<Banner> listEnabled();
}