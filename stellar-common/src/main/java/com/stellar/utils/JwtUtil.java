package com.stellar.utils;

import com.stellar.constant.JwtClaimsConstant;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 工具类（JJWT 0.12.x + 与 RAG Python 端 PyJWT 约定互通）。
 *
 * ⚠️ 与 RAG Python 端互通约定：
 *   1. 算法：HS256
 *   2. 密钥：原始 UTF-8 字节（不做 Base64 解码）——与 PyJWT jwt.encode(..., str_secret) 默认行为等价
 *   3. claim 字段名：与 JwtClaimsConstant 一致（EMP_ID / USER_ID / ROLE / NAME / NICKNAME 等全大写）
 *   4. 过期：expiration(exp) 在 claims() 之后设置
 *   5. jti：每个 token 自动生成唯一 ID，用于黑名单吊销（E4）
 *   6. type：区分 access / refresh token（E1）
 */
public class JwtUtil {

    /** access token 类型标识 */
    public static final String TYPE_ACCESS = "access";
    /** refresh token 类型标识 */
    public static final String TYPE_REFRESH = "refresh";

    /**
     * 生成 access JWT（JJWT 0.12.x API）。
     * 自动生成 jti 并标记 type=access。
     *
     * @param secretKey 密钥（UTF-8 原始字符串，不要传 Base64 编码后的字符串）
     * @param ttlMillis 有效期（毫秒）
     * @param claims    自定义 claims（EMP_ID / USER_ID / ROLE 等）
     * @return JWT 字符串
     */
    public static String createJWT(String secretKey, long ttlMillis, Map<String, Object> claims) {
        return buildJWT(secretKey, ttlMillis, claims, TYPE_ACCESS);
    }

    /**
     * 生成 refresh JWT。claims 与 access token 相同，但 type=refresh。
     * 用于 E1 refresh 机制：access 过期后用 refresh 换新 token。
     *
     * @param secretKey 密钥（和 access token 共用同一密钥）
     * @param ttlMillis 有效期（毫秒，通常比 access 长）
     * @param claims    自定义 claims（与 access token 一致）
     * @return refresh JWT 字符串
     */
    public static String createRefreshJWT(String secretKey, long ttlMillis, Map<String, Object> claims) {
        return buildJWT(secretKey, ttlMillis, claims, TYPE_REFRESH);
    }

    /**
     * 内部构建 JWT 的统一方法，access/refresh 共用。
     */
    private static String buildJWT(String secretKey, long ttlMillis, Map<String, Object> claims, String type) {
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        long expMillis = System.currentTimeMillis() + ttlMillis;
        Date exp = new Date(expMillis);

        return Jwts.builder()
                .claims(claims)
                .id(UUID.randomUUID().toString())
                .claim(JwtClaimsConstant.TOKEN_TYPE, type)
                .signWith(key)
                .expiration(exp)
                .compact();
    }

    /**
     * 解析 JWT（JJWT 0.12.x API）。
     *
     * @param secretKey 密钥（和生成时保持同一字符串）
     * @param token     JWT 字符串
     * @return Claims
     * @throws io.jsonwebtoken.JwtException 签名错误/过期/格式错误等任何校验失败都会抛
     */
    public static Claims parseJWT(String secretKey, String token) {
        SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
