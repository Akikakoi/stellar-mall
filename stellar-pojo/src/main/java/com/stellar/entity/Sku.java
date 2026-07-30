package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SKU 库存单元（和 stellar_sku 对应）。
 * version 字段是乐观锁扣库存用的：UPDATE ... SET stock=stock-?, version=version+1 WHERE id=? AND version=?
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Sku implements Serializable {

    private Long id;
    private Long spuId;
    /** SKU 全名（建议和 seed 一致：SPU名 · 规格1 · 规格2）。 */
    private String name;
    /** 规格文本（分号分隔：屏幕:55寸;内存:4G+64G）。 */
    private String specs;
    /** 规格 JSON（复杂前端选择器用，可选）。 */
    private String specsJson;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private BigDecimal costPrice;
    private BigDecimal marketPrice;
    /** 当前库存（乐观锁扣减）。 */
    private Integer stock;
    /** 乐观锁版本号，初值 0。 */
    private Integer version;
    private Integer warnStock;
    private Integer weightG;
    private String barcode;
    private String image;
    private Integer sort;
    /** 1 在售 0 停售。 */
    private Integer status;

    private LocalDateTime createTime;
    private Long          createUser;
    private LocalDateTime updateTime;
    private Long          updateUser;
}
