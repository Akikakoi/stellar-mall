package com.stellar.product;

import com.stellar.constant.MessageConstant;
import com.stellar.dto.CategoryPageQueryDTO;
import com.stellar.dto.CategorySaveDTO;
import com.stellar.dto.CategoryUpdateDTO;
import com.stellar.dto.SpuSaveDTO;
import com.stellar.entity.Category;
import com.stellar.exception.BaseException;
import com.stellar.result.PageResult;
import com.stellar.service.CategoryService;
import com.stellar.service.SpuService;
import com.stellar.vo.CategoryDeletableVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-M1 RED阶段：分类 Category 全链路 + 2 级限制。
 * 本测试走 Service 真实签名（save(CategorySaveDTO) / update(CategoryUpdateDTO) / pageQuery(CategoryPageQueryDTO)）。
 * ⚠️ 类级别 @Transactional：每个 @Test 方法结束后自动回滚，保证测试幂等。
 */
@SpringBootTest
@Transactional
class CategoryServiceTest {

    @Autowired(required = false)
    private CategoryService categoryService;

    /** 每个测试名加唯一纳秒后缀，避免和 P3 样例/脏数据重名抛同名冲突 */
    private static String uid(String prefix) {
        return prefix + "-UT-" + Integer.toHexString(
                (int) ((System.nanoTime() ^ System.identityHashCode(Thread.currentThread())) & 0xffff));
    }

    private static CategorySaveDTO toSaveDto(Category c) {
        if (c == null) return null;
        CategorySaveDTO dto = new CategorySaveDTO();
        BeanUtils.copyProperties(c, dto);
        return dto;
    }

    @Test
    void saveCategory_thenGetById() {
        assertNotNull(categoryService, "RED失败：CategoryService 未注册");
        Category top = new Category();
        top.setName(uid("大家电"));
        top.setParentId(0L);
        top.setLevel(1);
        top.setSort(1);
        top.setStatus(1);
        Long id = categoryService.save(toSaveDto(top));
        assertNotNull(id);

        Category got = categoryService.getById(id);
        assertTrue(got.getName().startsWith("大家电"));
        assertEquals(Long.valueOf(0L), got.getParentId());
        assertEquals(Integer.valueOf(1), got.getLevel());
    }

    @Test
    void saveLevel3Category_throwsException_max2Levels() {
        assertNotNull(categoryService);
        Category l1 = new Category();
        l1.setName(uid("L1"));
        l1.setParentId(0L); l1.setLevel(1); l1.setStatus(1);
        Long l1Id = categoryService.save(toSaveDto(l1));

        Category l2 = new Category();
        l2.setName(uid("L2"));
        l2.setParentId(l1Id); l2.setLevel(2); l2.setStatus(1);
        Long l2Id = categoryService.save(toSaveDto(l2));
        assertNotNull(l2Id);

        Category l3 = new Category();
        l3.setName(uid("L3-禁止"));
        l3.setParentId(l2Id);
        l3.setLevel(3);
        l3.setStatus(1);
        assertThrows(Exception.class, () -> categoryService.save(toSaveDto(l3)),
                "M1 精简版限制最多 2 级分类，第 3 级保存必须抛异常");
    }

    @Test
    void saveChildWithWrongLevel_throwsOrAutoCorrect_butEnforcesConsistency() {
        assertNotNull(categoryService);
        Category l1 = new Category();
        l1.setName(uid("家电L1"));
        l1.setParentId(0L); l1.setLevel(1); l1.setStatus(1);
        Long l1Id = categoryService.save(toSaveDto(l1));

        Category bad = new Category();
        bad.setName(uid("错级子类"));
        bad.setParentId(l1Id);
        bad.setLevel(1); // 非法
        bad.setStatus(1);
        // 两种实现都接受：要么抛异常，要么 save 前自动把 level 修正为 2。本测试只断言最终一致性
        Exception thrown = null;
        Long badId = null;
        try { badId = categoryService.save(toSaveDto(bad)); } catch (Exception e) { thrown = e; }
        if (thrown == null) {
            assertNotNull(badId);
            Category saved = categoryService.getById(badId);
            assertEquals(Integer.valueOf(2), saved.getLevel(), "若不抛异常，则 save 必须自动修正为正确层级");
        }
    }

