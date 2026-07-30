-- ============================================================
-- 短信/邮件通知系统
-- ============================================================

-- 短信验证码表
CREATE TABLE IF NOT EXISTS `stellar_sms_code` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `phone`       VARCHAR(20)  NOT NULL COMMENT '手机号',
    `code`        VARCHAR(10)  NOT NULL COMMENT '验证码',
    `type`        VARCHAR(20)  NOT NULL DEFAULT 'LOGIN' COMMENT '类型：LOGIN/REGISTER/BIND',
    `used`        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已使用：0否 1是',
    `expire_time` DATETIME     NOT NULL COMMENT '过期时间',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (`id`),
    KEY `idx_phone_type` (`phone`, `type`),
    KEY `idx_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='短信验证���';

-- 通知日志表
CREATE TABLE IF NOT EXISTS `stellar_notification_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'ID',
    `user_id`       BIGINT       COMMENT '接收用户ID',
    `phone`         VARCHAR(20)  COMMENT '接收手机号',
    `email`         VARCHAR(100) COMMENT '接收邮箱',
    `channel`       VARCHAR(20)  NOT NULL DEFAULT 'SMS' COMMENT '渠道：SMS/EMAIL',
    `type`          VARCHAR(30)  NOT NULL COMMENT '类型：VERIFY_CODE/ORDER_SHIPPED/ORDER_RECEIVED/COUPON_EXPIRE/MARKETING',
    `title`         VARCHAR(200) COMMENT '标题（邮件用）',
    `content`       TEXT         NOT NULL COMMENT '通知内容',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '发送状态：0待发送 1成功 2失败',
    `error_msg`     VARCHAR(500) COMMENT '失败原因',
    `send_time`     DATETIME     COMMENT '实际发送时间',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_type` (`type`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知日志';

-- 给 stellar_mall_user 表加 email 字段（如不存在）
ALTER TABLE stellar_mall_user ADD COLUMN IF NOT EXISTS `email` VARCHAR(100) COMMENT '邮箱' AFTER `phone`;
