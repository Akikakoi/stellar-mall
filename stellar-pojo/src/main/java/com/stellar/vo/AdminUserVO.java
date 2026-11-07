package com.stellar.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理端 C 端用户列表 VO（不包含密码）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserVO implements Serializable {
    private Long id;
    private String email;
    private String nickname;
    private Integer status;
    private String createTime;
}