    @Test
    void tree_returnsNestedStructure_withChildrenPopulated() {
        assertNotNull(categoryService);
        String l1Name = uid("大家电2");
        String l2Name = uid("电视");
        Category l1a = new Category();
        l1a.setName(l1Name);
        l1a.setParentId(0L); l1a.setLevel(1); l1a.setStatus(1);
        Long l1Id = categoryService.save(toSaveDto(l1a));
        Category l2a = new Category();
        l2a.setName(l2Name);
        l2a.setParentId(l1Id); l2a.setLevel(2); l2a.setStatus(1);
        Long l2Id = categoryService.save(toSaveDto(l2a));

        List<Category> tree = categoryService.tree(true);
        List<Category> root = tree.stream()
                .filter(x -> l1Id.equals(x.getId())).toList();
        assertEquals(1, root.size());
        assertNotNull(root.get(0).getChildren());
        assertEquals(1, root.get(0).getChildren().size());
        assertEquals(l2Id, root.get(0).getChildren().get(0).getId());
        assertEquals(l2Name, root.get(0).getChildren().get(0).getName());
    }

    @Test
    void pageQuery_andStartOrStop_work() {
        assertNotNull(categoryService);
        Category c = new Category();
        c.setName(uid("启停分类"));
        c.setParentId(0L); c.setLevel(1); c.setStatus(1);
        Long id = categoryService.save(toSaveDto(c));

        categoryService.startOrStop(id, 0);
        assertEquals(Integer.valueOf(0), categoryService.getById(id).getStatus());

        CategoryPageQueryDTO dto = new CategoryPageQueryDTO();
        dto.setPage(1);
        dto.setPageSize(10);
        dto.setName("启停分类");
        PageResult pr = categoryService.pageQuery(dto);
        assertTrue(pr.getTotal() >= 1);
    }

    @Test
    void deleteCategoryWithChildren_throwsException() {
        assertNotNull(categoryService);
        Category l1 = new Category();
        l1.setName(uid("要删的父"));
        l1.setParentId(0L); l1.setLevel(1); l1.setStatus(1);
        Long l1Id = categoryService.save(toSaveDto(l1));
        Category l2 = new Category();
        l2.setName(uid("要删的子"));
        l2.setParentId(l1Id); l2.setLevel(2); l2.setStatus(1);
        categoryService.save(toSaveDto(l2));

        assertThrows(Exception.class, () -> categoryService.deleteById(l1Id),
                "还有子分类时，删除父分类必须拒绝");
    }

    // ==================== 以下为 RED→GREEN 新增：分类下有商品时必须拒绝删除 ====================

    @Autowired(required = false)
    private SpuService spuService;

    /** 仅创建一个 SPU，挂在指定 L1 / L2 分类下，返回 SPU id。 */
    private Long createSpuForTest(Long l1Id, Long l2Id) {
        assertNotNull(spuService, "需要 SpuService 来创建关联商品");
        SpuSaveDTO s = new SpuSaveDTO();
        s.setName(uid("测试SPU-" + (l2Id == null ? "L1挂" : "L2挂")));
        s.setCategoryId(l1Id);
        s.setCategory2Id(l2Id);
        s.setStatus(1);
        s.setDescription("HTML 描述");
        s.setDescriptionMd("## MD 描述");
        s.setMainImage("");
        s.setPrice(new BigDecimal("199.00"));
        s.setTotalStock(99);
        return spuService.saveWithSkus(s);
    }

