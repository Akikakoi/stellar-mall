package com.stellar.annotation;

import java.lang.annotation.*;

/**
 * 接口限流注解，基于 Redis 滑动窗口计数。
 * <p>
 * 使用示例：
 * <pre>{@code
 *   @RateLimit(key = "login", maxRequests = 10, windowSeconds = 60)
 *   @PostMapping("/login")
 *   public Result<?> login(...) { ... }
 * }</pre>
 * <p>
 * key 用于区分不同接口的限流桶，默认取方法名。
 * 限流粒度：每个 IP + key 独立计数。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 限流标识，不同接口使用不同 key */
    String key() default "";

    /** 时间窗口内允许的最大请求数 */
    int maxRequests() default 10;

    /** 时间窗口大小（秒） */
    int windowSeconds() default 60;
}