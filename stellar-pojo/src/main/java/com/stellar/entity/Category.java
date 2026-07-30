package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 分类实体（最多 2 级）。children 字段不在表里，由 Service 组装。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category implements Serializable {

    private Long id;

    /** 分类名（同父级同 type 下唯一） */
    private String name;

    /** 父分类 id，0=顶级 */
    private Long parentId;

    /** 层级：1=顶级，2=二级 */
    private Integer level;

    /** 1 商品分类 2 售后分类 */
    private Integer type;

    /** 排序值，越大越靠前 */
    private Integer sort;

    /** 1 启用 0 禁用 */
    private Integer status;

    /** 非数据库字段：tree() 组装时填子分类 */
    private List<Category> children;

    /**
     * 非数据库字段：pageQuery 返回时由 Service 批量填充「该分类作用域下的关联 SPU 数」。
     * 统计口径与 checkDeletable / 删除校验保持一致：
     *   L1（顶级）：SUM(WHERE category_id = L1.id OR category2_id = L1.id) ，即覆盖「L1 直接挂 + 所有 L2 子分类下挂」
     *   L2（二级）：SUM(WHERE category_id = L2.id OR category2_id = L2.id)
     * 默认为 0，避免 NPE。
     */
    @Builder.Default
    private Integer spuCount = 0;

    private LocalDateTime createTime;
    private Long          createUser;
    private LocalDateTime updateTime;
    private Long          updateUser;
}
