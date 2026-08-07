package com.stellar.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * C 端用户登录请求体（邮箱 + 密码）。
 */
@Data
@ApiModel(description = "C端用户登录请求")
public class MallUserLoginDTO implements Serializable {

    @ApiModelProperty(value = "邮箱", required = true, example = "user@example.com")
    private String email;

    @ApiModelProperty(value = "密码（明文）", required = true, example = "123456")
    private String password;
}
