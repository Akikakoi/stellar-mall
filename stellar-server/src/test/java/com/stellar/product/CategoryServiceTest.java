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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.transaction.annotation.Transactional;

import com.stellar.TestRedisConfig;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分类 Category 全链路测试（平级模型，无层级）。
 * ⚠️ 类级别 @Transactional：每个 @Test 方法结束后自动回滚，保证测试幂等。
 */
@SpringBootTest(properties = "stellar.elasticsearch.enabled=false")
@Transactional
@Import(TestRedisConfig.class)
class CategoryServiceTest {

    @Autowired(required = false)
    private CategoryService categoryService;

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
        Category c = new Category();
        c.setName(uid("大家电"));
        c.setSort(1);
        c.setStatus(1);
        Long id = categoryService.save(toSaveDto(c));
        assertNotNull(id);

        Category got = categoryService.getById(id);
        assertTrue(got.getName().startsWith("大家电"));
    }

    @Test
    void saveDuplicateNameSameType_throwsException() {
        assertNotNull(categoryService);
        String name = uid("重复分类");
        Category c1 = new Category();
        c1.setName(name); c1.setType(1); c1.setStatus(1);
        categoryService.save(toSaveDto(c1));

        Category c2 = new Category();
        c2.setName(name); c2.setType(1); c2.setStatus(1);
        assertThrows(Exception.class, () -> categoryService.save(toSaveDto(c2)),
                "同类型下同名分类保存必须抛异常");
    }

    @Test
    void tree_returnsFlatList() {
        assertNotNull(categoryService);
        String name1 = uid("分类A");
        String name2 = uid("分类B");
        Category c1 = new Category();
        c1.setName(name1); c1.setStatus(1);
        categoryService.save(toSaveDto(c1));
        Category c2 = new Category();
        c2.setName(name2); c2.setStatus(1);
        categoryService.save(toSaveDto(c2));

        List<Category> tree = categoryService.tree(true);
        assertTrue(tree.size() >= 2);
        // 平级模型：所有分类都是顶级，无 children 嵌套
        assertTrue(tree.stream().anyMatch(c -> name1.equals(c.getName())));
        assertTrue(tree.stream().anyMatch(c -> name2.equals(c.getName())));
    }

    @Test
    void pageQuery_andStartOrStop_work() {
        assertNotNull(categoryService);
        Category c = new Category();
        c.setName(uid("启停分类"));
        c.setStatus(1);
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
    void deleteCategoryWithoutSpus_success() {
        assertNotNull(categoryService);
        Category c = new Category();
        c.setName(uid("空分类"));
        c.setStatus(1);
        Long id = categoryService.save(toSaveDto(c));

        assertDoesNotThrow(() -> categoryService.deleteById(id));
        assertNull(categoryService.getById(id), "删除后应查不到分类");
    }

    // ==================== 分类下有商品时必须拒绝删除 ====================

    @Autowired(required = false)
    private SpuService spuService;

    /** ES 关闭后 ElasticsearchConfig 不再创建 ElasticsearchOperations，
     *  用 Mock 满足 SpuEsSyncService 等的构造注入，确保测试不触碰真实 ES。 */
    @MockBean
    private ElasticsearchOperations elasticsearchOperations;

    private Long createSpuForTest(Long catId) {
        assertNotNull(spuService, "需要 SpuService 来创建关联商品");
        SpuSaveDTO s = new SpuSaveDTO();
        s.setName(uid("测试SPU"));
        s.setCategoryId(catId);
        s.setStatus(1);
        s.setDescription("HTML 描述");
        s.setDescriptionMd("## MD 描述");
        s.setMainImage("");
        s.setPrice(new BigDecimal("199.00"));
        s.setTotalStock(99);
        return spuService.saveWithSkus(s);
    }

    @Test
    void deleteCategoryWithLinkedSpus_throwsException() {
        Category c = new Category();
        c.setName(uid("有商品分类"));
        c.setStatus(1);
        Long catId = categoryService.save(toSaveDto(c));
        createSpuForTest(catId);

        BaseException ex = assertThrows(BaseException.class, () -> categoryService.deleteById(catId),
                "分类下还有商品时，删除分类必须抛 BaseException(CATEGORY_HAS_LINKED_SPUS)");
        assertTrue(ex.getMessage() == null
                        || ex.getMessage().isEmpty()
                        || ex.getMessage().contains("商品")
                        || MessageConstant.CATEGORY_HAS_LINKED_SPUS.equals(ex.getMessage()),
                "异常消息应该是「该分类下还有商品，无法删除」。实际: " + ex.getMessage());
    }

    // ==================== 删除分类「预校验」 ====================

    @Test
    void checkDeletable_categoryWithLinkedSpus_returnsFalse_withSpuCount_andReason() {
        Category c = new Category();
        c.setName(uid("checkDeletable-有商品"));
        c.setStatus(1);
        Long catId = categoryService.save(toSaveDto(c));
        createSpuForTest(catId);

        CategoryDeletableVO vo = categoryService.checkDeletable(catId);
        assertNotNull(vo);
        assertFalse(vo.getDeletable(), "有商品关联的分类必须 deletable=false");
        assertNotNull(vo.getLinkedSpuCount(), "应返回关联商品数量");
        assertTrue(vo.getLinkedSpuCount() >= 1, "至少有 1 个关联 SPU；实际: " + vo.getLinkedSpuCount());
        assertNotNull(vo.getReason(), "不可删除时必须给出禁止原因");
        assertTrue(vo.getReason().contains("商品"), "原因文案应包含「商品」二字；实际: " + vo.getReason());
    }

    @Test
    void checkDeletable_emptyCategory_returnsTrue() {
        Category c = new Category();
        c.setName(uid("checkDeletable-空"));
        c.setStatus(1);
        Long catId = categoryService.save(toSaveDto(c));

        CategoryDeletableVO vo = categoryService.checkDeletable(catId);
        assertNotNull(vo);
        assertTrue(vo.getDeletable(), "空分类 deletable=true");
        assertEquals(Integer.valueOf(0), vo.getLinkedSpuCount(), "空分类关联商品数应为 0");
    }

    // ==================== 分类分页返回 SPU 数量 ====================

    @Test
    void pageQuery_returnsSpuCountEqualToNumberOfLinkedSpus() {
        assertNotNull(categoryService);
        Integer type = 1;
        Category c = new Category();
        c.setName(uid("计数验证"));
        c.setType(type); c.setStatus(1); c.setSort(0);
        Long catId = categoryService.save(toSaveDto(c));
        int spuCount = 3;
        for (int i = 0; i < spuCount; i++) {
            createSpuForTest(catId);
        }

        CategoryPageQueryDTO dto = new CategoryPageQueryDTO();
        dto.setPage(1); dto.setPageSize(20);
        dto.setType(type); dto.setStatus(1);
        dto.setSortBy("id"); dto.setSortOrder("desc");
        PageResult pr = categoryService.pageQuery(dto);
        @SuppressWarnings("unchecked")
        List<Category> list = (List<Category>) pr.getRecords();

        Category hit = list.stream().filter(c2 -> catId.equals(c2.getId())).findFirst().orElse(null);
        assertNotNull(hit, "分页结果中应能查到刚插入的分类");
        assertEquals(spuCount, getSpuCountOrFail(hit),
                "分类下挂了 " + spuCount + " 个 SPU，spuCount 应该等于 " + spuCount);
    }

    private static int getSpuCountOrFail(Category c) {
        try {
            java.lang.reflect.Method m = Category.class.getMethod("getSpuCount");
            Object v = m.invoke(c);
            if (v instanceof Number) return ((Number) v).intValue();
            fail("Category.getSpuCount() 返回了非数字: " + v);
            return -1;
        } catch (NoSuchMethodException e) {
            fail("Category 目前还没有 getSpuCount() 方法，请检查 Category 实体类。");
            return -1;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== 分类分页排序 ====================

    @Test
    void pageQuery_sortByCreateTimeAsc_returnsOrdered() {
        assertNotNull(categoryService);
        Integer type = 1;
        String[] names = {"Zoo", "Apple", "Moon", "Cat"};
        for (String n : names) {
            Category c = new Category();
            c.setName(uid(n));
            c.setType(type); c.setStatus(1); c.setSort(0);
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

    @Test
    void pageQuery_sortByNameDesc_returnsOrdered() {
        assertNotNull(categoryService);
        Integer type = 2;
        String[] names = {"Delta", "Bravo", "Charlie", "Echo", "Alpha"};
        for (String n : names) {
            Category c = new Category();
            c.setName(uid(n));
            c.setType(type); c.setStatus(1); c.setSort(0);
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