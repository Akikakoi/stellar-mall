package com.stellar.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 行为埋点专用线程池：与业务线程池隔离。
 * <p>埋点是低频优先、高吞吐异步日志场景：队列给足缓冲，拒绝策略丢任务保活
 * （丢埋点可容忍，绝不能反向压垮业务线程）。</p>
 */
@Slf4j
@Configuration
public class BehaviorExecutorConfig {

    public static final String BEHAVIOR_EXECUTOR = "behaviorExecutor";

    @Bean(BEHAVIOR_EXECUTOR)
    public Executor behaviorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(2000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("stellar-behavior-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.initialize();
        log.info("behavior executor initialized: core=2 max=8 queue=2000");
        return executor;
    }
}
