package com.stellar.service;

public interface SkuStockService {

    /**
     * 扣减库存，内部使用乐观锁 version 重试（默认最多 5 次）。
     * 库存不足抛 BaseException（或子类 StockInsufficientException）。
     */
    void deduct(Long skuId, int qty);

    /**
     * 回滚库存（给取消订单场景用），同样乐观锁重试。
     */
    void rollback(Long skuId, int qty);
}
