package com.stellar.interceptor;

import com.stellar.constant.MessageConstant;
import com.stellar.context.BaseContext;
import com.stellar.exception.UnauthorizedException;
import com.stellar.properties.JwtProperties;
import com.stellar.service.TokenBlacklistService;
import com.stellar.utils.JwtUtil;
import com.stellar.utils.TokenUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 管理端 / C 端 JWT 拦截器公共流程：提取 token → 解析验签 → 黑名单校验 → 写入 BaseContext。
 * <p>子类只负责声明各自的 token header 名、密钥与 claim 名。</p>
 */
@Slf4j
public abstract class AbstractJwtTokenInterceptor implements HandlerInterceptor {

    protected final JwtProperties jwtProperties;
    protected final TokenBlacklistService tokenBlacklistService;

    protected AbstractJwtTokenInterceptor(JwtProperties jwtProperties,
                                          TokenBlacklistService tokenBlacklistService) {
        this.jwtProperties = jwtProperties;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        // 静态资源 / 非 Controller 请求直接放行
        if (!(handler instanceof HandlerMethod)) return true;

        String token = TokenUtils.extractToken(req, tokenHeaderName());
        if (token == null || token.isEmpty()) {
            throw new UnauthorizedException(MessageConstant.UNAUTHORIZED);
        }

        Claims claims;
        try {
            claims = JwtUtil.parseJWT(secretKey(), token);
        } catch (ExpiredJwtException e) {
            log.warn("[{}] token expired: {}", getClass().getSimpleName(), e.getMessage());
            throw new UnauthorizedException("登录已过期，请重新登录");
        } catch (SignatureException e) {
            log.warn("[{}] token signature invalid: {}", getClass().getSimpleName(), e.getMessage());
            throw new UnauthorizedException(MessageConstant.UNAUTHORIZED);
        } catch (Exception e) {
            log.warn("[{}] token invalid: {}", getClass().getSimpleName(), e.getMessage());
            throw new UnauthorizedException(MessageConstant.UNAUTHORIZED);
        }

        Long userId = ((Number) claims.get(claimName())).longValue();

        // E4: 检查 token 是否已被加入黑名单（登出后立即失效）
        String jti = claims.getId();
        if (tokenBlacklistService.isBlacklisted(jti)) {
            log.warn("[{}] token 已被加入黑名单: jti={}", getClass().getSimpleName(), jti);
            throw new UnauthorizedException(MessageConstant.UNAUTHORIZED);
        }

        // 写入线程上下文：Service / @AutoFill AOP 用 BaseContext.getCurrentId() 读取
        BaseContext.setCurrentId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        // ⚠️ 务必清线程：线程池复用会泄漏前一个登录者的 ID
        BaseContext.remove();
    }

    protected abstract String tokenHeaderName();

    protected abstract String secretKey();

    protected abstract String claimName();
}
