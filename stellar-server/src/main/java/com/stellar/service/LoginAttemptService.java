package com.stellar.service;

/**
 * 登录失败计数与账号临时锁定服务（E2）。
 * <p>
 * 基于 Redis 计数：同一账号连续失败 N 次后临时锁定 M 分钟，锁定期间拒绝登录。
 * 成功登录时清零计数。支持手动解锁。
 */
public interface LoginAttemptService {

    /**
     * 检查账号是否被临时锁定。锁定时抛 BaseException（含剩余分钟数）。
     * @param type 账号类型：employee / mall_user
     * @param account 账号标识（username / email）
     */
    void checkLocked(String type, String account);

    /**
     * 记录一次登录失败。达到阈值时设置锁定。
     * @param type 账号类型
     * @param account 账号标识
     */
    void recordFailure(String type, String account);

    /**
     * 登录成功时清零计数。
     * @param type 账号类型
     * @param account 账号标识
     */
    void clearAttempts(String type, String account);

    /**
     * 手动解锁（管理端调用）。
     * @param type 账号类型
     * @param account 账号标识
     */
    void unlock(String type, String account);
}
