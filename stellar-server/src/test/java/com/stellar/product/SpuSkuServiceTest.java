package com.stellar.product;

import com.stellar.dto.CategorySaveDTO;
import com.stellar.dto.SpuPageQueryDTO;
import com.stellar.dto.SpuSaveDTO;
import com.stellar.entity.Category;
import com.stellar.entity.Spu;
import com.stellar.entity.Sku;
import com.stellar.result.PageResult;
import com.stellar.service.CategoryService;
import com.stellar.service.SpuService;
import com.stellar.service.SkuService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-M1 RED阶段：SPU + SKU CRUD + SKU 嵌套保存 + 6 SPU 精简样例数据。
 * ⚠️ 类级别 @Transactional：每个 @Test 方法结束后自动回滚，避免残留。
 * ⚠️ 每个 @Test 会先造唯一分类（用返回 ID 当外键），不依赖库里预置数据。
 */
@SpringBootTest
@Transactional
class SpuSkuServiceTest {

    @Autowired(required = false)
    private SpuService spuService;
    @Autowired(required = false)
    private SkuService skuService;
    @Autowired(required = false)
    private CategoryService categoryService;

    private static String uid(String prefix) {
        return prefix + "-UT-" + Integer.toHexString(
                (int) ((System.nanoTime() ^ System.identityHashCode(Thread.currentThread())) & 0xffff));
    }

    /** 创建唯一分类，返回 categoryId */
    private Long seedCategory() {
        Category c = new Category();
        c.setName(uid("CAT"));
        c.setStatus(1);
        CategorySaveDTO cdto = new CategorySaveDTO();
        BeanUtils.copyProperties(c, cdto);
        return categoryService.save(cdto);
    }

    @Test
    void saveSpuWithSkus_persistsAllAndBackfillsIds() {
        assertNotNull(spuService, "RED失败：SpuService 未注册");
        assertNotNull(skuService, "RED失败：SkuService 未注册");
        Long catId = seedCategory();

        SpuSaveDTO dto = new SpuSaveDTO();
        dto.setName(uid("星耀 55 寸 4K 智能电视 Pro"));
        dto.setCategoryId(catId);
        
        dto.setSubtitle("量子点 · 超薄全面屏 · 杜比视界");
        dto.setMainImage("https://cdn.example.com/tv-main.jpg");
        dto.setSubImages("https://cdn.example.com/tv-1.jpg;https://cdn.example.com/tv-2.jpg");
        dto.setDescriptionMd("## 产品亮点\n- 量子点 4K 面板");
        dto.setSort(1);
        dto.setStatus(1);

        Sku s1 = new Sku();
        s1.setName(uid("55 寸 标"));
        s1.setSpecs("屏幕:55 寸;存储:3G+32G");
        s1.setPrice(new BigDecimal("3299.00"));
        s1.setOriginalPrice(new BigDecimal("3999.00"));
        s1.setStock(100);
        s1.setSort(1);
        s1.setStatus(1);

        Sku s2 = new Sku();
        s2.setName(uid("55 寸 高"));
        s2.setSpecs("屏幕:55 寸;存储:4G+64G");
        s2.setPrice(new BigDecimal("3899.00"));
        s2.setOriginalPrice(new BigDecimal("4599.00"));
        s2.setStock(50);
        s2.setSort(2);
        s2.setStatus(1);

        dto.setSkuList(Arrays.asList(s1, s2));

        Long spuId = spuService.saveWithSkus(dto);
        assertNotNull(spuId);

        Spu spu = spuService.getById(spuId);
        assertTrue(spu.getName().startsWith("星耀"));
        assertTrue(spu.getDescriptionMd().contains("量子点 4K 面板"));
        assertEquals(BigDecimal.valueOf(3299.00).compareTo(spu.getMinPrice()), 0, "SPU 最低价格应该取最小 SKU 价");
        assertEquals(Integer.valueOf(150), spu.getTotalStock(), "SPU 总库存应该是 SKU 库存之和 100+50=150");
        assertEquals(Integer.valueOf(2), spu.getSkuCount());

        List<Sku> skus = skuService.listBySpuId(spuId);
        assertEquals(2, skus.size());
        skus.forEach(sku -> assertEquals(spuId, sku.getSpuId()));
        assertEquals(Integer.valueOf(0), skus.get(0).getVersion(), "乐观锁 version 初值必须为 0");
    }

    @Test
    void onOffShelf_updatesStatus_andSetsTime() {
        assertNotNull(spuService);
        Long catId = seedCategory();
        SpuSaveDTO dto = new SpuSaveDTO();
        dto.setName(uid("上下架测试"));
        dto.setCategoryId(catId); dto.setStatus(1);
        dto.setDescriptionMd("# 占位");
        dto.setSkuList(Arrays.asList());
        Long id = spuService.saveWithSkus(dto);

        spuService.onOffShelf(id, 0);
        Spu off = spuService.getById(id);
        assertEquals(Integer.valueOf(0), off.getStatus());
        assertNotNull(off.getOffShelfTime());

        spuService.onOffShelf(id, 1);
        Spu on = spuService.getById(id);
        assertEquals(Integer.valueOf(1), on.getStatus());
        assertNotNull(on.getOnShelfTime());
    }

