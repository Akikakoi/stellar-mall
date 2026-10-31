package com.stellar.mapper;

import com.stellar.entity.ServiceItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 保障服务字典 Mapper。表：stellar_service_item。
 */
@Mapper
public interface ServiceItemMapper {

    int insert(ServiceItem item);

    int update(ServiceItem item);

    int deleteById(@Param("id") Long id);

    ServiceItem getById(@Param("id") Long id);

    ServiceItem getByCode(@Param("code") String code);

    List<ServiceItem> listAll();

    /** 仅启用状态（C 端详情/管理端服务选择下拉用） */
    List<ServiceItem> listEnabled();

    List<ServiceItem> listByIds(@Param("ids") List<Long> ids);
}
