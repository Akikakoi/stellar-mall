package com.stellar;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import redis.embedded.RedisServer;

/**
 * 测试用内嵌 Redis 配置。
 * 随机端口启动，避免端口冲突。
 */
@TestConfiguration
public class TestRedisConfig {

    private RedisServer redisServer;

    @PostConstruct
    public void startRedis() throws Exception {
        redisServer = new RedisServer(16379);
        redisServer.start();
    }

    @PreDestroy
    public void stopRedis() throws Exception {
        if (redisServer != null) {
            redisServer.stop();
        }
    }

    @Bean
    @Primary
    public JavaMailSender javaMailSender() {
        return Mockito.mock(JavaMailSender.class);
    }

    @Bean
    @Primary
    public org.springframework.boot.autoconfigure.mail.MailProperties mailProperties() {
        return new org.springframework.boot.autoconfigure.mail.MailProperties();
    }
}
