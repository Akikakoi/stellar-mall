package com.stellar.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 员工登录请求体。
 */
@Data
@ApiModel(description = "员工登录请求")
public class EmployeeLoginDTO implements Serializable {

    @ApiModelProperty(value = "用户名", required = true, example = "admin")
    private String username;

    @ApiModelProperty(value = "密码（明文）", required = true, example = "123456")
    private String password;
}