    @Test
    void pageQueryByName_andUpdate() {
        assertNotNull(spuService);
        Long catId = seedCategory();
        SpuSaveDTO dto = new SpuSaveDTO();
        dto.setName(uid("我是分页SPU"));
        dto.setCategoryId(catId); dto.setStatus(1);
        dto.setDescriptionMd("# x");
        dto.setSkuList(Arrays.asList());
        Long id = spuService.saveWithSkus(dto);

        PageResult pr = spuService.pageQuery(1, 10, "分页SPU", null, null, null);
        assertTrue(pr.getTotal() >= 1);

        SpuSaveDTO update = new SpuSaveDTO();
        update.setId(id);
        update.setName(uid("修改后SPU名"));
        update.setCategoryId(catId); update.setStatus(1);
        update.setDescriptionMd("# 新描述");
        update.setSkuList(Arrays.asList());
        spuService.updateWithSkus(update);
        assertTrue(spuService.getById(id).getName().startsWith("修改后SPU名"));
    }

    // ============================== RED: 新增排序测试（当前必失败） ==============================
    /** 造 3 个 id/名不同的 SPU，断言按 sortBy=id/sortOrder=asc 分页返回 id 从小到大。 */
    @Test
    void pageQueryByDto_sortByIdAsc_returnsOrdered() {
        assertNotNull(spuService);
        Long catId = seedCategory();
        // 注意：sort 设为同一值（覆盖默认的 ORDER BY sort DESC 影响），名字故意乱序以验证排序维度确实生效
        String[] names = {"Z-苹果", "A-香蕉", "M-橙子"};
        for (String n : names) {
            SpuSaveDTO dto = new SpuSaveDTO();
            dto.setName(uid(n));
            dto.setCategoryId(catId); dto.setStatus(1);
            dto.setSort(0);
            dto.setDescriptionMd("# x");
            dto.setSkuList(Arrays.asList());
            spuService.saveWithSkus(dto);
        }

        SpuPageQueryDTO dto = new SpuPageQueryDTO();
        dto.setPage(1); dto.setPageSize(20);
        dto.setCategoryId(catId);     // 仅过滤我们造的，避免库里预置数据干扰
        
        dto.setSortBy("id");
        dto.setSortOrder("asc");

        PageResult pr = spuService.pageQueryByDto(dto);
        @SuppressWarnings("unchecked")
        List<Spu> list = (List<Spu>) pr.getRecords();
        assertTrue(list.size() >= 3, "至少应该查到我们造的 3 条 SPU，实际=" + list.size());
        // 取前 3 条断言 id 严格升序
        long prev = -1L;
        for (Spu s : list.subList(0, 3)) {
            assertTrue(s.getId() > prev, "SPU 应按 id 升序，但前 id=" + prev + " 后 id=" + s.getId());
            prev = s.getId();
        }
    }

    /** 造 3 个 SPU，断言按 sortBy=name/sortOrder=desc 返回名称从 Z→A 倒序。 */
    @Test
    void pageQueryByDto_sortByNameDesc_returnsOrdered() {
        assertNotNull(spuService);
        Long catId = seedCategory();
        String[] names = {"苹果", "香蕉", "橙子", "草莓"};
        for (String n : names) {
            SpuSaveDTO dto = new SpuSaveDTO();
            dto.setName(uid(n));
            dto.setCategoryId(catId); dto.setStatus(1);
            dto.setSort(0);
            dto.setDescriptionMd("# x");
            dto.setSkuList(Arrays.asList());
            spuService.saveWithSkus(dto);
        }

        SpuPageQueryDTO dto = new SpuPageQueryDTO();
        dto.setPage(1); dto.setPageSize(20);
        dto.setCategoryId(catId);
        
        dto.setSortBy("name");
        dto.setSortOrder("desc");

        PageResult pr = spuService.pageQueryByDto(dto);
        @SuppressWarnings("unchecked")
        List<Spu> list = (List<Spu>) pr.getRecords();
        assertTrue(list.size() >= 4, "至少应该查到我们造的 4 条 SPU，实际=" + list.size());
        String prevName = "\uFFFF"; // 大值
        for (Spu s : list.subList(0, 4)) {
            assertTrue(s.getName().compareTo(prevName) <= 0,
                    "SPU 应按 name desc，但前 name=" + prevName + " 后 name=" + s.getName());
            prevName = s.getName();
        }
    }
}
