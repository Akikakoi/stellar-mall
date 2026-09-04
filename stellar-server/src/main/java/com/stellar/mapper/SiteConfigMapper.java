package com.stellar.mapper;

import com.stellar.entity.SiteConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 站点级配置 Mapper。表：stellar_site_config。
 */
@Mapper
public interface SiteConfigMapper {

    /** 按配置键查询（无记录返回 null = 使用默认值） */
    SiteConfig getByKey(@Param("configKey") String configKey);

    int insert(SiteConfig config);

    /** 更新配置值与操作人 */
    int updateValue(@Param("configKey") String configKey,
                    @Param("configValue") String configValue,
                    @Param("updateUser") Long updateUser);

    /** 删除配置行（恢复默认） */
    int deleteByKey(@Param("configKey") String configKey);
}
