package com.stellar.utils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * JWT Token 提取工具：统一兼容业务 header（token / authentication）与
 * Authorization: Bearer 两种传入方式。
 */
public final class TokenUtils {

    private TokenUtils() {
    }

    /** Controller 场景：直接使用已绑定的两个 header 值。 */
    public static String extractToken(String tokenHeader, String authorizationHeader) {
        if (tokenHeader != null && !tokenHeader.isEmpty()) {
            return tokenHeader;
        }
        return extractBearer(authorizationHeader);
    }

    /** 拦截器场景：按业务 token header 名读取，再回退到 Authorization。 */
    public static String extractToken(HttpServletRequest request, String tokenHeaderName) {
        String token = request.getHeader(tokenHeaderName);
        if (token != null && !token.isEmpty()) {
            return token;
        }
        return extractBearer(request.getHeader("Authorization"));
    }

    private static String extractBearer(String authorizationHeader) {
        if (authorizationHeader != null
                && authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorizationHeader.substring(7);
        }
        return null;
    }
}
