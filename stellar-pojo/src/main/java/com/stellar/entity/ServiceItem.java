package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保障服务字典实体。表：stellar_service_item。
 * <p>code 为稳定业务标识：前端 serviceInfo JSON 快照中的 id 即 code，
 * 管理端改标题/价格不影响历史快照的解析。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceItem {

    private Long id;

    /** 业务标识（快照 id，稳定不变），如 screen_insurance */
    private String code;

    /** 展示名称，如：碎屏险 · 1年 */
    private String title;

    /** 单价（元/件，随商品数量倍乘） */
    private BigDecimal price;

    /** 说明（选填） */
    private String description;

    /** 1 启用 0 停用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
