package com.stellar.service.impl;

import com.stellar.constant.MessageConstant;
import com.stellar.dto.CategoryPageQueryDTO;
import com.stellar.dto.CategorySaveDTO;
import com.stellar.dto.CategoryUpdateDTO;
import com.stellar.entity.Category;
import com.stellar.exception.BaseException;
import com.stellar.mapper.CategoryMapper;
import com.stellar.mapper.SpuMapper;
import com.stellar.result.PageResult;
import com.stellar.service.CategoryService;
import com.stellar.vo.CategoryDeletableVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类服务实现类。
 * <p>
 * 提供分类的增删改查、启停、列表查询及删除预校验等功能。
 * 所有分类均为平级，操作时会同步清理缓存。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;
    private final SpuMapper spuMapper;

    /**
     * 新增分类。
     * <p>
     * 直接创建分类，校验唯一键约束。
     * </p>
     *
     * @param dto 分类保存请求
     * @return 新增分类ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "category:tree", allEntries = true)
    public Long save(CategorySaveDTO dto) {
        Integer type = dto.getType() == null ? 1 : dto.getType();
        String name = dto.getName() == null ? "" : dto.getName().trim();
        if (name.isEmpty()) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        }

        // 唯一键检查 (name, type)
        Category dup = categoryMapper.getByNameType(name, type);
        if (dup != null) {
            throw new BaseException(MessageConstant.CATEGORY_NAME_ALREADY_EXISTS);
        }

        Category c = new Category();
        BeanUtils.copyProperties(dto, c);
        c.setName(name);
        c.setLevel(1);
        c.setType(type);
        if (c.getSort() == null) c.setSort(0);
        if (c.getStatus() == null) c.setStatus(1);
        categoryMapper.insert(c);
        return c.getId();
    }

    /**
     * 根据ID查询分类。
     *
     * @param id 分类ID
     * @return 分类实体，不存在时返回null
     */
    @Override
    public Category getById(Long id) {
        return id == null ? null : categoryMapper.getById(id);
    }

    /**
     * 更新分类。
     * <p>
     * 修改名称时会重新校验唯一键约束（同类型下名称不可重复）。
     * </p>
     *
     * @param dto 分类更新请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "category:tree", allEntries = true)
    public void update(CategoryUpdateDTO dto) {
        if (dto == null || dto.getId() == null) throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        Category old = categoryMapper.getById(dto.getId());
        if (old == null) throw new BaseException(MessageConstant.CATEGORY_NOT_FOUND);

        // 改 name → 需要重新检查唯一键（同 type）
        String newName = dto.getName() == null ? null : dto.getName().trim();
        Integer newType = dto.getType();
        if (newName != null && !newName.equals(old.getName())) {
            Integer t = newType == null ? old.getType() : newType;
            Category dup = categoryMapper.getByNameType(newName, t);
            if (dup != null && !dup.getId().equals(old.getId())) {
                throw new BaseException(MessageConstant.CATEGORY_NAME_ALREADY_EXISTS);
            }
        }

        Category update = new Category();
        BeanUtils.copyProperties(dto, update);
        update.setId(dto.getId());
        if (newName != null) update.setName(newName);
        categoryMapper.update(update);
    }

    /**
     * 删除分类。
     * <p>
     * 删除前会校验是否可删（有关联商品时禁止删除）。
     * </p>
     *
     * @param id 分类ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "category:tree", allEntries = true)
    public void deleteById(Long id) {
        if (id == null) return;
        Category old = categoryMapper.getById(id);
        if (old == null) return;
        // 复用预校验逻辑
        CategoryDeletableVO check = checkDeletable(id);
        if (!Boolean.TRUE.equals(check.getDeletable())) {
            if (check.getLinkedSpuCount() != null && check.getLinkedSpuCount() > 0) {
                throw new BaseException(MessageConstant.CATEGORY_HAS_LINKED_SPUS);
            }
            String reason = check.getReason() == null ? MessageConstant.ILLEGAL_PARAMETER : check.getReason();
            throw new BaseException(reason);
        }
        categoryMapper.deleteById(id);
    }

    /**
     * 预校验分类是否可删除。
     * <p>
     * 检查该分类下是否有关联商品。
     * </p>
     *
     * @param id 分类ID
     * @return 可删除性校验结果VO
     */
    @Override
    public CategoryDeletableVO checkDeletable(Long id) {
        if (id == null) {
            return CategoryDeletableVO.builder()
                    .deletable(true).linkedSpuCount(0).childCount(0).build();
        }
        Category old = categoryMapper.getById(id);
        if (old == null) {
            return CategoryDeletableVO.builder()
                    .deletable(true).linkedSpuCount(0).childCount(0).build();
        }
        // 只检查该分类本身是否有关联商品
        int linkedSpuCount = countLinkedSpuInScope(List.of(id));
        if (linkedSpuCount > 0) {
            String reason = "该分类下还有 " + linkedSpuCount + " 个商品，禁止删除。请先下架或删除关联商品后再试。";
            return CategoryDeletableVO.builder()
                    .deletable(false)
                    .linkedSpuCount(linkedSpuCount)
                    .childCount(0)
                    .reason(reason)
                    .build();
        }
        return CategoryDeletableVO.builder()
                .deletable(true)
                .linkedSpuCount(0)
                .childCount(0)
                .build();
    }

    /** 统计 scopeIds 中任一 ID 作为 SPU.categoryId 关联的去重 SPU 数。 */
    private int countLinkedSpuInScope(List<Long> scopeIds) {
        if (scopeIds == null || scopeIds.isEmpty()) return 0;
        return (int) spuMapper.countDistinctIdByCategoryScope(scopeIds);
    }

    /**
     * 启用或停用分类。
     *
     * @param id     分类ID
     * @param status 状态（0=停用，1=启用）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "category:tree", allEntries = true)
    public void startOrStop(Long id, Integer status) {
        if (id == null || status == null || (status != 0 && status != 1)) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        }
        Category old = categoryMapper.getById(id);
        if (old == null) throw new BaseException(MessageConstant.CATEGORY_NOT_FOUND);
        if (old.getStatus().equals(status)) return;
        Category up = new Category();
        up.setId(id);
        up.setStatus(status);
        categoryMapper.update(up);
    }

    /**
     * 分页查询分类列表。
     * <p>
     * 支持按名称、类型、状态筛选，并回填每条记录的关联商品数量。
     * </p>
     *
     * @param dto 分页查询条件
     * @return 分页结果
     */
    @Override
    public PageResult pageQuery(CategoryPageQueryDTO dto) {
        int p = dto.getPage() == null || dto.getPage() < 1 ? 1 : dto.getPage();
        int ps = dto.getPageSize() == null || dto.getPageSize() < 1 ? 10 : dto.getPageSize();
        List<Category> list = categoryMapper.page((p - 1) * ps, ps,
                dto.getName(), dto.getType(), dto.getStatus(),
                dto.getSortBy(), dto.getSortOrder());
        long total = categoryMapper.count(dto.getName(), dto.getType(), dto.getStatus());

        // 回填每条记录的 spuCount
        if (list != null && !list.isEmpty()) {
            for (Category c : list) {
                if (c == null || c.getId() == null) continue;
                c.setSpuCount(countLinkedSpuInScope(List.of(c.getId())));
            }
        }

        return new PageResult(total, list);
    }

    /**
     * 查询所有分类的平铺列表。
     * <p>
     * 一次查询全量分类，返回平铺列表，结果会被缓存。
     * </p>
     *
     * @param onlyEnabled 是否仅返回启用状态的分类
     * @return 分类平铺列表
     */
    @Override
    @Cacheable(value = "category:tree", key = "#onlyEnabled")
    public List<Category> tree(boolean onlyEnabled) {
        Integer statusFilter = onlyEnabled ? 1 : null;
        List<Category> all = categoryMapper.list(statusFilter, null);
        if (all == null || all.isEmpty()) return new ArrayList<>();
        return all;
    }
}