package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 员工实体（商城后台：超级管理员/运营/客服/财务）。
 * 对应表：stellar_employee。公共 4 字段由 @AutoFill AOP 自动注入。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Employee implements Serializable {

    private Long id;

    /** 登录用户名（唯一） */
    private String username;

    /** 姓名 */
    private String name;

    /** BCrypt 哈希后的密码（永远不返回给前端 VO） */
    private String passwordHash;

    private String phone;
    private String sex;
    private String idNumber;
    private String avatar;

    /** 1 启用，0 锁定 */
    private Integer status;

    /** 1 超级管理员 2 运营 3 客服 4 财务 */
    private Integer role;

    private LocalDateTime createTime;
    private Long          createUser;
    private LocalDateTime updateTime;
    private Long          updateUser;
}
