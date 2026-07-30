package com.stellar.exception;

import com.stellar.result.Result;

/**
 * 业务异常基类——Controller 层抛出后由 GlobalExceptionHandler 统一包装成 Result.error(msg, code) 返回。
 * <p>
 * ⚠️ 永远不要把堆栈、密钥、SQL 语句塞进 getMessage()——交给后端日志打。
 */
public class BaseException extends RuntimeException {

    private final int code;

    public BaseException(String msg) {
        super(msg);
        this.code = 0;
    }

    public BaseException(String msg, int code) {
        super(msg);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public Result<?> toResult() {
        return Result.error(code, getMessage());
    }
}
