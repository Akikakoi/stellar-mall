package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * C 端用户（会员）。表：stellar_mall_user。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MallUser implements Serializable {

    private Long id;

    /** 手机号（可选，通知用） */
    private String phone;

    /** 邮箱（登录账号，唯一） */
    private String email;

    /** 昵称 */
    private String nickname;

    /** BCrypt 哈希后的密码（永不返回前端） */
    private String password;

    /** 1 正常 0 锁定 */
    private Integer status;

    private LocalDateTime createTime;
    private Long          createUser;
    private LocalDateTime updateTime;
    private Long          updateUser;
}
