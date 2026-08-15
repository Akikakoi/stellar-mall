package com.stellar;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 星耀商城 stellar-mall 启动类。
 * <p>
 * 技术栈：
 *   Spring Boot 2.7.18 (Java 17) / MyBatis + PageHelper / Spring Security Crypto (BCrypt) /
 *   Redis (Lettuce) / Knife4j 4.4.0 / Apache HttpClient / Jackson / JJWT 0.9.1
 * <p>
 * ⚠️ 本地启动前：
 *   1) 在 MySQL 创建 stellar_mall 库 + stellar / stellar_ro 两个账号（见 sql/stellar_mall_accounts.sql）
 *   2) 用 stellar 账号执行 sql/stellar_mall_ddl.sql（建表 + seed）
 *   3) 启动 Redis（默认 db=11）
 *   4) 复制 application-dev.yml.example 为 application-dev.yml，改成本地账号
 *   5) 启动后访问：http://127.0.0.1:8082/doc.html （Swagger/Knife4j 文档）
 */
@Slf4j
@SpringBootApplication(exclude = {
        ElasticsearchClientAutoConfiguration.class,
        ElasticsearchRestClientAutoConfiguration.class
})
@MapperScan(basePackages = {"com.stellar.mapper", "com.stellar.ragsync.mapper"})
@EnableScheduling
@EnableAsync
@EnableTransactionManagement
public class StellarMallApplication {

    public static void main(String[] args) {
        SpringApplication.run(StellarMallApplication.class, args);
        log.info("========================================");
        log.info("  Stellar Mall 启动成功！");
        log.info("  端口: 8082");
        log.info("  Swagger: http://127.0.0.1:8082/doc.html");
        log.info("  健康检查: http://127.0.0.1:8082/health");
        log.info("========================================");
    }
}