    @Test
    void deleteLevel2WithLinkedSpus_throwsException_or_messageContains_HAS_LINKED_SPUS() {
        // L1 大家电 / L2 电视机 / SPU 挂在 L2
        Category l1 = new Category();
        l1.setName(uid("大家电")); l1.setParentId(0L); l1.setLevel(1); l1.setStatus(1);
        Long l1Id = categoryService.save(toSaveDto(l1));
        Category l2 = new Category();
        l2.setName(uid("电视机")); l2.setParentId(l1Id); l2.setLevel(2); l2.setStatus(1);
        Long l2Id = categoryService.save(toSaveDto(l2));
        createSpuForTest(l1Id, l2Id);

        // RED 阶段：当前 Service 完全不查 SPU，删除会「成功」→ assertThrows 失败。
        // GREEN 阶段：Service 拒绝 → 抛 BaseException，message 应当对齐 CATEGORY_HAS_LINKED_SPUS
        BaseException ex = assertThrows(BaseException.class, () -> categoryService.deleteById(l2Id),
                "二级分类下还有商品时，删除分类必须抛 BaseException(CATEGORY_HAS_LINKED_SPUS)");
        assertTrue(ex.getMessage() == null
                        || ex.getMessage().isEmpty()
                        || ex.getMessage().contains("商品")
                        || MessageConstant.CATEGORY_HAS_LINKED_SPUS.equals(ex.getMessage()),
                "异常消息应该是「该分类下还有商品，无法删除」。实际: " + ex.getMessage());
    }

    @Test
    void deleteLevel1WithLinkedSpus_throwsException_when_noChildren_but_hasDirectSpus() {
        // L1 无子分类，但 SPU 的 categoryId 直接引用 L1（category2Id = null 或 0）
        Category l1 = new Category();
        l1.setName(uid("无子类但有商品的L1"));
        l1.setParentId(0L); l1.setLevel(1); l1.setStatus(1);
        Long l1Id = categoryService.save(toSaveDto(l1));
        createSpuForTest(l1Id, null);

        BaseException ex = assertThrows(BaseException.class, () -> categoryService.deleteById(l1Id),
                "一级分类下还有关联商品时，删除分类必须抛 BaseException(CATEGORY_HAS_LINKED_SPUS)");
        assertTrue(MessageConstant.CATEGORY_HAS_LINKED_SPUS.equals(ex.getMessage())
                        || (ex.getMessage() != null && ex.getMessage().contains("商品")),
                "异常消息应该是「该分类下还有商品，无法删除」。实际: " + ex.getMessage());
    }

    @Test
    void deleteEmptyLevel2_success() {
        Category l1 = new Category();
        l1.setName(uid("空L1")); l1.setParentId(0L); l1.setLevel(1); l1.setStatus(1);
        Long l1Id = categoryService.save(toSaveDto(l1));
        Category l2 = new Category();
        l2.setName(uid("空L2")); l2.setParentId(l1Id); l2.setLevel(2); l2.setStatus(1);
        Long l2Id = categoryService.save(toSaveDto(l2));

        // 空的 L2 应能正常删除，且不影响 L1
        assertDoesNotThrow(() -> categoryService.deleteById(l2Id));
        assertNull(categoryService.getById(l2Id), "删除后应查不到 L2");
        assertNotNull(categoryService.getById(l1Id), "L1 不应该被级联删除");
    }

    // ==================== 删除分类「预校验」：checkDeletable 接口（在 UI 确认前就给出明确原因+数量） ====================

