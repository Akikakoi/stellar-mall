package com.stellar.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.StringUtils;

/**
 * Redisson 独立客户端配置：仅供分布式锁（{@link com.stellar.utils.RedisLockUtil}）使用。
 *
 * <p><b>为什么用独立 {@code org.redisson:redisson} 而不是 {@code redisson-spring-boot-starter}</b>：
 * starter 会注册 {@code RedissonConnectionFactory} 并顶替 Lettuce 成为 RedisTemplate 的连接工厂，
 * 所有既有 Redis 读写（缓存 / 幂等 / 限流）都被迫走 Redisson 的握手与编解码路径——
 * 其中任何一条不兼容（如测试内嵌 Redis、旧版本命令缺失）会把无关业务一起拖下水。
 * 独立 client 的爆炸半径只有锁这一处。</p>
 *
 * <p>连接参数复用 {@code spring.data.redis.*}（host/port/database/password），
 * 与 Lettuce 天然指向同一实例；连接池收窄为 2/8——锁操作是低频轻量命令，不值得占大池。</p>
 *
 * <p><b>为什么 {@code @Lazy}</b>：Redisson.create() 会急切建连，Redis 离线时若在
 * 启动期创建该 bean 会导致整个应用起不来，破坏项目「Redis 挂了降级运行」的既有语义。
 * 惰性后创建推迟到首次用锁，失败由 {@code RedisLockUtil} 捕获降级为 null token。</p>
 */
@Slf4j
@Configuration
public class RedissonConfig {

    @Lazy
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        Config config = new Config();
        String address = "redis://" + redisProperties.getHost() + ":" + redisProperties.getPort();
        var single = config.useSingleServer()
                .setAddress(address)
                .setDatabase(redisProperties.getDatabase())
                .setConnectionMinimumIdleSize(2)
                .setConnectionPoolSize(8)
                .setConnectTimeout(3000)
                .setTimeout(3000)
                .setRetryAttempts(3);
        if (StringUtils.hasText(redisProperties.getPassword())) {
            single.setPassword(redisProperties.getPassword());
        }
        RedissonClient client = Redisson.create(config);
        log.info("Redisson client initialized: address={}, database={}", address, redisProperties.getDatabase());
        return client;
    }
}
