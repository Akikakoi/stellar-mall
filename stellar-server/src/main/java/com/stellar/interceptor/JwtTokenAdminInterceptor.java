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
 * 管理端（Employee）JWT 拦截器。
 * <p>
 * 两处支持 token 传入，任选其一：
 *   1) Header：token=<jwt>（对齐 sky-take-out）
 *   2) Header：Authorization=Bearer <jwt>（对齐 RAG 端）
 * <p>
 * 拦截规则：
 *   - 放行 /admin/employee/login、/health、/doc.html、swagger 等白名单
 *   - 其他接口必须通过校验，失败直接 401（抛 UnauthorizedException，被 GlobalExceptionHandler 包成 Result）
 */
@Slf4j
@Component
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

    private final JwtProperties jwtProperties;

    @Autowired
    public JwtTokenAdminInterceptor(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        // 静态资源 / 非 Controller 请求直接放行
        if (!(handler instanceof HandlerMethod)) return true;

        String token = extractToken(req);
        if (token == null || token.isEmpty()) {
            throw new UnauthorizedException(MessageConstant.UNAUTHORIZED);
        }

        Claims claims;
        try {
            claims = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token);
        } catch (ExpiredJwtException e) {
            log.warn("[JwtTokenAdminInterceptor] token expired: {}", e.getMessage());
            throw new UnauthorizedException("登录已过期，请重新登录");
        } catch (SignatureException e) {
            log.warn("[JwtTokenAdminInterceptor] token signature invalid: {}", e.getMessage());
            throw new UnauthorizedException(MessageConstant.UNAUTHORIZED);
        } catch (Exception e) {
            log.warn("[JwtTokenAdminInterceptor] token invalid: {}", e.getMessage());
            throw new UnauthorizedException(MessageConstant.UNAUTHORIZED);
        }

        Long empId = ((Number) claims.get(JwtClaimsConstant.EMP_ID)).longValue();
        // 写入线程上下文：Service / @AutoFill AOP 用 BaseContext.getCurrentId() 读取
        BaseContext.setCurrentId(empId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        // ⚠️ 务必清线程：线程池复用会泄漏前一个登录者的 ID
        BaseContext.remove();
    }

    private String extractToken(HttpServletRequest req) {
        String t = req.getHeader(jwtProperties.getAdminTokenName()); // 即 "token"
        if (t != null && !t.isEmpty()) return t;

        String auth = req.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return auth.substring(7);
        }
        return null;
    }
}
