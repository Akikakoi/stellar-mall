package com.stellar.controller;

import com.stellar.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商城健康检查接口（给 RAG 端 /health 里的 mall_mysql/mall_redis/mall_api 三项探活用）。
 *
 * RAG 端探活规则：
 *   mall_mysql  ←→ HTTP GET http://127.0.0.1:8082/health 看返回里 mysql.ok == true
 *   mall_redis  ←→ 同上，看 redis.ok == true
 *   mall_api   ←→ 该请求本身能 200 就 ok（如果连不上 api 就是 red/error）
 *
 * 路径 /health 不被 JWT 拦截器拦截，方便部署探针和 RAG 桥接层直接探活。
 */
@RestController
@Api(tags = "健康检查")
@Slf4j
public class MallHealthController {

    private final DataSource dataSource;

    /** Redis 已用于缓存与库存分布式锁，但已支持 Redis 不可用时降级，不再属于强依赖。 */
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired
    public MallHealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    @ApiOperation("商城健康检查（供 RAG 探活 mall_mysql/mall_redis/mall_api 三项）")
    public Result<Map<String, Object>> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("service", "stellar-mall");
        out.put("time", ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

        // —— MySQL 探活：拿连接 + isValid(2s)
        Map<String, Object> mysql = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            boolean ok = conn.isValid(2);
            mysql.put("ok", ok);
            if (ok) {
                mysql.put("catalog", conn.getCatalog());
            } else {
                mysql.put("error", "isValid returned false");
            }
        } catch (Exception e) {
            mysql.put("ok", false);
            mysql.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        out.put("mysql", mysql);

        // —— Redis 探活：发一个 PING（若 Redis 未启动则跳过，不影响主业务）
        Map<String, Object> redis = new LinkedHashMap<>();
        if (redisTemplate == null) {
            redis.put("ok", false);
            redis.put("skipped", true);
            redis.put("error", "Redis 未配置或未启动（业务不依赖 Redis，仅跳过本探活项）");
        } else {
            try {
                String pong = redisTemplate.execute((RedisConnection c) -> c.ping());
                redis.put("ok", "PONG".equalsIgnoreCase(pong));
                if (pong != null) redis.put("pong", pong);
            } catch (Exception e) {
                redis.put("ok", false);
                redis.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        out.put("redis", redis);

        // —— api 自己肯定是 ok（能走到这里说明请求本身 200 了）
        Map<String, Object> api = new LinkedHashMap<>();
        api.put("ok", true);
        out.put("api", api);

        return Result.success(out);
    }
}
