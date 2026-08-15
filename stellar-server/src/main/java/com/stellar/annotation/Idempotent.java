package com.stellar.annotation;

import java.lang.annotation.*;

/**
 * 幂等性注解 — 基于请求头 X-Idempotency-Key 防重复提交。
 * <p>
 * 客户端在提交订单等写操作时，生成一个唯一 key（如 UUID），
 * 放在请求头 X-Idempotency-Key 中。服务端在 windowSeconds 内，
 * 同一 key 的重复请求直接返回上一次的处理结果，避免重复创建订单。
 * <p>
 * 使用示例：
 * <pre>{@code
 *   @Idempotent(keyPrefix = "order", windowSeconds = 300)
 *   @PostMapping("/submit")
 *   public Result<?> submit(...) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {

    /** 幂等键前缀，不同接口使用不同前缀 */
    String keyPrefix() default "";

    /** 幂等窗口（秒），超时后相同 key 可按新请求处理 */
    int windowSeconds() default 300;
}
