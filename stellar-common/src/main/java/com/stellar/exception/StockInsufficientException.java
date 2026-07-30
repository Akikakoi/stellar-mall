package com.stellar.exception;

import com.stellar.constant.MessageConstant;

/**
 * 库存不足异常（SkuStockService 抛出，便于上层精细化 catch）。
 * 继承 BaseException 以便全局处理器直接返回 STOCK_NOT_ENOUGH 文案。
 */
public class StockInsufficientException extends BaseException {

    public StockInsufficientException() {
        super(MessageConstant.STOCK_NOT_ENOUGH);
    }

    public StockInsufficientException(String msg) {
        super(msg);
    }
}
