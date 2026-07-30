package com.stellar.entity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address implements Serializable {
    private Long id;
    private Long userId;
    private String consignee;    // 收货人
    private String phone;         // 联系电话
    private String province;      // 省
    private String city;          // 市
    private String district;      // 区
    private String detail;        // 详细地址
    private Integer isDefault;    // 1=默认 0=非默认
    private LocalDateTime createTime;
    private Long createUser;
    private LocalDateTime updateTime;
    private Long updateUser;
}