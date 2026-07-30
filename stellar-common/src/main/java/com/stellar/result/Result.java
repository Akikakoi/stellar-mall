package com.stellar.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 后端统一返回结果（完全对齐 sky-take-out Result 结构，RAG 前端 request.js 拦截器也能识别 code=0/1）。
 */
@Data
public class Result<T> implements Serializable {

    /** 状态码：1 成功，0 失败（和 sky 完全一致） */
    private Integer code;
    /** 简短提示 */
    private String msg;
    /** 数据负载 */
    private T data;

    public static <T> Result<T> success() {
        Result<T> r = new Result<>();
        r.code = 1;
        r.msg = "success";
        return r;
    }

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = 1;
        r.msg = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> error(String msg) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.msg = msg;
        return r;
    }

    public static <T> Result<T> error(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        return r;
    }
}
