package com.stellar.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 更新收货地址请求 DTO。
 */
@Data
@ApiModel("更新收货地址请求")
public class AddressUpdateDTO implements Serializable {

    @NotNull(message = "地址 ID 不能为空")
    @ApiModelProperty(value = "地址 ID", required = true)
    private Long id;

    @NotBlank(message = "收货人姓名不能为空")
    @Size(max = 50, message = "收货人姓名最多 50 字符")
    @ApiModelProperty(value = "收货人姓名", required = true)
    private String consignee;

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Size(max = 20, message = "联系电话最多 20 字符")
    @ApiModelProperty(value = "联系电话", required = true)
    private String phone;

    @Size(max = 50, message = "省份最多 50 字符")
    @ApiModelProperty("省份")
    private String province;

    @Size(max = 50, message = "城市最多 50 字符")
    @ApiModelProperty("城市")
    private String city;

    @Size(max = 50, message = "区县最多 50 字符")
    @ApiModelProperty("区县")
    private String district;

    @NotBlank(message = "详细地址不能为空")
    @Size(max = 255, message = "详细地址最多 255 字符")
    @ApiModelProperty(value = "详细地址", required = true)
    private String detail;

    @NotNull(message = "是否默认地址不能为空")
    @ApiModelProperty(value = "是否默认地址：1=默认 0=非默认", required = true)
    private Integer isDefault;
}
