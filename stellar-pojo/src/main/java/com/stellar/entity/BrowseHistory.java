package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 商品浏览历史（用户足迹）。表：stellar_browse_history。
 * <p>仅登录用户产生记录；(user_id, spu_id) 唯一，重复浏览只刷新 browse_time；
 * 已下架/已删除商品记录保留，由前端据 status 提示。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrowseHistory implements Serializable {

    private Long id;

    /** 登录用户 ID */
    private Long userId;

    /** 商品 SPU ID */
    private Long spuId;

    /** 浏览时的 SKU ID（可选） */
    private Long skuId;

    /** 最近浏览时间 */
    private LocalDateTime browseTime;

    /** 首次浏览时间 */
    private LocalDateTime createTime;
}