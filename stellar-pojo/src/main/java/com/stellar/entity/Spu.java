package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SPU 标准产品单元（和 stellar_spu 对应）。
 * skuList 是非 DB 字段：Service 层组装，返回给前端时填充。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Spu implements Serializable {

    private Long id;
    private String name;
    private String subTitle;
    private Long brandId;
    private Long categoryId;
    private Long category2Id;       // 二级分类（三级分类结构中）
    private String description;
    private String descriptionMd;
    private String mainImage;
    private String subImages;
    private String sliderImages;
    private Integer saleCount;
    private Integer commentCount;
    private Integer totalStock;
    private Integer skuCount;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer isNew;
    private Integer isHot;
    private Integer sort;
    private Integer status;          // 1 上架 0 下架
    private LocalDateTime onShelfTime;
    private LocalDateTime offShelfTime;
    private LocalDateTime createTime;
    private Long          createUser;
    private LocalDateTime updateTime;
    private Long          updateUser;

    /** 非 DB 字段：详情/列表接口返回时可选填充（后端标准字段名）。 */
    private List<Sku> skuList;
    /** 非 DB 字段：skuList 的别名（前端常用 skus，getById/列表组装时同步填充 skuList + skus）。 */
    private List<Sku> skus;
    /** 非 DB 字段：品牌名（列表联查返回）。 */
    private String brandName;
    /** 非 DB 字段：分类名。 */
    private String categoryName;
}
