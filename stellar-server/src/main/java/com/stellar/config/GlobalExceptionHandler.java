package com.stellar.config;

import com.stellar.exception.BaseException;
import com.stellar.exception.UnauthorizedException;
import com.stellar.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.ServletException;

/**
 * 全局异常处理器（对齐 sky-take-out GlobalExceptionHandler）。
 * <p>
 * 处理顺序：
 *   1) BaseException（业务异常，msg 直接对用户友好，不打堆栈，避免刷屏）
 *   2) UnauthorizedException → 401 HTTP 状态码（让前端 axios.response interceptor 能按 HTTP 401 识别）
 *   3) 参数校验/绑定异常 → 400
 *   4) 其他 RuntimeException → 500，日志打堆栈，返回「系统繁忙」
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. 未授权 → HTTP 401（前端 axios 通用 interceptor 按状态码跳登录页）
    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<?> unauthorized(UnauthorizedException e) {
        log.warn("[GlobalExceptionHandler] 401: {}", e.getMessage());
        return e.toResult();
    }

    // 2. 通用业务异常 → HTTP 200（Result.code=0/自定义，完全对齐 sky）
    @ExceptionHandler(BaseException.class)
    public Result<?> baseException(BaseException e) {
        log.warn("[GlobalExceptionHandler] Biz err: code={}, msg={}", e.getCode(), e.getMessage());
        return e.toResult();
    }

    // 3. 参数校验失败（@Valid @RequestBody 走 MethodArgumentNotValidException，@ModelAttribute 走 BindException）
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> valid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse("参数校验失败");
        log.warn("[GlobalExceptionHandler] 400 Valid: {}", msg);
        return Result.error(400, msg);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> bind(BindException e) {
        String msg = e.getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse("参数绑定失败");
        log.warn("[GlobalExceptionHandler] 400 Bind: {}", msg);
        return Result.error(400, msg);
    }

    // 4. 请求体 JSON 格式错误
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> jsonParse(HttpMessageNotReadableException e) {
        log.warn("[GlobalExceptionHandler] 400 JSON parse: {}", e.getMessage());
        return Result.error(400, "请求体 JSON 格式错误");
    }

    // 5. Servlet 路径匹配错
    @ExceptionHandler(ServletException.class)
    public Result<?> servlet(ServletException e) {
        log.warn("[GlobalExceptionHandler] Servlet err: {}", e.getMessage());
        return Result.error(404, e.getMessage());
    }

    // 6. 兜底：未知 RuntimeException —— ⚠️ 严禁把堆栈塞给前端！只说「系统繁忙」，打日志堆栈
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> runtime(RuntimeException e) {
        log.error("[GlobalExceptionHandler] 500 Uncaught:", e);
        return Result.error(500, "系统繁忙，请稍后再试");
    }

    // 7. 受检异常兜底
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<?> any(Exception e) {
        log.error("[GlobalExceptionHandler] 500 Uncaught:", e);
        return Result.error(500, "系统繁忙，请稍后再试");
    }
}
