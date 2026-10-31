package com.stellar.service;

import com.stellar.entity.ServiceItem;

import java.util.List;

/**
 * 保障服务字典服务。
 */
public interface ServiceItemService {

    /** 全量服务列表（管理端） */
    List<ServiceItem> listAll();

    /** 启用中的服务（管理端商品编辑下拉等） */
    List<ServiceItem> listEnabled();

    /**
     * 新增/更新服务（id 为空新增，否则更新）。code 仅新增时可指定且全局唯一。
     *
     * @param item 服务数据
     * @return 服务 id
     */
    Long save(ServiceItem item);

    /**
     * 删除服务。仍被商品关联时拒绝删除（提示先取消关联）。
     *
     * @param id 服务 id
     */
    void delete(Long id);
}
