package com.stellar.interceptor;

import com.stellar.annotation.RateLimit;
import com.stellar.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 接口限流拦截器：基于 Redis + Lua 实现真实滑动窗口计数。
 * <p>
 * 限流策略：每个 IP + 限流 key 在 windowSeconds 秒内最多 maxRequests 次。
 * Redis 结构：ZSET，key = rate_limit:{key}:{ip}，member = {时间戳}:{uuid}，score = 时间戳。
 * <p>
 * 为什么用 Lua + ZSET 而不是 INCR+EXPIRE 固定窗口：
 * <ul>
 *   <li>固定窗口在两个窗口交界处可瞬间放行最多 2 倍流量（边界突发）；</li>
 *   <li>原实现 INCR 与 EXPIRE 是两条命令，进程在两步之间崩溃会留下永不过期的 key，
 *       之后每个窗口首请求 count==1 会重新补 EXPIRE，但 key 本身泄漏到那时为止。</li>
 * </ul>
 * Lua 脚本内原子完成「清理过期成员 → 计数 → 判断 → 写入 → 续 TTL」，两个问题同时消除。
 * Redis 不可用时放行（保证可用性优先）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String REDIS_PREFIX = "rate_limit:";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 滑动窗口计数脚本：
     * ARGV: [1]=当前毫秒时间戳, [2]=窗口毫秒, [3]=最大请求数, [4]=本次请求唯一值
     * 返回 1=放行，0=限流。
     */
    private static final DefaultRedisScript<Long> SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local now_ms = tonumber(ARGV[1])
            local window_ms = tonumber(ARGV[2])
            local max_requests = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', key, 0, now_ms - window_ms)
            local count = redis.call('ZCARD', key)
            if count >= max_requests then
                redis.call('PEXPIRE', key, window_ms)
                return 0
            end
            redis.call('ZADD', key, now_ms, now_ms .. ':' .. ARGV[4])
            -- PEXPIRE 必须在 ZADD 之后：首次请求时 key 尚不存在，先 PEXPIRE 是空操作，
            -- ZADD 会创建一个永不过期的 key（TTL 泄漏）
            redis.call('PEXPIRE', key, window_ms)
            return 1
            """, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 是否信任反向代理头（X-Forwarded-For / X-Real-IP 等）。
     * 仅当服务部署在可信反向代理（Nginx 等）之后时才应开启；
     * 直接对外暴露时开启会被伪造 IP 头绕过限流。
     */
    @Value("${stellar.rate-limit.trust-proxy-headers:false}")
    private boolean trustProxyHeaders;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        String key = rateLimit.key();
        if (key.isEmpty()) {
            key = handlerMethod.getMethod().getName();
        }

        String clientIp = getClientIp(request);
        int maxRequests = rateLimit.maxRequests();
        int windowSeconds = rateLimit.windowSeconds();

        if (isRateLimited(key, clientIp, maxRequests, windowSeconds)) {
            log.warn("[限流] IP={} 触发限流 key={} maxRequests={} windowSeconds={}",
                    clientIp, key, maxRequests, windowSeconds);
            writeRateLimitResponse(response);
            return false;
        }

        return true;
    }

    /**
     * 检查是否触发限流，Lua 脚本原子完成滑动窗口计数。
     */
    private boolean isRateLimited(String key, String clientIp, int maxRequests, int windowSeconds) {
        String redisKey = REDIS_PREFIX + key + ":" + clientIp;
        long nowMs = System.currentTimeMillis();
        long windowMs = windowSeconds * 1000L;

        try {
            Long allowed = stringRedisTemplate.execute(
                    SLIDING_WINDOW_SCRIPT,
                    List.of(redisKey),
                    String.valueOf(nowMs),
                    String.valueOf(windowMs),
                    String.valueOf(maxRequests),
                    UUID.randomUUID().toString());
            return !Long.valueOf(1L).equals(allowed);
        } catch (Exception e) {
            // Redis 不可用时放行，避免影响正常业务
            log.warn("[限流] Redis 操作异常，放行请求: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 返回 429 Too Many Requests。
     */
    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        Result<Void> result = Result.error("请求过于频繁，请稍后再试");
        response.getOutputStream().write(
                OBJECT_MAPPER.writeValueAsString(result).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 获取客户端真实 IP。
     * <p>仅在 {@code stellar.rate-limit.trust-proxy-headers=true} 时信任
     * X-Forwarded-For 等代理头；否则直接取 TCP 连接的 RemoteAddr，
     * 防止攻击者伪造 IP 头绕过限流。</p>
     */
    private String getClientIp(HttpServletRequest request) {
        if (trustProxyHeaders) {
            String[] headers = {
                    "X-Forwarded-For",
                    "X-Real-IP",
                    "Proxy-Client-IP",
                    "WL-Proxy-Client-IP"
            };
            for (String header : headers) {
                String ip = request.getHeader(header);
                if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                    // X-Forwarded-For 可能包含多个 IP，取第一个
                    int commaIdx = ip.indexOf(',');
                    return commaIdx > 0 ? ip.substring(0, commaIdx).trim() : ip.trim();
                }
            }
        }
        return request.getRemoteAddr();
    }
}