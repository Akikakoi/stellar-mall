package com.stellar.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.stellar.constant.MessageConstant;
import com.stellar.dto.SpuPageQueryDTO;
import com.stellar.dto.SpuSaveDTO;
import com.stellar.entity.Category;
import com.stellar.entity.Sku;
import com.stellar.entity.Spu;
import com.stellar.exception.BaseException;
import com.stellar.elasticsearch.event.SpuChangedEvent;
import com.stellar.mapper.CategoryMapper;
import com.stellar.mapper.SkuMapper;
import com.stellar.mapper.SpuMapper;
import com.stellar.ragsync.service.RagSyncService;
import com.stellar.result.PageResult;
import com.stellar.service.SpuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpuServiceImpl implements SpuService {

    private final SpuMapper spuMapper;
    private final SkuMapper skuMapper;
    private final CategoryMapper categoryMapper;
    private final RagSyncService ragSyncService;
    private final ApplicationEventPublisher eventPublisher;

    // ================= 保存 =================
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "spu:detail", key = "#result")
    public Long saveWithSkus(SpuSaveDTO dto) {
        if (dto == null) throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        if (dto.getCategoryId() == null) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        }
        // 校验二级分类归属一级分类
        validateCategoryRelation(dto.getCategoryId(), dto.getCategory2Id());

        Spu spu = new Spu();
        BeanUtils.copyProperties(dto, spu);

        // ===== 前端简易表单兼容：别名字段兜底 → 标准字段 =====
        String resolvedImage = dto.resolveMainImage();
        if (resolvedImage != null) spu.setMainImage(resolvedImage);
        String resolvedMd = dto.resolveDescriptionMd();
        if (resolvedMd != null) spu.setDescriptionMd(resolvedMd);
        String resolvedHtml = dto.resolveDescription();
        if (resolvedHtml != null) spu.setDescription(resolvedHtml);

        if (spu.getSort() == null) spu.setSort(0);
        if (spu.getStatus() == null) spu.setStatus(1);
        if (spu.getIsNew() == null) spu.setIsNew(0);
        if (spu.getIsHot() == null) spu.setIsHot(0);
        if (spu.getMainImage() == null) spu.setMainImage("");
        // NOT NULL 字段默认值（避免 insert 时报 cannot be null；聚合 SKU 后会在 refreshAggregates 中回写覆盖）
        if (spu.getSaleCount() == null) spu.setSaleCount(0);
        if (spu.getCommentCount() == null) spu.setCommentCount(0);
        if (spu.getTotalStock() == null) spu.setTotalStock(0);
        if (spu.getSkuCount() == null) spu.setSkuCount(0);
        if (spu.getMinPrice() == null) spu.setMinPrice(BigDecimal.ZERO);
        if (spu.getMaxPrice() == null) spu.setMaxPrice(BigDecimal.ZERO);
        if (spu.getStatus() == 1) {
            spu.setOnShelfTime(LocalDateTime.now());
        }

        spuMapper.insert(spu);
        Long spuId = spu.getId();

        // ===== 前端简易表单兼容：skuList 为空但 price 非空 → 自动生成 1 条默认 SKU =====
        List<Sku> skuList = dto.getSkuList();
        if ((skuList == null || skuList.isEmpty()) && dto.getPrice() != null) {
            Sku defaultSku = new Sku();
            defaultSku.setName(dto.getName());
            defaultSku.setSpecs("默认规格");
            defaultSku.setPrice(dto.getPrice());
            defaultSku.setOriginalPrice(dto.getPrice());
            defaultSku.setImage(spu.getMainImage());
            defaultSku.setStock(dto.getTotalStock() != null ? dto.getTotalStock() : 0);
            defaultSku.setStatus(1);
            defaultSku.setSort(0);
            skuList = new ArrayList<>();
            skuList.add(defaultSku);
        }

        // 保存嵌套 SKU
        if (skuList != null && !skuList.isEmpty()) {
            for (Sku s : skuList) {
                s.setSpuId(spuId);
                if (s.getStatus() == null) s.setStatus(1);
                if (s.getSort() == null) s.setSort(0);
                if (s.getStock() == null) s.setStock(0);
                if (s.getVersion() == null) s.setVersion(0);
                if (s.getWarnStock() == null) s.setWarnStock(10);
                skuMapper.insert(s);
            }
        }

        // 从 SKU 聚合回写 SPU
        refreshAggregates(spuId);

        // ===== [RAG] 变更入 Outbox（同事务） =====
        ragSyncService.enqueueSpuSync(spuId, "SAVE");
        // ===== [ES] 发布同步事件 =====
        eventPublisher.publishEvent(SpuChangedEvent.saved(spuId));
        return spuId;
    }

    @Override
    @Cacheable(value = "spu:detail", key = "#id", unless = "#result == null")
    public Spu getById(Long id) {
        if (id == null) return null;
        Spu s = spuMapper.getById(id);
        if (s != null) {
            List<Sku> skus = skuMapper.listBySpuId(id);
            if (skus != null) {
                skus.sort(Comparator.comparing(a -> a.getSort() == null ? 0 : a.getSort(),
                        Comparator.nullsLast(Integer::compareTo)));
            }
            s.setSkuList(skus);
            s.setSkus(skus);
        }
        return s;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "spu:detail", key = "#dto.id")
    public void updateWithSkus(SpuSaveDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        }
        // 校验二级分类归属一级分类（仅当传了 category2Id 时）
        if (dto.getCategoryId() != null && dto.getCategory2Id() != null) {
            validateCategoryRelation(dto.getCategoryId(), dto.getCategory2Id());
        }
        Spu old = spuMapper.getById(dto.getId());
        if (old == null) throw new BaseException(MessageConstant.SPU_NOT_FOUND);

        Spu update = new Spu();
        BeanUtils.copyProperties(dto, update);
        update.setId(dto.getId());

        // ===== 前端简易表单兼容：别名字段兜底 → 标准字段（仅当有值时覆盖，避免 null 覆盖已有） =====
        String resolvedImage = dto.resolveMainImage();
        if (resolvedImage != null) update.setMainImage(resolvedImage);
        // 描述双版本：只在 DTO 对应字段非空时覆盖；若一边空另一边有值，则两边同步
        String resolvedMd = dto.resolveDescriptionMd();
        if (resolvedMd != null) update.setDescriptionMd(resolvedMd);
        String resolvedHtml = dto.resolveDescription();
        if (resolvedHtml != null) update.setDescription(resolvedHtml);

        spuMapper.update(update);

        // ===== 前端简易表单兼容：skuList 为空但 price 非空 → 生成 1 条默认 SKU 并覆盖原 SKU =====
        List<Sku> skuList = dto.getSkuList();
        boolean hasExplicitSkus = skuList != null && !skuList.isEmpty();
        if (!hasExplicitSkus && dto.getPrice() != null) {
            Sku defaultSku = new Sku();
            defaultSku.setName(dto.getName() != null ? dto.getName() : old.getName());
            defaultSku.setSpecs("默认规格");
            defaultSku.setPrice(dto.getPrice());
            defaultSku.setOriginalPrice(dto.getPrice());
            defaultSku.setImage(update.getMainImage() != null ? update.getMainImage() : old.getMainImage());
            defaultSku.setStock(dto.getTotalStock() != null ? dto.getTotalStock() : 0);
            defaultSku.setStatus(1);
            defaultSku.setSort(0);
            skuList = new ArrayList<>();
            skuList.add(defaultSku);
            hasExplicitSkus = true;
        }

        // 若 skuList 非空则先删后插（覆盖式）；空则保留原 SKU
        if (hasExplicitSkus) {
            skuMapper.deleteBySpuId(dto.getId());
            for (Sku s : skuList) {
                s.setSpuId(dto.getId());
                s.setId(null); // 强制重生成 id
                if (s.getStatus() == null) s.setStatus(1);
                if (s.getSort() == null) s.setSort(0);
                if (s.getStock() == null) s.setStock(0);
                if (s.getVersion() == null) s.setVersion(0);
                if (s.getWarnStock() == null) s.setWarnStock(10);
                skuMapper.insert(s);
            }
            refreshAggregates(dto.getId());
        }

        // ===== [RAG] 变更入 Outbox（同事务） =====
        ragSyncService.enqueueSpuSync(dto.getId(), "SAVE");
        // ===== [ES] 发布同步事件 =====
        eventPublisher.publishEvent(SpuChangedEvent.saved(dto.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onOffShelf(Long id, Integer status) {
        if (id == null || status == null || (status != 0 && status != 1)) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        }
        Spu old = spuMapper.getById(id);
        if (old == null) throw new BaseException(MessageConstant.SPU_NOT_FOUND);
        if ((old.getStatus() == null ? 0 : old.getStatus()) == status) return;

        Spu up = new Spu();
        up.setId(id);
        up.setStatus(status);
        if (status == 1) up.setOnShelfTime(LocalDateTime.now());
        else up.setOffShelfTime(LocalDateTime.now());
        spuMapper.updateStatusAndTime(up);

        // ===== [RAG] 变更入 Outbox（同事务） =====
        ragSyncService.enqueueSpuSync(id, status == 1 ? "ONSHELF" : "OFFSHELF");
        // ===== [ES] 发布同步事件 =====
        eventPublisher.publishEvent(SpuChangedEvent.saved(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchOnOffShelf(List<Long> ids, Integer status) {
        if (ids == null || ids.isEmpty() || status == null || (status != 0 && status != 1)) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        }
        for (Long id : ids) {
            onOffShelf(id, status);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "spu:detail", key = "#id")
    public void deleteById(Long id) {
        if (id == null) return;
        Spu old = spuMapper.getById(id);
        if (old == null) return;
        skuMapper.deleteBySpuId(id);
        spuMapper.deleteById(id);

        // ===== [RAG] 变更入 Outbox（同事务） =====
        ragSyncService.enqueueSpuSync(id, "DELETE");
        // ===== [ES] 发布同步事件 =====
        eventPublisher.publishEvent(SpuChangedEvent.deleted(id));
    }

    @Override
    public PageResult pageQuery(Integer page, Integer pageSize, String name,
                                Long categoryId, Long category2Id,
                                Integer status, Integer isNew, Integer isHot,
                                BigDecimal priceFrom, BigDecimal priceTo) {
        int p = (page == null || page < 1) ? 1 : page;
        int ps = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        List<Spu> list = spuMapper.page((p - 1) * ps, ps, name,
                categoryId, category2Id, status, isNew, isHot, priceFrom, priceTo,
                null, null);
        long total = spuMapper.count(name, categoryId, category2Id,
                status, isNew, isHot, priceFrom, priceTo);
        return new PageResult(total, list == null ? new ArrayList<>() : list);
    }

    /** DTO 版本分页：带 sortBy/sortOrder（白名单排序，Mapper XML 内部二次校验）。 */
    @Override
    public PageResult pageQueryByDto(SpuPageQueryDTO dto) {
        if (dto == null) dto = new SpuPageQueryDTO();
        int p = (dto.getPage() == null || dto.getPage() < 1) ? 1 : dto.getPage();
        int ps = (dto.getPageSize() == null || dto.getPageSize() < 1) ? 10 : dto.getPageSize();
        List<Spu> list = spuMapper.page((p - 1) * ps, ps,
                dto.getName(), dto.getCategoryId(), dto.getCategory2Id(),
                dto.getStatus(), dto.getIsNew(), dto.getIsHot(),
                dto.getPriceFrom(), dto.getPriceTo(),
                dto.getSortBy(), dto.getSortOrder());
        long total = spuMapper.count(dto.getName(), dto.getCategoryId(), dto.getCategory2Id(),
                dto.getStatus(), dto.getIsNew(), dto.getIsHot(),
                dto.getPriceFrom(), dto.getPriceTo());
        return new PageResult(total, list == null ? new ArrayList<>() : list);
    }

    // ================= 内部：校验分类归属 =================
    /**
     * 校验 category2Id（二级分类）的 parentId 是否等于 categoryId（一级分类），
     * 防止商品被错误地挂到不属于的父分类下。
     */
    private void validateCategoryRelation(Long categoryId, Long category2Id) {
        if (category2Id == null) return; // 未设二级分类，不校验
        Category subCategory = categoryMapper.getById(category2Id);
        if (subCategory == null) {
            throw new BaseException("二级分类不存在（id=" + category2Id + "）");
        }
        if (subCategory.getLevel() == null || subCategory.getLevel() != 2) {
            throw new BaseException("category2Id 必须指向二级分类（id=" + category2Id + "）");
        }
        if (subCategory.getParentId() == null || !subCategory.getParentId().equals(categoryId)) {
            throw new BaseException("二级分类（" + subCategory.getName() + "）不属于所选的一级分类");
        }
    }

    // ================= 内部：聚合 SKU 回写 SPU =================
    private void refreshAggregates(Long spuId) {
        List<Sku> skus = skuMapper.listBySpuId(spuId);
        if (skus == null || skus.isEmpty()) {
            spuMapper.refreshAggregatesFromSku(spuId, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
            return;
        }
        int count = 0;
        int total = 0;
        BigDecimal min = null;
        BigDecimal max = null;
        for (Sku s : skus) {
            count++;
            if (s.getStock() != null) total += s.getStock();
            BigDecimal pr = s.getPrice();
            if (pr != null) {
                if (min == null || pr.compareTo(min) < 0) min = pr;
                if (max == null || pr.compareTo(max) > 0) max = pr;
            }
        }
        if (min == null) min = BigDecimal.ZERO;
        if (max == null) max = BigDecimal.ZERO;
        spuMapper.refreshAggregatesFromSku(spuId, min, max, total, count);
    }
}
