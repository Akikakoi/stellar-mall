package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 首页装修模块（对应 stellar_home_module 表）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeModule implements Serializable {

    private Long id;

    /** 模块类型：BANNER / HOT_PRODUCTS / NEW_PRODUCTS / CATEGORY_SHOWCASE / COUPON_ENTRY / PRODUCT_GRID / SINGLE_IMAGE */
    private String type;

    /** 模块标题 */
    private String title;

    /** 模块配置 JSON */
    private String config;

    /** 排序值，越小越靠前 */
    private Integer sortOrder;

    /** 1 启用 0 禁用 */
    private Integer status;

    private LocalDateTime createTime;
    private Long          createUser;
    private LocalDateTime updateTime;
    private Long          updateUser;
}
