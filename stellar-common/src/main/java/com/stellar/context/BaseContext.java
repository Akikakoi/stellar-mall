package com.stellar.context;

/**
 * 线程上下文：存储当前登录用户 ID（由 JWT 拦截器写入，Controller/Service/AOP 切面随时读取）。
 *
 * ⚠️ 用完记得清理（拦截器 afterCompletion），防止线程池复用时「前一个用户 ID 泄漏给下一个请求」。
 */
public class BaseContext {

    private static final ThreadLocal<Long> THREAD_LOCAL = new ThreadLocal<>();

    public static void setCurrentId(Long id) {
        THREAD_LOCAL.set(id);
    }

    public static Long getCurrentId() {
        return THREAD_LOCAL.get();
    }

    public static void remove() {
        THREAD_LOCAL.remove();
    }
}
