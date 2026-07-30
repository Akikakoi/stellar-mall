package com.stellar.interceptor;

import com.stellar.constant.JwtClaimsConstant;
import com.stellar.constant.MessageConstant;
import com.stellar.context.BaseContext;
import com.stellar.exception.UnauthorizedException;
import com.stellar.properties.JwtProperties;
import com.stellar.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * C 端（用户/小程序）JWT 拦截器。
 *
 * token header 支持两种：
 *   1) authentication=<jwt>（对齐 sky-take-out user 拦截器）
 *   2) Authorization=Bearer <jwt>（对齐 RAG 端）
 *
 * 拦截后把 USER_ID 写入 BaseContext（和 EMP_ID 共用 ThreadLocal；
 * 管理员端和 C 端的请求路径是隔离的，不会混用）。
 */
@Slf4j
@Component
public class JwtTokenUserInterceptor implements HandlerInterceptor {

    private final JwtProperties jwtProperties;

    @Autowired
    public JwtTokenUserInterceptor(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) return true;

        String token = extractToken(req);
        if (token == null || token.isEmpty()) {
            throw new UnauthorizedException(MessageConstant.UNAUTHORIZED);
        }

        Claims claims;
        try {
            claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException("登录已过期，请重新登录");
        } catch (SignatureException e) {
            throw new UnauthorizedException(MessageConstant.UNAUTHORIZED);
        } catch (Exception e) {
            log.warn("[JwtTokenUserInterceptor] token invalid: {}", e.getMessage());
            throw new UnauthorizedException(MessageConstant.UNAUTHORIZED);
        }

        Long userId = ((Number) claims.get(JwtClaimsConstant.USER_ID)).longValue();
        BaseContext.setCurrentId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        BaseContext.remove();
    }

    private String extractToken(HttpServletRequest req) {
        String t = req.getHeader(jwtProperties.getUserTokenName()); // "authentication"
        if (t != null && !t.isEmpty()) return t;

        String auth = req.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return auth.substring(7);
        }
        return null;
    }
}
