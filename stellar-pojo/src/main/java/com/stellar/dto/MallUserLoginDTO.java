package com.stellar.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * C 端用户登录请求体（手机号 + 密码）。
 */
@Data
@ApiModel(description = "C端用户登录请求")
public class MallUserLoginDTO implements Serializable {

    @ApiModelProperty(value = "手机号", required = true, example = "13800138000")
    private String phone;

    @ApiModelProperty(value = "密码（明文）", required = true, example = "123456")
    private String password;
}
