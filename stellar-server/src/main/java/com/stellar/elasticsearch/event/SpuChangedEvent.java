package com.stellar.elasticsearch.event;

import lombok.Getter;

/**
 * SPU 变更事件 — 当商品新增/更新/删除时发布，
 * 由 {@link com.stellar.elasticsearch.sync.SpuEsSyncService} 监听并同步至 ES。
 */
@Getter
public class SpuChangedEvent {

    public enum Action {
        SAVE,
        DELETE
    }

    private final Long spuId;
    private final Action action;

    public SpuChangedEvent(Long spuId, Action action) {
        this.spuId = spuId;
        this.action = action;
    }

    /** 商品保存（新增/更新） */
    public static SpuChangedEvent saved(Long spuId) {
        return new SpuChangedEvent(spuId, Action.SAVE);
    }

    /** 商品删除 */
    public static SpuChangedEvent deleted(Long spuId) {
        return new SpuChangedEvent(spuId, Action.DELETE);
    }
}
