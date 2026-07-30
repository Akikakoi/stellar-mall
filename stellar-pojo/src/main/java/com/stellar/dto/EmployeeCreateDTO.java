package com.stellar.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
@ApiModel(description = "员工新增请求")
public class EmployeeCreateDTO implements Serializable {

    @ApiModelProperty(value = "用户名", required = true, example = "admin")
    private String username;

    @ApiModelProperty(value = "密码（明文）", required = true, example = "123456")
    private String password;

    @ApiModelProperty(value = "姓名", required = true, example = "张三")
    private String name;

    @ApiModelProperty(value = "手机号", example = "13800138000")
    private String phone;

    @ApiModelProperty(value = "性别：0保密 1男 2女", example = "1")
    private Integer sex;

    @ApiModelProperty(value = "身份证号", example = "110101199001011234")
    private String idNumber;

    @ApiModelProperty(value = "头像URL", example = "https://example.com/avatar.jpg")
    private String avatar;

    @ApiModelProperty(value = "状态：1启用 0禁用", example = "1")
    private Integer status;

    @ApiModelProperty(value = "角色：1超级管理员 2运营 3客服 4财务", example = "2")
    private Integer role;
}