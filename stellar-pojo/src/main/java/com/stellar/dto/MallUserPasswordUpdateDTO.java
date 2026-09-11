package com.stellar.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * C 端用户修改密码请求体。
 * <p>
 * 两种验证方式二选一：
 * <ul>
 *   <li>原密码验证：传 oldPassword（常规改密）；</li>
 *   <li>邮箱验证码验证：传 code（验证码发往当前登录邮箱，适用于邮箱验证码注册、从未设置过密码的账号）。</li>
 * </ul>
 * 两者都传时优先使用邮箱验证码。
 * </p>
 */
@Data
@ApiModel(description = "C端用户修改密码请求")
public class MallUserPasswordUpdateDTO implements Serializable {

    @ApiModelProperty("原密码（与 code 二选一）")
    private String oldPassword;

    @ApiModelProperty("邮箱验证码（发往当前登录邮箱，与 oldPassword 二选一）")
    private String code;

    @ApiModelProperty(value = "新密码（明文，6-32 位）", required = true)
    @NotBlank(message = "请输入新密码")
    @Size(min = 6, max = 32, message = "密码长度需为 6-32 位")
    private String newPassword;
}
