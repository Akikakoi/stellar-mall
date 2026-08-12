package com.stellar.service.impl;

import com.stellar.constant.JwtClaimsConstant;
import com.stellar.properties.JwtProperties;
import com.stellar.service.TokenBlacklistService;
import com.stellar.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * JWT 黑名单服务实现（E4）。
 * <p>
 * Redis key 设计：{@code jwt_blacklist:{jti}} → "1"，TTL = token 剩余有效期。
 * <p>
 * 降级策略：Redis 不可用时 {@link #isBlacklisted} 返回 false（放行），
 * 由 JWT 本身的签名/过期校验兜底，避免 Redis 故障导致全站不可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    /** Redis key 前缀：jwt_blacklist:{jti} */
    private static final String BLACKLIST_KEY_PREFIX = "jwt_blacklist:";

    private final StringRedisTemplate stringRedisTemplate;
    private final JwtProperties jwtProperties;

    @Override
    public void blacklist(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        try {
            // 先按 admin 密钥试解析，失败再用 user 密钥
            Claims claims = parseClaimsAnySide(token);
            if (claims == null) {
                log.warn("[TokenBlacklist] 无法解析 token，跳过黑名单写入");
                return;
            }
            String jti = claims.getId();
            if (jti == null || jti.isEmpty()) {
                log.warn("[TokenBlacklist] token 无 jti，跳过黑名单写入");
                return;
            }
            Date exp = claims.getExpiration();
            long ttlMs = exp != null ? exp.getTime() - System.currentTimeMillis() : 0L;
            if (ttlMs <= 0) {
                // 已过期，无需写黑名单（JWT 解析本身就会拒绝）
                log.debug("[TokenBlacklist] token 已过期，无需写黑名单: jti={}", jti);
                return;
            }
            stringRedisTemplate.opsForValue().set(
                    BLACKLIST_KEY_PREFIX + jti,
                    "1",
                    ttlMs,
                    TimeUnit.MILLISECONDS);
            log.info("[TokenBlacklist] token 已加入黑名单: jti={}, ttlMs={}", jti, ttlMs);
        } catch (ExpiredJwtException e) {
            // 已过期 token 无需写黑名单
            log.debug("[TokenBlacklist] token 已过期，无需写黑名单");
        } catch (Exception e) {
            log.warn("[TokenBlacklist] 写入黑名单失败: {}", e.getMessage());
        }
    }

    @Override
    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isEmpty()) {
            return false;
        }
        try {
            Boolean exists = stringRedisTemplate.hasKey(BLACKLIST_KEY_PREFIX + jti);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            // 降级：Redis 不可用时放行，由 JWT 签名/过期校验兜底
            log.warn("[TokenBlacklist] Redis 不可用，降级放行: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 用 admin 或 user 密钥尝试解析 token，返回成功的那一个。
     * 两端密钥不同，登出时不知道当前 token 属于哪端，依次尝试。
     */
    private Claims parseClaimsAnySide(String token) {
        // 先 admin
        try {
            return JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
        } catch (Exception ignored) {
            // 落到 user
        }
        try {
            return JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
        } catch (Exception e) {
            return null;
        }
    }
}
