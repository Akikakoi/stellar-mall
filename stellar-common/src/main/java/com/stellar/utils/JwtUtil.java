package com.stellar.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类（100% 对齐 sky-take-out 版本 + 与 RAG Python 端 PyJWT 约定互通）。
 *
 * ⚠️ 与 RAG Python 端互通约定：
 *   1. 算法：HS256
 *   2. 密钥：原始 UTF-8 字节（不做 Base64 解码）——与 PyJWT jwt.encode(..., str_secret) 默认行为等价
 *   3. claim 字段名：与 JwtClaimsConstant 一致（EMP_ID / USER_ID / ROLE / NAME / NICKNAME 等全大写）
 *   4. 过期：setExpiration(exp)，会覆盖 claims 里同名的 exp（Java 端 setClaims 要在 setExpiration 之前）
 */
public class JwtUtil {

    /**
     * 生成 JWT。
     *
     * @param secretKey 密钥（UTF-8 原始字符串，不要传 Base64 编码后的字符串）
     * @param ttlMillis 有效期（毫秒）
     * @param claims    自定义 claims（EMP_ID / USER_ID / ROLE 等）
     * @return JWT 字符串
     */
    public static String createJWT(String secretKey, long ttlMillis, Map<String, Object> claims) {
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.HS256;
        long expMillis = System.currentTimeMillis() + ttlMillis;
        Date exp = new Date(expMillis);

        JwtBuilder builder = Jwts.builder()
                // ⚠️ setClaims 必须在最前面：jjwt 0.9.1 setExpiration/setIssuer 等都会写入 claims，
                // 如果先 setExpiration 再 setClaims，整份 claims 会被整表覆盖，exp 就没了。
                .setClaims(claims)
                .signWith(signatureAlgorithm, secretKey.getBytes(StandardCharsets.UTF_8))
                .setExpiration(exp);

        return builder.compact();
    }

    /**
     * 解析 JWT。
     *
     * @param secretKey 密钥（和生成时保持同一字符串）
     * @param token     JWT 字符串
     * @return Claims
     * @throws io.jsonwebtoken.JwtException 签名错误/过期/格式错误等任何校验失败都会抛
     */
    public static Claims parseJWT(String secretKey, String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                .parseClaimsJws(token)
                .getBody();
        return claims;
    }
}
