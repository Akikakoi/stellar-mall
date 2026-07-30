package com.stellar.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 收货地址 VO。
 * <p>
 * 字段与 {@link com.stellar.entity.Address} 保持一致，前端可按原有字段访问。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("收货地址 VO")
public class AddressVO implements Serializable {

    @ApiModelProperty("地址 ID")
    private Long id;

    @ApiModelProperty("用户 ID")
    private Long userId;

    @ApiModelProperty("收货人姓名")
    private String consignee;

    @ApiModelProperty("联系电话")
    private String phone;

    @ApiModelProperty("省份")
    private String province;

    @ApiModelProperty("城市")
    private String city;

    @ApiModelProperty("区县")
    private String district;

    @ApiModelProperty("详细地址")
    private String detail;

    @ApiModelProperty("是否默认地址：1=默认 0=非默认")
    private Integer isDefault;

    @ApiModelProperty("创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty("创建人")
    private Long createUser;

    @ApiModelProperty("更新时间")
    private LocalDateTime updateTime;

    @ApiModelProperty("更新人")
    private Long updateUser;
}
