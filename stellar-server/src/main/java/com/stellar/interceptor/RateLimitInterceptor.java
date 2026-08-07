package com.stellar.interceptor;

import com.stellar.annotation.RateLimit;
import com.stellar.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 接口限流拦截器：基于 Redis 实现简单滑动窗口计数。
 * <p>
 * 限流策略：每个 IP + 限流 key 在 windowSeconds 秒内最多 maxRequests 次。
 * Redis key 格式：rate_limit:{key}:{ip}:{window_bucket}
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String REDIS_PREFIX = "rate_limit:";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StringRedisTemplate stringRedisTemplate;

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
     * 检查是否触发限流，使用 Redis INCR + EXPIRE 实现固定窗口计数。
     */
    private boolean isRateLimited(String key, String clientIp, int maxRequests, int windowSeconds) {
        // 使用固定窗口：按秒数取整
        long windowBucket = System.currentTimeMillis() / 1000 / windowSeconds;
        String redisKey = REDIS_PREFIX + key + ":" + clientIp + ":" + windowBucket;

        try {
            Long count = stringRedisTemplate.opsForValue().increment(redisKey);
            if (count == null) {
                return false;
            }
            // 首次设置过期时间（窗口结束后的一个窗口内自动清理）
            if (count == 1) {
                stringRedisTemplate.expire(redisKey, windowSeconds * 2L, TimeUnit.SECONDS);
            }
            return count > maxRequests;
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
     * 获取客户端真实 IP，优先取反向代理头。
     */
    private String getClientIp(HttpServletRequest request) {
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
        return request.getRemoteAddr();
    }
}