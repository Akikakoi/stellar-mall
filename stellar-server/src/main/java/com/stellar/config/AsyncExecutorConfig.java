package com.stellar.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 业务异步线程池：按任务语义隔离，避免所有 {@code @Async} 挤在同一个池里互相拖累。
 *
 * <p><b>为什么每个 {@code @Async} 都必须写全执行器名</b>：Spring Boot 的
 * {@code applicationTaskExecutor} 带 {@code @ConditionalOnMissingBean(Executor.class)}，
 * 一旦容器里存在任何 Executor Bean（本项目已有 {@link BehaviorExecutorConfig} 提供的
 * behaviorExecutor），默认池就不再创建。此时未标注执行器名的 {@code @Async} 会由
 * {@code AsyncExecutionAspectSupport#getDefaultExecutor} 按 {@code TaskExecutor} 类型
 * 去容器里取「唯一」的那个池——此前取到的正是埋点专用池（core=2 +
 * {@link ThreadPoolExecutor.DiscardPolicy}），于是验证码邮件、发货通知、ES 同步全都
 * 跑在埋点池上：既与被埋点批量写抢那两个线程，队列打满时还会被静默丢弃。</p>
 *
 * <p><b>注意</b>：现在容器里有多个 Executor Bean，若将来新增 {@code @Async} 时忘记写执行器名，
 * 按类型取 Bean 会因不唯一而失败，从而退化为 {@code SimpleAsyncTaskExecutor}
 * —— 那个执行器每次调用都新建线程且无上限，比多池共存本身更危险。</p>
 *
 * <p>结论：本项目所有 {@code @Async} 一律显式写执行器名，且拒绝策略按「能否丢」来选。</p>
 */
@Slf4j
@Configuration
public class AsyncExecutorConfig {

    /** 通知发信池：验证码邮件 / 发货通知，用户可感知，不允许静默丢弃。 */
    public static final String NOTIFICATION_EXECUTOR = "notificationExecutor";

    /** ES 同步池：商品变更后写 ES + 取语义向量，纯 IO 密集，与业务请求线程隔离。 */
    public static final String ES_SYNC_EXECUTOR = "esSyncExecutor";

    /** 聚合查询池：单个请求内把多次独立查询并行编排（如管理端看板 20+ 次统计）。 */
    public static final String AGGREGATE_EXECUTOR = "aggregateExecutor";

    /** 并发发信上限（SMTP 是阻塞网络 IO，数百 ms 起，不宜开太大）。 */
    private static final int CALLER_RUNS_IO_POOL_CORE = 2;
    private static final int CALLER_RUNS_IO_POOL_MAX = 8;

    /** 队列容量：给足缓冲，正常流量下永远不该触发拒绝策略。 */
    private static final int IO_POOL_QUEUE = 1000;

    /**
     * 聚合查询池的线程数。
     * <p>不能再大：Druid 未显式配置连接池，走的是默认 {@code max-active=8}。
     * 并行度超过连接数只会让线程堵在 {@code getConnection} 上，白白占着线程还拿不到收益。</p>
     */
    private static final int AGGREGATE_POOL_SIZE = 8;

    /**
     * 通知线程池。
     * <p>拒绝策略用 {@link ThreadPoolExecutor.CallerRunsPolicy}：宁可让调用方多等一会儿
     * 把信同步发出去，也不能像埋点那样直接丢——验证码或发货通知丢了用户会直接感知。
     * 同时开启优雅停机，避免重启时丢掉队列里的在途通知。</p>
     */
    @Bean(NOTIFICATION_EXECUTOR)
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CALLER_RUNS_IO_POOL_CORE);
        executor.setMaxPoolSize(CALLER_RUNS_IO_POOL_MAX);
        executor.setQueueCapacity(IO_POOL_QUEUE);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("stellar-notify-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(new BaseContextTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("notification executor initialized: core={} max={} queue={} policy=CallerRuns",
                CALLER_RUNS_IO_POOL_CORE, CALLER_RUNS_IO_POOL_MAX, IO_POOL_QUEUE);
        return executor;
    }

    /**
     * ES 同步线程池。
     * <p>由 SPU 变更事件驱动，链路里含外部 HTTP（取 embedding）与外部存储（写 ES），
     * 单次耗时被 IO 放大，单独成池避免与发信互相排队；队列打满时退化为调用方执行，
     * 保证商品数据最终一致。</p>
     */
    @Bean(ES_SYNC_EXECUTOR)
    public Executor esSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CALLER_RUNS_IO_POOL_CORE);
        executor.setMaxPoolSize(CALLER_RUNS_IO_POOL_MAX);
        executor.setQueueCapacity(IO_POOL_QUEUE);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("stellar-essync-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(new BaseContextTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("es sync executor initialized: core={} max={} queue={} policy=CallerRuns",
                CALLER_RUNS_IO_POOL_CORE, CALLER_RUNS_IO_POOL_MAX, IO_POOL_QUEUE);
        return executor;
    }

    /**
     * 聚合查询线程池：把一个请求内的多次独立查询并行化。
     *
     * <p>典型用法见 {@code AdminDashboardController#enhanced()}：原先 21 次查询串行执行，
     * 改并行后总耗时从「各次之和」降到约「并发度分之一」，且这些查询本身不在事务内，
     * 不会踩到「Spring 事务绑定 ThreadLocal、子线程无法加入主线程事务」的坑。</p>
     *
     * <p><b>反过来必须守住的红线</b>：不要在 {@code @Transactional} 方法内部把查询丢进来并行执行。
     * 子线程不会加入主线程事务，会各自从连接池另取连接、看不到主线程未提交的数据，
     * 还会白白放大连接占用。</p>
     */
    @Bean(AGGREGATE_EXECUTOR)
    public Executor aggregateExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(AGGREGATE_POOL_SIZE);
        executor.setMaxPoolSize(AGGREGATE_POOL_SIZE);
        executor.setQueueCapacity(128);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("stellar-aggr-");
        // 队列满时由调用方执行：聚合查询在请求线程里同步做完，比丢结果或抛异常给用户都好
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(new BaseContextTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("aggregate executor initialized: core={} max={} queue=128 policy=CallerRuns",
                AGGREGATE_POOL_SIZE, AGGREGATE_POOL_SIZE);
        return executor;
    }
}
