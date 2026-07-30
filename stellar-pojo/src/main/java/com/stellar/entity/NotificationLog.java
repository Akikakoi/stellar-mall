package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知日志（对应 stellar_notification_log 表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLog implements Serializable {

    private Long id;
    private Long userId;
    private String phone;
    private String email;
    private String channel;     // SMS / EMAIL
    private String type;        // VERIFY_CODE / ORDER_SHIPPED / ORDER_RECEIVED / COUPON_EXPIRE / MARKETING
    private String title;
    private String content;
    private Integer status;     // 0 待发送 1 成功 2 失败
    private String errorMsg;
    private LocalDateTime sendTime;
    private LocalDateTime createTime;
}
