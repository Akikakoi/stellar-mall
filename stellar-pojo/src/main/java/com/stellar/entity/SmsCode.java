package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 短信验证码（对应 stellar_sms_code 表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsCode implements Serializable {

    private Long id;
    private String phone;
    private String code;
    private String type;       // LOGIN / REGISTER / BIND
    private Integer used;      // 0 未使用 1 已使用
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
}
