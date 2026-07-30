-- =====================================================
-- 售后系统迁移脚本
-- 执行前提：已存在 stellar_mall 数据库及基础表结构
-- 日期：2026-07-25
-- =====================================================

-- 如果表不存在则创建（兼容已通过主 DDL 建表的场景）
CREATE TABLE IF NOT EXISTS stellar_after_sale (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id        BIGINT        NOT NULL                COMMENT '订单 ID',
    sku_id          BIGINT        NOT NULL                COMMENT '申请售后的 SKU',
    user_id         BIGINT        NOT NULL                COMMENT '申请人用户 ID',
    type            TINYINT       NOT NULL                COMMENT '售后类型：1 仅退款，2 退货退款，3 换货',
    status          TINYINT       NOT NULL DEFAULT 1      COMMENT '售后状态：1 申请，2 商家审核中，3 用户退货中，4 退款中，5 完成，6 已拒绝，7 已取消',
    reason          VARCHAR(255)  NOT NULL                COMMENT '申请原因',
    detail          VARCHAR(1000) DEFAULT NULL            COMMENT '详细描述',
    amount          DECIMAL(10,2) NOT NULL                COMMENT '申请退款金额',
    images          VARCHAR(2000) DEFAULT NULL            COMMENT '凭证图片 JSON',
    audit_user_id   BIGINT        DEFAULT NULL            COMMENT '审核人员工 ID',
    audit_remark    VARCHAR(255)  DEFAULT NULL            COMMENT '审核备注',
    audit_time      DATETIME      DEFAULT NULL            COMMENT '审核时间',
    return_tracking VARCHAR(128)  DEFAULT NULL            COMMENT '退货快递单号',
    refund_no       VARCHAR(128)  DEFAULT NULL            COMMENT '第三方退款流水号',
    refund_time     DATETIME      DEFAULT NULL            COMMENT '退款完成时间',
    exchange_sku_id BIGINT        DEFAULT NULL            COMMENT '换货目标 SKU（换货专用）',
    create_time     DATETIME      NOT NULL                COMMENT '创建时间',
    create_user     BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人',
    update_time     DATETIME      NOT NULL                COMMENT '更新时间',
    update_user     BIGINT        NOT NULL DEFAULT 0      COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_after_sale_order (order_id),
    KEY idx_after_sale_user (user_id),
    KEY idx_after_sale_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后申请表';

-- 售后状态与前端数字码对照：
--   1 = 申请中     2 = 商家审核中   3 = 用户退货中
--   4 = 退款中     5 = 已完成       6 = 已拒绝       7 = 已取消
--
-- 售后类型：
--   1 = 仅退款     2 = 退货退款     3 = 换货
