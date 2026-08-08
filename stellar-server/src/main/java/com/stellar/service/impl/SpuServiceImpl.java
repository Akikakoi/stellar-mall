package com.stellar.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.stellar.constant.MessageConstant;
import com.stellar.dto.SpuPageQueryDTO;
import com.stellar.dto.SpuSaveDTO;
import com.stellar.entity.Sku;
import com.stellar.entity.Spu;
import com.stellar.exception.BaseException;
import com.stellar.elasticsearch.event.SpuChangedEvent;
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

/**
 * SPU（标准产品单元）服务实现类。
 * <p>
 * 提供 SPU 的增删改查、上下架、批量上下架、分页查询、SKU 聚合刷新等核心功能。
 * 每个 SPU 下可嵌套多个 SKU，保存/更新时支持自动生成默认 SKU。
 * 变更操作会同步触发 RAG 索引队列和 Elasticsearch 同步事件。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpuServiceImpl implements SpuService {

    private final SpuMapper spuMapper;
    private final SkuMapper skuMapper;
    private final RagSyncService ragSyncService;
    private final ApplicationEventPublisher eventPublisher;

    // ================= 保存 =================

    /**
     * 保存 SPU 及其嵌套的 SKU 列表。
     * <p>
     * 若前端未传 skuList 但传了 price，会自动生成一条默认 SKU；保存完成后从 SKU 聚合
     * 价格、库存等数据回写 SPU，并触发 RAG 同步和 ES 事件。
     * </p>
     *
     * @param dto SPU 保存请求
     * @return 新创建的 SPU ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "spu:detail", key = "#result")
    public Long saveWithSkus(SpuSaveDTO dto) {
        if (dto == null) throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        if (dto.getCategoryId() == null) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        }

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

    /**
     * 根据 ID 查询 SPU 及其关联的 SKU 列表（按 sort 排序），结果会被缓存。
     *
     * @param id SPU ID
     * @return SPU 实体（含 SKU 列表），不存在则返回 null
     */
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

    /**
     * 更新 SPU 及其嵌套的 SKU 列表（先删后插覆盖式更新）。
     * <p>
     * 若前端未传 skuList 但传了 price，会自动生成一条默认 SKU 覆盖原有 SKU；
     * 若 skuList 为空且 price 也为空，则保留原有 SKU 不变。
     * 更新完成后会清除缓存、刷新聚合数据并触发 RAG/ES 同步。
     * </p>
     *
     * @param dto SPU 更新请求（必须包含 id）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "spu:detail", key = "#dto.id")
    public void updateWithSkus(SpuSaveDTO dto) {
        if (dto == null || dto.getId() == null) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
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

    /**
     * 单个 SPU 上架/下架操作。
     * <p>
     * 上架时记录上架时间，下架时记录下架时间，并触发 RAG 同步和 ES 事件。
     * 若当前状态与目标状态一致则直接返回，不做重复操作。
     * </p>
     *
     * @param id     SPU ID
     * @param status 目标状态：1-上架，0-下架
     */
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

    /**
     * 批量上架/下架操作，遍历调用 {@link #onOffShelf(Long, Integer)}。
     *
     * @param ids    SPU ID 列表
     * @param status 目标状态：1-上架，0-下架
     */
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

    /**
     * 删除 SPU 及其关联的所有 SKU，清除缓存并触发 RAG/ES 删除同步。
     *
     * @param id SPU ID
     */
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

    /**
     * 多条件分页查询 SPU 列表。
     *
     * @param page        页码（从 1 开始）
     * @param pageSize    每页大小
     * @param name        商品名称（模糊匹配）
     * @param categoryId  分类 ID
     * @param status      状态：1-上架，0-下架
     * @param isNew       是否新品
     * @param isHot       是否热销
     * @param priceFrom   价格区间下限
     * @param priceTo     价格区间上限
     * @return 分页结果
     */
    @Override
    public PageResult pageQuery(Integer page, Integer pageSize, String name,
                                Long categoryId,
                                Integer status, Integer isNew, Integer isHot,
                                BigDecimal priceFrom, BigDecimal priceTo) {
        int p = (page == null || page < 1) ? 1 : page;
        int ps = (pageSize == null || pageSize < 1) ? 10 : pageSize;
        List<Spu> list = spuMapper.page((p - 1) * ps, ps, name,
                categoryId, status, isNew, isHot, priceFrom, priceTo,
                null, null);
        long total = spuMapper.count(name, categoryId,
                status, isNew, isHot, priceFrom, priceTo);
        return new PageResult(total, list == null ? new ArrayList<>() : list);
    }

    /**
     * DTO 版本分页查询，支持白名单排序（sortBy/sortOrder 由 Mapper XML 内部二次校验）。
     *
     * @param dto 分页查询参数
     * @return 分页结果
     */
    @Override
    public PageResult pageQueryByDto(SpuPageQueryDTO dto) {
        if (dto == null) dto = new SpuPageQueryDTO();
        int p = (dto.getPage() == null || dto.getPage() < 1) ? 1 : dto.getPage();
        int ps = (dto.getPageSize() == null || dto.getPageSize() < 1) ? 10 : dto.getPageSize();
        List<Spu> list = spuMapper.page((p - 1) * ps, ps,
                dto.getName(), dto.getCategoryId(),
                dto.getStatus(), dto.getIsNew(), dto.getIsHot(),
                dto.getPriceFrom(), dto.getPriceTo(),
                dto.getSortBy(), dto.getSortOrder());
        long total = spuMapper.count(dto.getName(), dto.getCategoryId(),
                dto.getStatus(), dto.getIsNew(), dto.getIsHot(),
                dto.getPriceFrom(), dto.getPriceTo());
        return new PageResult(total, list == null ? new ArrayList<>() : list);
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
