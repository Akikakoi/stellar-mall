package com.stellar.config;

import com.stellar.context.BaseContext;
import org.springframework.core.task.TaskDecorator;

/**
 * 把「提交任务的线程」上的 {@link BaseContext} 快照传递给线程池的工作线程。
 *
 * <p>解决的问题：{@code BaseContext} 底层是 {@link ThreadLocal}，而线程池工作线程是复用的。
 * 一旦业务代码从请求线程切到池线程（{@code @Async}、{@code CompletableFuture.supplyAsync}），
 * 子任务里再调 {@code BaseContext.getCurrentId()} 只会拿到 null —— 例如库存批量调整要记录
 * 「操作人」、埋点要记录「哪个用户在搜」，都会静默记成空值。</p>
 *
 * <p>两个必须做对的细节：</p>
 * <ol>
 *   <li><b>提交时快照</b>：在 {@code decorate()} 里取当前线程的值。该方法由提交任务的线程执行，
 *       此时 ThreadLocal 还是对的；等到任务真正运行再取就已经晚了。</li>
 *   <li><b>执行后清理</b>：{@code finally} 里必须 {@code remove()}。工作线程会被下一个任务复用，
 *       不清理就会把上一个用户的 ID 泄漏给下一个任务 —— 这类越权非常隐蔽，因为多数请求
 *       恰好会覆盖写，只有在没写上下文的任务里才暴露。</li>
 * </ol>
 */
public class BaseContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // 快照必须发生在提交线程上
        Long userId = BaseContext.getCurrentId();
        return () -> {
            if (userId != null) {
                BaseContext.setCurrentId(userId);
            }
            try {
                runnable.run();
            } finally {
                BaseContext.remove();
            }
        };
    }
}
