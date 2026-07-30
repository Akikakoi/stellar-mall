package com.stellar.mapper;

import com.stellar.annotation.AutoFill;
import com.stellar.entity.Spu;
import com.stellar.enumeration.OperationType;
import com.stellar.vo.BucketVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * SPU Mapper。insert / update 必须带 @AutoFill 以触发公共 4 字段注入。
 */
@Mapper
public interface SpuMapper {

    @AutoFill(OperationType.INSERT)
    int insert(Spu spu);

    @AutoFill(OperationType.UPDATE)
    int update(Spu spu);

    int deleteById(@Param("id") Long id);

    Spu getById(@Param("id") Long id);

    /** 批量按 id 列表查询 SPU。 */
    List<Spu> listByIds(@Param("ids") List<Long> ids);

    /** 仅用于上下架状态 + 时间戳动态更新。 */
    @AutoFill(OperationType.UPDATE)
    int updateStatusAndTime(Spu spu);

    /** 聚合 SKU 反写 SPU：min/max 价、总库存、sku_count。一般在 save/updateWithSkus 后调一次。*/
    int refreshAggregatesFromSku(@Param("id") Long spuId,
                                 @Param("minPrice") BigDecimal minPrice,
                                 @Param("maxPrice") BigDecimal maxPrice,
                                 @Param("totalStock") Integer totalStock,
                                 @Param("skuCount") Integer skuCount);

    long count(@Param("name") String name,
               @Param("categoryId") Long categoryId,
               @Param("category2Id") Long category2Id,
               @Param("status") Integer status,
               @Param("isNew") Integer isNew,
               @Param("isHot") Integer isHot,
               @Param("priceFrom") BigDecimal priceFrom,
               @Param("priceTo") BigDecimal priceTo);

    List<Spu> page(@Param("offset") int offset,
                   @Param("pageSize") int pageSize,
                   @Param("name") String name,
                   @Param("categoryId") Long categoryId,
                   @Param("category2Id") Long category2Id,
                   @Param("status") Integer status,
                   @Param("isNew") Integer isNew,
                   @Param("isHot") Integer isHot,
                   @Param("priceFrom") BigDecimal priceFrom,
                   @Param("priceTo") BigDecimal priceTo,
                   @Param("sortBy") String sortBy,
                   @Param("sortOrder") String sortOrder);

    /**
     * 统计「scopeIds 中任一 id 作为 SPU.categoryId 或 SPU.category2Id 关联的 SPU 数」，
     * 同一个 SPU 只会计一次（COUNT DISTINCT），避免 L1 + L2 作用域叠加时的重复计数。
     * scopeIds 为 null/empty → 返回 0。
     */
    long countDistinctIdByCategoryScope(@Param("scopeIds") List<Long> scopeIds);

    /** 查询全部 SPU（用于导出），按 id 排序。 */
    List<Spu> listAll();

    /** 累加商品销量（付款成功后调用）。 */
    int incrSaleCount(@Param("spuId") Long spuId, @Param("qty") int qty);

    /** MySQL 聚合：按分类统计商品数（与 ES 搜索聚合口径一致，复用 spuWhere 过滤条件）。 */
    List<BucketVO> aggCategories(@Param("name") String name,
                                 @Param("categoryId") Long categoryId,
                                 @Param("category2Id") Long category2Id,
                                 @Param("status") Integer status,
                                 @Param("isNew") Integer isNew,
                                 @Param("isHot") Integer isHot,
                                 @Param("priceFrom") BigDecimal priceFrom,
                                 @Param("priceTo") BigDecimal priceTo);

    /** MySQL 聚合：按价格区间统计商品数。 */
    List<BucketVO> aggPriceRanges(@Param("name") String name,
                                  @Param("categoryId") Long categoryId,
                                  @Param("category2Id") Long category2Id,
                                  @Param("status") Integer status,
                                  @Param("isNew") Integer isNew,
                                  @Param("isHot") Integer isHot,
                                  @Param("priceFrom") BigDecimal priceFrom,
                                  @Param("priceTo") BigDecimal priceTo);
}
