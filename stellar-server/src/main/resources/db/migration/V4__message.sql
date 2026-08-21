-- 迁移：订单表增加物流字段 + 新增用户消息表
-- 执行方式：mysql -u stellar -p stellar_mall < migration_20260724_msg.sql

-- 1. 订单表增加物流字段
ALTER TABLE stellar_mall_order
    ADD COLUMN tracking_no VARCHAR(64) DEFAULT NULL COMMENT '快递单号' AFTER remark,
    ADD COLUMN delivery_company VARCHAR(32) DEFAULT NULL COMMENT '快递公司' AFTER tracking_no,
    ADD COLUMN delivery_time DATETIME DEFAULT NULL COMMENT '发货时间' AFTER delivery_company;

-- 2. 用户消息表
CREATE TABLE IF NOT EXISTS stellar_user_message (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL               COMMENT '用户 ID',
    type        VARCHAR(32)  NOT NULL               COMMENT '类型：ORDER_SHIPPED / ORDER_CANCELLED / SYSTEM / COUPON',
    title       VARCHAR(200) NOT NULL               COMMENT '消息标题',
    content     VARCHAR(1000) DEFAULT ''            COMMENT '消息正文',
    ref_id      BIGINT       DEFAULT NULL           COMMENT '关联业务 ID（如订单 ID）',
    is_read     TINYINT      NOT NULL DEFAULT 0     COMMENT '0 未读 / 1 已读',
    create_time DATETIME     NOT NULL               COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_msg_user (user_id),
    KEY idx_user_msg_unread (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户消息通知';
