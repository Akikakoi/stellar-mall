package com.stellar.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.stellar.json.JacksonObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 总配置：
 *  1. 启用 Spring Cache（@Cacheable / @CacheEvict）
 *  2. 统一 RedisTemplate / StringRedisTemplate 序列化
 *  3. 配置 CacheManager 默认过期时间、JSON 序列化
 */
@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    /** 缓存默认 TTL：30 分钟 */
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    /**
     * 使用项目统一的 JacksonObjectMapper 构造 JSON RedisSerializer，
     * 并在类型信息中写入 @class，保证反序列化时知道具体类型。
     */
    @Bean
    public GenericJackson2JsonRedisSerializer jsonRedisSerializer() {
        ObjectMapper objectMapper = new JacksonObjectMapper();
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

    /**
     * StringRedisTemplate：用于分布式锁、计数、字符串缓存。
     * key / value 都使用 String 序列化。
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * CacheManager：优先使用 Redis；Redis 不可用时自动降级为 JVM 内存缓存（开发环境友好）。
     * 默认 30 分钟过期；key 用 String，value 用 JSON。
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                      GenericJackson2JsonRedisSerializer jsonRedisSerializer) {
        if (isRedisAvailable(connectionFactory)) {
            RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(DEFAULT_TTL)
                    .serializeKeysWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(jsonRedisSerializer))
                    .disableCachingNullValues();

            RedisCacheManager manager = RedisCacheManager.builder(connectionFactory)
                    .cacheDefaults(config)
                    .build();

            log.info("RedisCacheManager 已初始化，默认缓存 TTL={} 分钟", DEFAULT_TTL.toMinutes());
            return manager;
        }

        log.warn("Redis 连接失败，已降级为 ConcurrentMapCacheManager（内存缓存，多实例间不共享）");
        return new ConcurrentMapCacheManager();
    }

    /**
     * 探测 Redis 是否可用，避免无 Redis 时服务启动失败。
     */
    private boolean isRedisAvailable(RedisConnectionFactory connectionFactory) {
        try (var conn = connectionFactory.getConnection()) {
            conn.ping();
            return true;
        } catch (Exception e) {
            log.warn("Redis 健康检查失败：{}", e.getMessage());
            return false;
        }
    }
}
