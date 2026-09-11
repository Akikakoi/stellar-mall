package com.stellar.service;

public interface SkuStockService {

    /**
     * 扣减库存并记录出入库日志。
     * 并发控制由 {@code stellar.stock.lock-mode} 决定：optimistic（默认）为单条原子条件扣减，
     * redis 为先取分布式锁再更新；库存不足抛 BaseException（或子类 StockInsufficientException）。
     *
     * @param businessNo 关联业务单号（如订单号），写入出入库日志用于追溯，可传 null
     */
    void deduct(Long skuId, int qty, String businessNo);

    /**
     * 回滚库存并记录出入库日志（给取消订单/售后回退场景用），并发控制同上。
     *
     * @param businessNo 关联业务单号（如订单号），写入出入库日志用于追溯，可传 null
     */
    void rollback(Long skuId, int qty, String businessNo);
}
