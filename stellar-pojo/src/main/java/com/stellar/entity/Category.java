package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 分类实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category implements Serializable {

    private Long id;

    /** 分类名（同父级同 type 下唯一） */
    private String name;

    /** 1 商品分类 2 售后分类 */
    private Integer type;

    /** 排序值，越大越靠前 */
    private Integer sort;

    /** 1 启用 0 禁用 */
    private Integer status;

    /**
     * 非数据库字段：该分类下的关联商品数，默认为 0，避免 NPE。
     */
    @Builder.Default
    private Integer spuCount = 0;

    private LocalDateTime createTime;
    private Long          createUser;
    private LocalDateTime updateTime;
    private Long          updateUser;
}
