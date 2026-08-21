-- ============================================================
-- 积分抵扣支付功能 - 数据库迁移
-- 日期: 2026-07-28
-- 说明: 
--   1. 订单表增加积分抵扣字段
--   2. 新增积分支付记录表（用于退款追溯）
-- ============================================================

-- 1. stellar_mall_order 增加积分抵扣字段
ALTER TABLE stellar_mall_order
    ADD COLUMN points_deducted INT NOT NULL DEFAULT 0 COMMENT '使用的积分数（冻结后实际扣除）',
    ADD COLUMN points_amount   DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '积分抵扣金额(元), 100积分=1元';

-- 2. 积分支付记录表（可追溯每次积分抵扣与退还）
CREATE TABLE IF NOT EXISTS stellar_points_payment (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    order_id         BIGINT       NOT NULL COMMENT '订单ID',
    user_id          BIGINT       NOT NULL COMMENT '用户ID',
    points           INT          NOT NULL COMMENT '变动的积分数',
    amount           DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '对应的金额',
    type             TINYINT      NOT NULL COMMENT '类型: 1冻结 2实际扣除 3退还 4取消解冻',
    biz_desc         VARCHAR(200) DEFAULT NULL COMMENT '业务描述',
    create_time      DATETIME     NOT NULL,
    INDEX idx_order (order_id),
    INDEX idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分抵扣支付记录（追溯用）';
