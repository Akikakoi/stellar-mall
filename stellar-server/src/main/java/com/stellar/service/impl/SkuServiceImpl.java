package com.stellar.service.impl;

import com.stellar.constant.MessageConstant;
import com.stellar.entity.Sku;
import com.stellar.exception.BaseException;
import com.stellar.mapper.SkuMapper;
import com.stellar.service.SkuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * SKU服务实现类。
 * <p>
 * 提供SKU的增删改查及按SPU ID查询SKU列表等功能。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SkuServiceImpl implements SkuService {

    private final SkuMapper skuMapper;

    /**
     * 新增SKU。
     * <p>
     * 自动填充默认值：version=0、status=1、stock=0、warnStock=10、sort=0。
     * </p>
     *
     * @param sku SKU实体
     * @return 新增SKU ID
     */
    @Override
    public Long save(Sku sku) {
        if (sku == null) throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        if (sku.getVersion() == null) sku.setVersion(0);
        if (sku.getStatus() == null) sku.setStatus(1);
        if (sku.getStock() == null) sku.setStock(0);
        if (sku.getWarnStock() == null) sku.setWarnStock(10);
        if (sku.getSort() == null) sku.setSort(0);
        skuMapper.insert(sku);
        return sku.getId();
    }

    /**
     * 根据ID查询SKU。
     *
     * @param id SKU ID
     * @return SKU实体，不存在时返回null
     */
    @Override
    public Sku getById(Long id) {
        return id == null ? null : skuMapper.getById(id);
    }

    /**
     * 根据SPU ID查询SKU列表。
     *
     * @param spuId SPU ID
     * @return SKU列表，没有结果时返回空列表
     */
    @Override
    public List<Sku> listBySpuId(Long spuId) {
        if (spuId == null) return Collections.emptyList();
        List<Sku> list = skuMapper.listBySpuId(spuId);
        return list == null ? Collections.emptyList() : list;
    }

    /**
     * 更新SKU信息。
     *
     * @param sku SKU实体（必须包含ID）
     */
    @Override
    public void update(Sku sku) {
        if (sku == null || sku.getId() == null) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        }
        skuMapper.update(sku);
    }

    /**
     * 根据ID删除SKU。
     *
     * @param id SKU ID
     */
    @Override
    public void deleteById(Long id) {
        if (id == null) return;
        skuMapper.deleteById(id);
    }
}
