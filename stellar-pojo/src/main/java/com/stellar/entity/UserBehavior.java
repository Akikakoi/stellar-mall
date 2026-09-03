package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户行为埋点日志。表：stellar_user_behavior。
 * <p>只追加、不更新；由 C 端埋点接口异步批量落库，异常静默不影响业务。
 * 事件类型统一小写下划线：view_item_list / view_item / search / add_to_cart /
 * order_placed / favorite / page_view。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehavior implements Serializable {

    private Long id;

    /** 登录用户 ID（游客为 NULL，靠 deviceId 归因） */
    private Long userId;

    /** 前端匿名设备 ID（localStorage 生成，登录前后一致） */
    private String deviceId;

    /** 事件类型 */
    private String eventType;

    /** 关联 SPU ID */
    private Long spuId;

    /** 关联 SKU ID */
    private Long skuId;

    /** 关联分类 ID */
    private Long categoryId;

    /** 搜索词（search 事件） */
    private String keyword;

    /** 来源场景：home/search/category/detail/cart/order/favorites */
    private String scene;

    /** 列表位次（view_item_list 用，从 1 开始） */
    private Integer position;

    /** 金额（商品单价 / 下单金额） */
    private BigDecimal amount;

    /** 停留时长毫秒（view_item 离开时补报） */
    private Integer durationMs;

    /** 扩展 JSON（排序方式/分页等） */
    private String extra;

    /** 客户端 IP（后端记录） */
    private String clientIp;

    /** User-Agent（后端记录） */
    private String userAgent;

    /** 前端事件发生时间 */
    private LocalDateTime eventTime;

    /** 入库时间 */
    private LocalDateTime createTime;
}
