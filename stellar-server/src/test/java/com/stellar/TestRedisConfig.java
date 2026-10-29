package com.stellar;

import jakarta.annotation.PostConstruct;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.javamail.JavaMailSender;
import redis.embedded.RedisServer;

import java.io.IOException;

/**
 * 测试用内嵌 Redis 配置（固定端口 16379，与 application.properties 的
 * spring.data.redis.port 保持一致）。
 *
 * <p>必须是「进程级单例」：Spring Test 会按上下文配置缓存多个 ApplicationContext，
 * 每个上下文都会实例化本类。若每个实例都去 bind 16379，第二个上下文就会因端口占用而
 * 「Could not create server TCP listening socket 127.0.0.1:16379: bind」启动失败。
 * 因此这里只在首次调用时启动，随 JVM 退出统一关闭，避免残留孤儿进程。</p>
 */
@TestConfiguration
public class TestRedisConfig {

    private static final int REDIS_PORT = 16379;

    /** 进程级单例，跨 ApplicationContext 复用。 */
    private static RedisServer redisServer;

    @PostConstruct
    public void startRedis() throws IOException {
        startOnce();
    }

    private static synchronized void startOnce() throws IOException {
        if (redisServer != null) {
            return;
        }
        RedisServer server = new RedisServer(REDIS_PORT);
        server.start();
        redisServer = server;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.stop();
            } catch (Exception ignored) {
                // JVM 退出阶段忽略关闭异常
            }
        }));
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