    @Test
    void checkDeletable_categoryWithLinkedSpus_returnsFalse_withSpuCount_andReason() {
        // 场景：L1 / L2 分类，L2 下挂 1 个 SPU → checkDeletable(L2) 应 false，count=1，reason 含"商品"
        Category l1 = new Category();
        l1.setName(uid("L1-checkDeletable")); l1.setParentId(0L); l1.setLevel(1); l1.setStatus(1);
        Long l1Id = categoryService.save(toSaveDto(l1));
        Category l2 = new Category();
        l2.setName(uid("L2-有商品")); l2.setParentId(l1Id); l2.setLevel(2); l2.setStatus(1);
        Long l2Id = categoryService.save(toSaveDto(l2));
        createSpuForTest(l1Id, l2Id); // 在 L2 上挂 1 个 SPU

        CategoryDeletableVO vo = categoryService.checkDeletable(l2Id);
        assertNotNull(vo);
        assertFalse(vo.getDeletable(), "有商品关联的分类必须 deletable=false");
        assertNotNull(vo.getLinkedSpuCount(), "应返回关联商品数量");
        assertTrue(vo.getLinkedSpuCount() >= 1, "至少有 1 个关联 SPU；实际: " + vo.getLinkedSpuCount());
        assertNotNull(vo.getReason(), "不可删除时必须给出禁止原因");
        assertTrue(vo.getReason().contains("商品"), "原因文案应包含「商品」二字；实际: " + vo.getReason());
    }

    @Test
    void checkDeletable_emptyCategory_returnsTrue_nullSafeReasonAndCount() {
        // 场景：空 L2 分类 → deletable=true，reason 和 count 有合理值
        Category l1 = new Category();
        l1.setName(uid("L1-空")); l1.setParentId(0L); l1.setLevel(1); l1.setStatus(1);
        Long l1Id = categoryService.save(toSaveDto(l1));
        Category l2 = new Category();
        l2.setName(uid("L2-空")); l2.setParentId(l1Id); l2.setLevel(2); l2.setStatus(1);
        Long l2Id = categoryService.save(toSaveDto(l2));

        CategoryDeletableVO vo = categoryService.checkDeletable(l2Id);
        assertNotNull(vo);
        assertTrue(vo.getDeletable(), "空分类 deletable=true");
        assertEquals(Integer.valueOf(0), vo.getLinkedSpuCount(), "空分类关联商品数应为 0");
        // deletable=true 时 reason 可以是 null 或空，但不能抛 NPE
    }

    @Test
    void checkDeletable_level1WithChildren_returnsFalse_andReasonMentionsChildren() {
        Category l1 = new Category();
        l1.setName(uid("L1-有子")); l1.setParentId(0L); l1.setLevel(1); l1.setStatus(1);
        Long l1Id = categoryService.save(toSaveDto(l1));
        Category l2 = new Category();
        l2.setName(uid("L2-子")); l2.setParentId(l1Id); l2.setLevel(2); l2.setStatus(1);
        categoryService.save(toSaveDto(l2));

        CategoryDeletableVO vo = categoryService.checkDeletable(l1Id);
        assertNotNull(vo);
        assertFalse(vo.getDeletable(), "有子分类的 L1 不可删");
        assertTrue(vo.getReason() != null
                && (vo.getReason().contains("子分类") || vo.getReason().contains("商品")), "原因需覆盖子分类或商品；实际: " + vo.getReason());
    }

    // ==================== RED → GREEN 新增：分类分页返回每条带关联 SPU 数（用户要求用「商品数量」替代「类型」列显示） ====================

