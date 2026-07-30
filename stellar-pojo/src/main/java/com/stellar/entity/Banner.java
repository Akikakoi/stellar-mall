package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 首页轮播图 Banner（和 stellar_banner 对应）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Banner implements Serializable {

    private Long id;

    /** 标题 */
    private String title;

    /** 图片 URL */
    private String imageUrl;

    /** 点击跳转链接（可为空，空则不可点击） */
    private String linkUrl;

    /** 排序值，越大越靠前 */
    private Integer sort;

    /** 1 启用 0 禁用 */
    private Integer status;

    private LocalDateTime createTime;
    private Long          createUser;
    private LocalDateTime updateTime;
    private Long          updateUser;
}