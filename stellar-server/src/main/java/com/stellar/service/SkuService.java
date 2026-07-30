package com.stellar.service;

import com.stellar.entity.Sku;

import java.util.List;

public interface SkuService {

    /** 单独保存 SKU（供测试/内部使用）。 */
    Long save(Sku sku);

    Sku getById(Long id);

    List<Sku> listBySpuId(Long spuId);

    void update(Sku sku);

    void deleteById(Long id);
}
