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
import com.stellar.service.SiteConfigService;
import com.stellar.service.SpuService;
import com.stellar.vo.OrbitSpuVO;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    /** 前端最近展示列表最多排除的商品数（防止超长 IN 列表拖慢随机 SQL） */
    private static final int MAX_ORBIT_EXCLUDE = 100;

    private final SpuMapper spuMapper;
    private final SkuMapper skuMapper;
    private final RagSyncService ragSyncService;
    private final ApplicationEventPublisher eventPublisher;
    private final SiteConfigService siteConfigService;

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
     * 随机抽取上架且有主图的 SPU（首页首屏环绕商品用）。
     *
     * @param count 抽取数量，限制在 1~20，避免恶意大数量拖慢 ORDER BY RAND()
     * @return 随机商品列表
     */
    @Override
    public List<Spu> listRandom(int count) {
        int n = Math.max(1, Math.min(count, 20));
        List<Spu> list = spuMapper.listRandom(n);
        return list == null ? new ArrayList<>() : list;
    }

    /**
     * 首页首屏环绕商品：管理端候选池优先，未配置或候选全部失效时随机兜底。
     * <p>
     * 合并「读候选池配置 + 拉候选详情 / 随机抽取」为一次调用，并且只返回
     * id/name/mainImage 三个卡片字段，避免首屏为 8 张卡片发起 2~9 个请求、
     * 或把 SPU 描述/SKU 列表整包下发。
     * </p>
     * <p>
     * 近期去重：{@code excludeIds} 为前端记录的最近展示过的商品，优先从没出现过的商品里抽；
     * 候选不足时放宽排除补足，保证环上卡片数量不缩水。
     * </p>
     *
     * @param count      展示数量，内部限制在 1~20
     * @param excludeIds 最近展示过的商品 id（可为空）
     * @return 轻量卡片数据（候选池有效商品不足时按实际数量返回）
     */
    @Override
    public List<OrbitSpuVO> listOrbit(int count, List<Long> excludeIds) {
        int limit = Math.max(1, Math.min(count, 20));
        List<Long> exclude = sanitizeExcludeIds(excludeIds);

        // 1) 管理端候选池优先：池内优先换新，不够再用池内「近期展示过」的商品补足
        List<Spu> pool = readConfiguredCandidates(exclude);
        // 2) 未配置候选池 / 候选全部失效：从全库随机，先排除最近展示过的
        if (pool.isEmpty()) {
            pool = pickRandomCandidates(limit, exclude);
        }

        List<OrbitSpuVO> result = new ArrayList<>(Math.min(limit, pool.size()));
        for (Spu spu : pool) {
            if (result.size() >= limit) break;
            result.add(OrbitSpuVO.builder()
                    .id(spu.getId())
                    .name(spu.getName())
                    .mainImage(spu.getMainImage())
                    .build());
        }
        return result;
    }

    /**
     * 排除列表清洗：过滤空值/非正数、去重、限制最大数量，避免超长 IN 列表。
     */
    private List<Long> sanitizeExcludeIds(List<Long> excludeIds) {
        if (excludeIds == null || excludeIds.isEmpty()) return List.of();
        return excludeIds.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .limit(MAX_ORBIT_EXCLUDE)
                .toList();
    }

    /**
     * 读管理端候选池并过滤下架/无主图商品；池内最近展示过的商品排到后面，
     * 保证候选池足够大时每次优先看到新商品。
     */
    private List<Spu> readConfiguredCandidates(List<Long> exclude) {
        List<Long> candidateIds = siteConfigService.getHomeOrbitSpuIds();
        if (candidateIds == null || candidateIds.isEmpty()) return new ArrayList<>();
        List<Spu> configured = spuMapper.listByIds(candidateIds);
        if (configured == null) return new ArrayList<>();

        Set<Long> excludeSet = new HashSet<>(exclude);
        List<Spu> fresh = new ArrayList<>();
        List<Spu> recent = new ArrayList<>();
        for (Spu spu : configured) {
            // 候选池可能包含已下架/无主图的商品，过滤后再随机，保证每张卡都能出图
            if (spu == null || !Integer.valueOf(1).equals(spu.getStatus())) continue;
            if (spu.getMainImage() == null || spu.getMainImage().isBlank()) continue;
            (excludeSet.contains(spu.getId()) ? recent : fresh).add(spu);
        }
        Collections.shuffle(fresh);
        Collections.shuffle(recent);
        fresh.addAll(recent);
        return fresh;
    }

    /**
     * 全库随机抽取：先排除最近展示过的商品；排除后候选不足时放宽条件补足，
     * 避免商品库较小或历史过多时环上卡片变少。
     */
    private List<Spu> pickRandomCandidates(int limit, List<Long> exclude) {
        List<Spu> picked = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        List<Spu> firstBatch = exclude.isEmpty()
                ? spuMapper.listRandom(limit)
                : spuMapper.listRandomExcluding(limit, exclude);
        addUnique(picked, seen, firstBatch, limit);

        for (int attempt = 0; picked.size() < limit && attempt < 3; attempt++) {
            int ask = Math.max(limit - picked.size(), 1) * (attempt + 2);
            List<Spu> extra = spuMapper.listRandom(ask);
            if (extra == null || extra.isEmpty()) break;
            addUnique(picked, seen, extra, limit);
        }
        return picked;
    }

    /** 按 id 去重地把 source 追加到 target，直到达到 limit。 */
    private void addUnique(List<Spu> target, Set<Long> seen, List<Spu> source, int limit) {
        if (source == null) return;
        for (Spu spu : source) {
            if (target.size() >= limit) return;
            if (spu == null || spu.getId() == null) continue;
            if (seen.add(spu.getId())) target.add(spu);
        }
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
     * 批量上架/下架操作。
     * <p>
     * 与逐条调用 {@link #onOffShelf(Long, Integer)} 的区别：一次查出全部目标 SPU、
     * 过滤出状态确实需要变化的 id，再走单条批量 UPDATE（避免 N 次 getById + N 次 update 的 N+1）；
     * RAG Outbox 与 ES 同步事件只对实际发生变化的 SPU 逐条触发（保持与单条路径一致的同步语义）。
     * 已不存在的 id 静默跳过（单条路径会抛 SPU_NOT_FOUND，批量场景跳过更合理）。
     * </p>
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
        List<Spu> existing = spuMapper.listByIds(ids);
        List<Long> toChange = new ArrayList<>(existing.size());
        for (Spu s : existing) {
            if ((s.getStatus() == null ? 0 : s.getStatus()) != status) {
                toChange.add(s.getId());
            }
        }
        if (toChange.isEmpty()) return;

        spuMapper.updateStatusBatch(toChange, status);
        String syncType = status == 1 ? "ONSHELF" : "OFFSHELF";
        for (Long id : toChange) {
            // ===== [RAG] 变更入 Outbox（同事务） =====
            ragSyncService.enqueueSpuSync(id, syncType);
            // ===== [ES] 发布同步事件 =====
            eventPublisher.publishEvent(SpuChangedEvent.saved(id));
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
