package com.stellar.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户消息/通知（订单发货、系统通知等）。
 * 表：stellar_user_message
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMessage implements Serializable {

    private Long id;
    private Long userId;

    /** 消息类型：ORDER_SHIPPED / ORDER_CANCELLED / SYSTEM / COUPON */
    private String type;

    /** 消息标题 */
    private String title;

    /** 消息正文 */
    private String content;

    /** 关联业务 ID（如订单 ID），可空 */
    private Long refId;

    /** 0 未读 / 1 已读 */
    private Integer isRead;

    private LocalDateTime createTime;
}
