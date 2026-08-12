package com.stellar.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 员工登录响应体（返回给前端的 token + 员工信息）。
 *
 * 注意：token 用的是 stellar.jwt.admin-secret-key 签发，与 RAG Python 端「三段式校验」的
 * STELLAR_ADMIN_SECRET_KEY 相同 → RAG 管理端 /api/chat 接口也能识别此 token。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "员工登录返回")
public class EmployeeLoginVO implements Serializable {

    @ApiModelProperty("员工 ID")
    private Long id;

    @ApiModelProperty("用户名")
    private String userName;

    @ApiModelProperty("姓名")
    private String name;

    @ApiModelProperty("角色：1 超级管理员 2 运营 3 客服 4 财务")
    private Integer role;

    @ApiModelProperty("JWT access token（前端 header: token=xxx / Authorization=Bearer xxx）")
    private String token;

    @ApiModelProperty("JWT refresh token（用于 access 过期后换新 token）")
    private String refreshToken;
}
