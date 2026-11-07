package com.stellar.service;

import com.stellar.result.PageResult;

/**
 * 管理端 C 端用户管理。
 */
public interface AdminUserService {

    /** 分页查询 C 端用户。 */
    PageResult page(String keyword, Integer status, int page, int pageSize);

    /** 启用/锁定用户（status: 1 正常 0 锁定）。 */
    void setStatus(Long id, Integer status, Long operatorId);
}