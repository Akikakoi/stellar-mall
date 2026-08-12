package com.stellar.service;

import com.stellar.dto.EmployeeCreateDTO;
import com.stellar.dto.EmployeeLoginDTO;
import com.stellar.dto.EmployeeUpdateDTO;
import com.stellar.entity.Employee;
import com.stellar.result.PageResult;
import com.stellar.vo.EmployeeLoginVO;

public interface EmployeeService {

    /** 员工登录：校验密码 → 签发 access + refresh JWT → 返回 VO */
    EmployeeLoginVO login(EmployeeLoginDTO dto);

    /**
     * 用 refresh token 换新的 access + refresh token。
     * 校验 refresh token 有效性与 Redis 存储一致性，单设备登录时旧 refresh 失效。
     * @param refreshToken 前端保存的 refresh token
     * @return 新的登录 VO（含新 access + refresh token）
     */
    EmployeeLoginVO refresh(String refreshToken);

    /** 员工登出（主要用于日志/审计，目前清 BaseContext 即可） */
    default void logout() { /* no-op, JWT 无状态，前端清 localstorage 即可 */ }

    /** 分页查询员工 */
    PageResult page(String name, Integer status, Integer role, int page, int pageSize);

    /** 按 ID 查询员工（用于详情回显、修改） */
    Employee getById(Long id);

    /** 启停用员工（status=1/0） */
    void setStatus(Long id, Integer status);

    /** 新增员工 */
    void create(EmployeeCreateDTO dto);

    /** 修改员工（含密码修改） */
    void update(EmployeeUpdateDTO dto);
}