    /** 一个 L1 分类下挂 N 个 SPU（无 L2）→ pageQuery 返回该 L1 的 spuCount 必须 = N。 */
    @Test
    void pageQuery_level1Category_returnsSpuCountEqualToNumberOfLinkedSpus() {
        assertNotNull(categoryService);
        Integer type = 1;
        Category l1 = new Category();
        l1.setName(uid("L1-计数验证"));
        l1.setParentId(0L); l1.setLevel(1); l1.setType(type); l1.setStatus(1); l1.setSort(0);
        Long l1Id = categoryService.save(toSaveDto(l1));
        // 挂 3 个 SPU，categoryId = l1（L1 维度，category2Id = null 也会被统计）
        int spuCount = 3;
        for (int i = 0; i < spuCount; i++) {
            createSpuForTest(l1Id, null);
        }

        CategoryPageQueryDTO dto = new CategoryPageQueryDTO();
        dto.setPage(1); dto.setPageSize(20);
        dto.setType(type); dto.setStatus(1);
        dto.setSortBy("id"); dto.setSortOrder("desc"); // 最新插的在最前，避免旧数据挡
        PageResult pr = categoryService.pageQuery(dto);
        @SuppressWarnings("unchecked")
        List<Category> list = (List<Category>) pr.getRecords();

        Category hit = list.stream().filter(c -> l1Id.equals(c.getId())).findFirst().orElse(null);
        assertNotNull(hit, "分页结果中应能查到刚插入的 L1 分类");
        // ⚠️ RED 阶段：Category 当前没有 spuCount 字段；GREEN 阶段补上后下面断言才能过
        assertEquals(spuCount, getSpuCountOrFail(hit),
                "L1 分类下挂了 " + spuCount + " 个 SPU，spuCount 应该等于 " + spuCount);
    }

    /** 一个 L2 分类下挂 N 个 SPU（SPU.category2Id = L2.id）→ pageQuery 返回 L2 的 spuCount = N，同时 L1 的 spuCount 仍只统计 categoryId=L1 的。 */
    @Test
    void pageQuery_level2Category_countsOnCategory2Id_and_level1CountsOnCategoryId() {
        assertNotNull(categoryService);
        Integer type = 2; // 套餐分类，避免和上面冲突
        Category l1 = new Category();
        l1.setName(uid("L1-维度拆分验证"));
        l1.setParentId(0L); l1.setLevel(1); l1.setType(type); l1.setStatus(1); l1.setSort(0);
        Long l1Id = categoryService.save(toSaveDto(l1));
        Category l2 = new Category();
        l2.setName(uid("L2-维度拆分验证"));
        l2.setParentId(l1Id); l2.setLevel(2); l2.setType(type); l2.setStatus(1); l2.setSort(0);
        Long l2Id = categoryService.save(toSaveDto(l2));

        // L1 直接挂 2 个 SPU（categoryId = l1，category2Id = null）
        int l1Direct = 2;
        for (int i = 0; i < l1Direct; i++) createSpuForTest(l1Id, null);
        // L2 挂 4 个 SPU（categoryId = l1, category2Id = l2）
        int l2OnL2 = 4;
        for (int i = 0; i < l2OnL2; i++) createSpuForTest(l1Id, l2Id);

        CategoryPageQueryDTO dto = new CategoryPageQueryDTO();
        dto.setPage(1); dto.setPageSize(50);
        dto.setType(type); dto.setStatus(1);
        dto.setSortBy("id"); dto.setSortOrder("desc");
        @SuppressWarnings("unchecked")
        List<Category> list = (List<Category>) categoryService.pageQuery(dto).getRecords();

        Category l1Hit = list.stream().filter(c -> l1Id.equals(c.getId())).findFirst().orElse(null);
        Category l2Hit = list.stream().filter(c -> l2Id.equals(c.getId())).findFirst().orElse(null);
        assertNotNull(l1Hit);
        assertNotNull(l2Hit);
        // 统计口径（和 checkDeletable / 删除校验保持一致，避免 UI 数和删除拦截数不一致）：
        //   L1：统计 categoryId = L1.id OR category2Id = L1.id 的全部 SPU（即 L1 作用域全部商品，含挂在子分类下的）
        //   L2：统计 categoryId = L2.id OR category2Id = L2.id 的全部 SPU
        assertEquals(l1Direct + l2OnL2, getSpuCountOrFail(l1Hit),
                "L1 spuCount 应覆盖「L1 直接挂 + L2 下挂」的总和（和删除禁止口径一致）。");
        assertEquals(l2OnL2, getSpuCountOrFail(l2Hit),
                "L2 spuCount 只统计挂在 L2 下的商品（category2Id = L2 或 categoryId = L2）。");
    }

