package com.stellar.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
@ApiModel("C端用户资料更新 DTO（nickname/avatar 都是可选，传哪个改哪个）")
public class MallUserProfileUpdateDTO implements Serializable {

    @ApiModelProperty("昵称（可选）")
    private String nickname;

    @ApiModelProperty("头像 URL（可选，当前表暂未存储，可扩展）")
    private String avatar;
}
