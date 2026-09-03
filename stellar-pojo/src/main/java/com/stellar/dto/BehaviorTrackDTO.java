package com.stellar.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 行为埋点批量上报 DTO：前端把一段时间/一批事件攒起来一次 POST。
 * <p>字段一律宽松校验（服务端容错），埋点失败不影响业务主流程。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BehaviorTrackDTO {

    /** 匿名设备 ID（必传，前端 localStorage 生成，游客与登录用户一致） */
    private String deviceId;

    /** 一批事件（上限 100，超出由前端分批） */
    private List<BehaviorEventDTO> events;

    /** 单条行为事件。 */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BehaviorEventDTO {

        /** 事件类型：view_item_list/view_item/search/add_to_cart/order_placed/favorite/page_view */
        private String eventType;

        private Long spuId;

        private Long skuId;

        private Long categoryId;

        /** 搜索词（search 事件） */
        private String keyword;

        /** 来源场景：home/search/category/detail/cart/order/favorites */
        private String scene;

        /** 列表位次（从 1 开始） */
        private Integer position;

        /** 金额（商品单价/下单金额） */
        private BigDecimal amount;

        /** 停留时长 ms（view_item 离开时补报） */
        private Integer durationMs;

        /** 扩展信息（对象或 JSON 字符串均可，服务端序列化后入库，最长 500 字符） */
        private Object extra;
    }
}