    /** 兼容 RED / GREEN 过渡期的取值：若 Category 有 spuCount getter 用 getter，否则反射取字段；取不到直接 fail 以暴露 RED。 */
    private static int getSpuCountOrFail(Category c) {
        try {
            java.lang.reflect.Method m = Category.class.getMethod("getSpuCount");
            Object v = m.invoke(c);
            if (v instanceof Number) return ((Number) v).intValue();
            fail("Category.getSpuCount() 返回了非数字: " + v);
            return -1;
        } catch (NoSuchMethodException e) {
            fail("RED→GREEN：Category 目前还没有 getSpuCount() 字段/方法，先在 Category + CategoryVO 上加 spuCount 再让 Service 回填。");
            return -1;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== RED → GREEN 新增：分类分页排序（id/name * asc/desc） ====================

    /** 造 4 个分类，type/status 统一，断言按 createTime ASC 分页返回创建时间从小到大。 */
    @Test
    void pageQuery_sortByCreateTimeAsc_returnsOrdered() {
        assertNotNull(categoryService);
        Integer type = 1;
        String[] names = {"Zoo", "Apple", "Moon", "Cat"};
        for (String n : names) {
            Category c = new Category();
            c.setName(uid(n));
            c.setParentId(0L); c.setLevel(1); c.setType(type); c.setStatus(1); c.setSort(0);
            categoryService.save(toSaveDto(c));
        }

        CategoryPageQueryDTO dto = new CategoryPageQueryDTO();
        dto.setPage(1); dto.setPageSize(20);
        dto.setType(type);
        dto.setStatus(1);
        dto.setSortBy("createTime");
        dto.setSortOrder("asc");

        PageResult pr = categoryService.pageQuery(dto);
        @SuppressWarnings("unchecked")
        List<Category> list = (List<Category>) pr.getRecords();
        assertTrue(list.size() >= 4, "至少应该查到我们造的 4 条分类，实际=" + list.size());
        long prev = -1L;
        for (Category c : list.subList(0, 4)) {
            assertTrue(c.getId() > prev, "分类应按创建时间升序（即 id 升序），但前 id=" + prev + " 后 id=" + c.getId());
            prev = c.getId();
        }
    }

    /** 造 5 个分类，断言按 name DESC 分页返回名称从大到小。 */
    @Test
    void pageQuery_sortByNameDesc_returnsOrdered() {
        assertNotNull(categoryService);
        Integer type = 2;
        String[] names = {"Delta", "Bravo", "Charlie", "Echo", "Alpha"}; // 按字典序 Alpha<Bravo<Charlie<Delta<Echo，desc 应 Echo→Alpha
        for (String n : names) {
            Category c = new Category();
            c.setName(uid(n));
            c.setParentId(0L); c.setLevel(1); c.setType(type); c.setStatus(1); c.setSort(0);
            categoryService.save(toSaveDto(c));
        }

        CategoryPageQueryDTO dto = new CategoryPageQueryDTO();
        dto.setPage(1); dto.setPageSize(20);
        dto.setType(type);
        dto.setStatus(1);
        dto.setSortBy("name");
        dto.setSortOrder("desc");

        PageResult pr = categoryService.pageQuery(dto);
        @SuppressWarnings("unchecked")
        List<Category> list = (List<Category>) pr.getRecords();
        assertTrue(list.size() >= 5, "至少应该查到我们造的 5 条分类，实际=" + list.size());
        String prevName = "\uFFFF";
        for (Category c : list.subList(0, 5)) {
            assertTrue(c.getName().compareTo(prevName) <= 0,
                    "分类应按 name desc，但前 name=" + prevName + " 后 name=" + c.getName());
            prevName = c.getName();
        }
    }
}
