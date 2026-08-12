package com.stellar.service;

/**
 * JWT 黑名单服务（E4）。
 * <p>
 * 用户登出时把 access / refresh token 的 jti 写入黑名单，TTL 与 token 剩余有效期一致。
 * 拦截器在每次请求时 {@link #isBlacklisted} 检查当前 token 的 jti，命中则拒绝访问。
 * <p>
 * 设计要点：
 *   1. Redis 不可用时不阻断主流程（降级放行，由 JWT 本身的签名/过期校验兜底）
 *   2. TTL 与 token 剩余有效期对齐，避免无限增长
 *   3. 仅靠 jti 标识，token 本身仍然是 stateless 的
 */
public interface TokenBlacklistService {

    /**
     * 把一个 JWT 加入黑名单。
     *
     * @param token JWT 字符串（必须是可解析的、未过期的 token）
     */
    void blacklist(String token);

    /**
     * 检查给定 jti 是否在黑名单中。
     *
     * @param jti JWT 的 jti claim
     * @return true 在黑名单中（应拒绝）；false 不在或 Redis 不可用（降级放行）
     */
    boolean isBlacklisted(String jti);
}
