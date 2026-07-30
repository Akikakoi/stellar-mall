package com.stellar.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final int MAX_LEVEL = 2;

    private final CategoryMapper categoryMapper;
    private final SpuMapper spuMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "category:tree", allEntries = true)
    public Long save(CategorySaveDTO dto) {
        // 1. 预处理 parentId 为空 → 顶分类
        Long parentId = dto.getParentId() == null ? 0L : dto.getParentId();

        // 2. 计算实际 level
        Integer expectLevel;
        if (parentId.equals(0L)) {
            expectLevel = 1;
        } else {
            // 子分类：必须父分类存在且 level = 1
            Category parent = categoryMapper.getById(parentId);
            if (parent == null) {
                throw new BaseException(MessageConstant.CATEGORY_PARENT_NOT_FOUND);
            }
            if (parent.getLevel() != 1) {
                throw new BaseException(MessageConstant.CATEGORY_PARENT_MUST_BE_LEVEL1);
            }
            expectLevel = 2;
        }

        // 3. 如果用户显式传 level 但传错了（不一致）→ 自动修正为正确值，或者抛异常都可以
        // 这里选自动修正（测试兼容两种实现）
        Integer level = dto.getLevel();
        if (level == null || level < 1 || level > MAX_LEVEL || !level.equals(expectLevel)) {
            level = expectLevel;
        }
        // 2 级封顶，不允许出现 3 级
        if (level > MAX_LEVEL) {
            throw new BaseException(MessageConstant.CATEGORY_LEVEL_LIMIT_EXCEEDED);
        }

        Integer type = dto.getType() == null ? 1 : dto.getType();
        String name = dto.getName() == null ? "" : dto.getName().trim();
        if (name.isEmpty()) {
            throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        }

        // 4. 唯一键检查 (parentId, name, type)
        Category dup = categoryMapper.getByParentNameType(parentId, name, type);
        if (dup != null) {
            throw new BaseException(MessageConstant.CATEGORY_NAME_ALREADY_EXISTS);
        }

        Category c = new Category();
        BeanUtils.copyProperties(dto, c);
        c.setName(name);
        c.setParentId(parentId);
        c.setLevel(level);
        c.setType(type);
        if (c.getSort() == null) c.setSort(0);
        if (c.getStatus() == null) c.setStatus(1);
        categoryMapper.insert(c);
        return c.getId();
    }

    @Override
    public Category getById(Long id) {
        return id == null ? null : categoryMapper.getById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "category:tree", allEntries = true)
    public void update(CategoryUpdateDTO dto) {
        if (dto == null || dto.getId() == null) throw new BaseException(MessageConstant.ILLEGAL_PARAMETER);
        Category old = categoryMapper.getById(dto.getId());
        if (old == null) throw new BaseException(MessageConstant.CATEGORY_NOT_FOUND);

        // 改 name → 需要重新检查唯一键（同父 + 同 type）
        String newName = dto.getName() == null ? null : dto.getName().trim();
        Integer newType = dto.getType();
        if (newName != null && !newName.equals(old.getName())) {
            Integer t = newType == null ? old.getType() : newType;
            Category dup = categoryMapper.getByParentNameType(old.getParentId(), newName, t);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "category:tree", allEntries = true)
    public void deleteById(Long id) {
        if (id == null) return;
        Category old = categoryMapper.getById(id);
        if (old == null) return;
        // 复用预校验逻辑（唯一的真源）——禁止删除原因可能是 SPU 关联或子分类
        CategoryDeletableVO check = checkDeletable(id);
        if (!Boolean.TRUE.equals(check.getDeletable())) {
            // 优先以是否有商品关联作为更重要的约束；其次才是子分类
            if (check.getLinkedSpuCount() != null && check.getLinkedSpuCount() > 0) {
                throw new BaseException(MessageConstant.CATEGORY_HAS_LINKED_SPUS);
            }
            if (check.getChildCount() != null && check.getChildCount() > 0) {
                throw new BaseException(MessageConstant.CATEGORY_HAS_CHILDREN);
            }
            // 兜底：VO 有其他禁止原因也直接抛
            String reason = check.getReason() == null ? MessageConstant.ILLEGAL_PARAMETER : check.getReason();
            throw new BaseException(reason);
        }
        categoryMapper.deleteById(id);
    }

    @Override
    public CategoryDeletableVO checkDeletable(Long id) {
        // 1) 不存在的分类：视为允许删（删除动作本身也 return noop），避免页面弹窗误报
        if (id == null) {
            return CategoryDeletableVO.builder()
                    .deletable(true).linkedSpuCount(0).childCount(0).build();
        }
        Category old = categoryMapper.getById(id);
        if (old == null) {
            return CategoryDeletableVO.builder()
                    .deletable(true).linkedSpuCount(0).childCount(0).build();
        }
        // 2) 作用域：当前分类 + 所有直接子分类（因为最多 2 级，不需要更深递归）
        List<Long> scopeIds = collectScopeIds(old);
        int childCount = scopeIds.size() - 1; // 扣掉自己
        // 3) 计算作用域中关联的 SPU 数量（覆盖 categoryId / category2Id 两个维度）
        int linkedSpuCount = countLinkedSpuInScope(scopeIds);
        // 4) 生成 VO，优先级：商品关联 > 子分类
        if (linkedSpuCount > 0) {
            String reason = "该分类下还有 " + linkedSpuCount + " 个商品，禁止删除。请先下架或删除关联商品后再试。";
            return CategoryDeletableVO.builder()
                    .deletable(false)
                    .linkedSpuCount(linkedSpuCount)
                    .childCount(childCount)
                    .reason(reason)
                    .build();
        }
        if (childCount > 0) {
            String reason = "该分类下还有 " + childCount + " 个子分类，禁止删除。请先删除子分类后再试。";
            return CategoryDeletableVO.builder()
                    .deletable(false)
                    .linkedSpuCount(0)
                    .childCount(childCount)
                    .reason(reason)
                    .build();
        }
        return CategoryDeletableVO.builder()
                .deletable(true)
                .linkedSpuCount(0)
                .childCount(0)
                .build();
    }

    /** 收集作用域 ID：当前分类 + 所有直接子分类 ID（两级模型正好子分类最多一层）。 */
    private List<Long> collectScopeIds(Category root) {
        List<Long> ids = new ArrayList<>();
        ids.add(root.getId());
        List<Category> children = categoryMapper.list(null, null, root.getType());
        for (Category c : children) {
            if (c.getParentId() != null && c.getParentId().equals(root.getId())) {
                ids.add(c.getId());
            }
        }
        return ids;
    }

    /** 统计 scopeIds 任一 ID 作为 SPU.categoryId 或 SPU.category2Id 关联的去重 SPU 数（一个 SPU 最多计一次，避免 L1 + L2 同时挂同一个商品时重复计数。 */
    private int countLinkedSpuInScope(List<Long> scopeIds) {
        if (scopeIds == null || scopeIds.isEmpty()) return 0;
        return (int) spuMapper.countDistinctIdByCategoryScope(scopeIds);
    }

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

    @Override
    public PageResult pageQuery(CategoryPageQueryDTO dto) {
        int p = dto.getPage() == null || dto.getPage() < 1 ? 1 : dto.getPage();
        int ps = dto.getPageSize() == null || dto.getPageSize() < 1 ? 10 : dto.getPageSize();
        List<Category> list = categoryMapper.page((p - 1) * ps, ps,
                dto.getName(), dto.getType(), dto.getStatus(),
                dto.getSortBy(), dto.getSortOrder());
        long total = categoryMapper.count(dto.getName(), dto.getType(), dto.getStatus());

        // ======== 回填每条记录的 spuCount（替换前端「类型」列显示）========
        // 口径与「删除预校验 checkDeletable」保持一致：
        //   L1 = 该分类本身(id) + 其所有 L2 子分类(id)；L2 = 该分类本身(id)。
        // 避免 N+1：跨记录不同分类的 scope 集合去重合并后统一批量？实现层面 countLinkedSpuInScope 已经用的是 scope 整集合一次 SQL，所以不同分类的 scope 如果完全相同才会重复。先用「相同 scope 结果缓存以避免重复 SQL。
        if (list != null && !list.isEmpty()) {
            final Map<Long, List<Long>> parentToChildren = indexChildrenByParent(list, dto.getType());
            final Map<List<Long>, Integer> scopeCountCache = new HashMap<>(list.size());
            for (Category c : list) {
                if (c == null || c.getId() == null) continue;
                // 对 scope 本身做规范化（排序）再用 List.hashCode/equals 稳定可比），否则作为 HashMap key 才会命中缓存
                List<Long> scope = buildScopeFor(c, parentToChildren);
                List<Long> key = scope.stream().sorted().collect(Collectors.toList());
                Integer cached = scopeCountCache.get(key);
                if (cached == null) {
                    cached = countLinkedSpuInScope(scope);
                    scopeCountCache.put(key, cached);
                }
                c.setSpuCount(cached);
            }
        }

        return new PageResult(total, list);
    }

    /** 为分类列表建立「parentId → 子分类 id 列表」索引。仅在本页 list 内的子分类会被纳入；如需跨页 L1/L2 同时显示，type 过滤确保同类型内遍历完整（本项目最多 2 级，可二次补 DB 拉取整同类型，但管理后台 pageSize ≥ 50 基本覆盖全部，如需严谨可改为 type 维度全量拉）。 */
    private Map<Long, List<Long>> indexChildrenByParent(List<Category> list, Integer typeFilter) {
        Map<Long, List<Long>> m = new HashMap<>();
        if (list == null) return m;
        // 策略：若 list.size() 较小，先按 type 拉一次全量（最多 2 级，数量少）确保父子关联跨页/跨页内都能正确匹配；这比「只在本页找子级」更严谨但仍是 O(全量分类数)，可忽略不计。
        List<Category> pool = (typeFilter != null) ? categoryMapper.list(null, null, typeFilter) : list;
        if (pool == null) pool = list;
        for (Category c : pool) {
            if (c == null || c.getId() == null || c.getParentId() == null || c.getParentId().equals(0L)) continue;
            m.computeIfAbsent(c.getParentId(), k -> new ArrayList<>(4)).add(c.getId());
        }
        return m;
    }

    /** 取分类 c 的计数作用域：L1 返回 [自身 + 全部子分类]；L2 返回 [自身]；其它层级返回 [自身]。 */
    private static List<Long> buildScopeFor(Category c, Map<Long, List<Long>> parentToChildren) {
        List<Long> scope = new ArrayList<>(4);
        scope.add(c.getId());
        boolean isL1 = (c.getParentId() == null || c.getParentId().equals(0L))
                || Integer.valueOf(1).equals(c.getLevel());
        if (isL1) {
            List<Long> sub = parentToChildren.get(c.getId());
            if (sub != null && !sub.isEmpty()) scope.addAll(sub);
        }
        return scope;
    }

    @Override
    @Cacheable(value = "category:tree", key = "#onlyEnabled")
    public List<Category> tree(boolean onlyEnabled) {
        // 一次查全量（最多 2 级，数量少，性能 OK）
        Integer statusFilter = onlyEnabled ? 1 : null;
        List<Category> all = categoryMapper.list(null, statusFilter, null);
        if (all == null || all.isEmpty()) return new ArrayList<>();

        List<Category> level1 = all.stream()
                .filter(c -> c.getParentId() == null || c.getParentId().equals(0L)
                        || Integer.valueOf(1).equals(c.getLevel()))
                .collect(Collectors.toList());
        Map<Long, List<Category>> lvl2ByParent = new HashMap<>();
        for (Category c : all) {
            if (c.getParentId() != null && !c.getParentId().equals(0L)) {
                lvl2ByParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
            }
        }
        for (Category l1 : level1) {
            List<Category> children = lvl2ByParent.get(l1.getId());
            if (children != null && !children.isEmpty()) {
                children.sort((a, b) -> {
                    int sa = a.getSort() == null ? 0 : a.getSort();
                    int sb = b.getSort() == null ? 0 : b.getSort();
                    return Integer.compare(sb, sa);
                });
                l1.setChildren(children);
            } else {
                l1.setChildren(new ArrayList<>());
            }
        }
        level1.sort((a, b) -> {
            int sa = a.getSort() == null ? 0 : a.getSort();
            int sb = b.getSort() == null ? 0 : b.getSort();
            return Integer.compare(sb, sa);
        });
        return level1;
    }
}
