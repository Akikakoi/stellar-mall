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

@Service
@RequiredArgsConstructor
public class SkuServiceImpl implements SkuService {

    private final SkuMapper skuMapper;

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

    @Override
    public Sku getById(Long id) {
        return id == null ? null : skuMapper.getById(id);
    }

    @Override
    public List<Sku> listBySpuId(Long spuId) {
        if (spuId == null) return Collections.emptyList();
        List<Sku> list = skuMapper.listBySpuId(spuId);
        return list == null ? Collections.emptyList() : list;
    }

    @Override
    public void update(Sku sku) {
        if (sku == null || sku.getId() == null) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        }
        skuMapper.update(sku);
    }

    @Override
    public void deleteById(Long id) {
        if (id == null) return;
        skuMapper.deleteById(id);
    }
}
