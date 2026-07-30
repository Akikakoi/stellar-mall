-- ============================================================
-- 优惠券表结构修复脚本
-- 适用场景：已通过旧的 stellar_mall_ddl.sql 建库，字段与当前
-- 后端 Coupon.java / CouponMapper.xml 不一致（min_amount/discount_rate/per_limit
-- 对应不上 condition_amount/discount_amount/per_user_limit）导致新增优惠券报
-- “服务器内部错误 / Unknown column”。
-- 说明：
--   1. 执行前请自行备份数据库；
--   2. 若优惠券表已有业务数据，旧表数据会被保留到 _backup 表，可手动核对；
--   3. 若此前从未添加过优惠券，直接执行即可。
-- ============================================================

USE stellar_mall;

-- ------------------------------------------------------------
-- 1. 修复优惠券模板表 stellar_coupon
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_coupon_backup_20260721;
RENAME TABLE stellar_coupon TO stellar_coupon_backup_20260721;

CREATE TABLE stellar_coupon (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    name             VARCHAR(100)  NOT NULL                COMMENT '优惠券名称',
    type             TINYINT       NOT NULL DEFAULT 1      COMMENT '类型：1 满减券，2 折扣券',
    condition_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '使用门槛：满多少可用（满减）',
    discount_amount  DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '满减金额 / 折扣比例（0.85=85折）',
    total_count      INT           NOT NULL                COMMENT '总发放数量',
    received_count   INT           NOT NULL DEFAULT 0      COMMENT '已领取数量',
    used_count       INT           NOT NULL DEFAULT 0      COMMENT '已使用数量',
    per_user_limit   INT           NOT NULL DEFAULT 1      COMMENT '每人限领张数',
    start_time       DATETIME      NOT NULL                COMMENT '领取开始时间',
    end_time         DATETIME      NOT NULL                COMMENT '领取结束时间',
    status           TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 启用，0 停用',
    create_time      DATETIME      NOT NULL                COMMENT '创建时间',
    create_user      BIGINT        NOT NULL                COMMENT '创建人',
    update_time      DATETIME      NOT NULL                COMMENT '更新时间',
    update_user      BIGINT        NOT NULL                COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_coupon_status (status, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板表';

INSERT INTO stellar_coupon (
    id, name, type,
    condition_amount,
    discount_amount,
    total_count, received_count, used_count,
    per_user_limit,
    start_time, end_time, status,
    create_time, create_user, update_time, update_user
)
SELECT
    id, name, type,
    COALESCE(min_amount, 0),
    COALESCE(discount_amount, discount_rate, 0),
    total_count, received_count, used_count,
    COALESCE(per_limit, 1),
    start_time, end_time, status,
    create_time, create_user, update_time, update_user
FROM stellar_coupon_backup_20260721;


-- ------------------------------------------------------------
-- 2. 修复用户领券记录表 stellar_user_coupon
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_user_coupon_backup_20260721;
RENAME TABLE stellar_user_coupon TO stellar_user_coupon_backup_20260721;

CREATE TABLE stellar_user_coupon (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT        NOT NULL                COMMENT '用户 ID',
    coupon_id   BIGINT        NOT NULL                COMMENT '券模板 ID',
    status      TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 未使用，2 已使用，3 已过期',
    order_id    BIGINT        DEFAULT NULL            COMMENT '使用的订单 ID',
    used_time   DATETIME      DEFAULT NULL            COMMENT '使用时间',
    create_time DATETIME      NOT NULL                COMMENT '创建时间',
    create_user BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人',
    update_time DATETIME      NOT NULL                COMMENT '更新时间',
    update_user BIGINT        NOT NULL DEFAULT 0      COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_user_coupon_user (user_id),
    KEY idx_user_coupon_coupon (coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户领券记录';

INSERT INTO stellar_user_coupon (
    id, user_id, coupon_id, status,
    order_id, used_time,
    create_time, create_user, update_time, update_user
)
SELECT
    id, user_id, coupon_id, status,
    used_order_id, used_time,
    create_time, create_user, update_time, update_user
FROM stellar_user_coupon_backup_20260721;

-- ------------------------------------------------------------
-- 3. （可选）确认无误后删除备份表
-- ------------------------------------------------------------
-- DROP TABLE IF EXISTS stellar_coupon_backup_20260721;
-- DROP TABLE IF EXISTS stellar_user_coupon_backup_20260721;
