package com.stellar.service;

import com.stellar.result.PageResult;

/**
 * C 端商品浏览历史服务。表：stellar_browse_history。
 */
public interface BrowseHistoryService {

    /** 记录一次浏览：upsert 刷新时间，超上限时淘汰最旧。 */
    void record(Long userId, Long spuId, Long skuId);

    /** 分页查询当前用户浏览历史（倒序）。 */
    PageResult page(Long userId, int page, int pageSize);

    /** 删除单条浏览记录（校验归属，越权静默跳过）。 */
    void deleteById(Long userId, Long id);

    /** 清空当前用户全部浏览记录。 */
    void clear(Long userId);
}