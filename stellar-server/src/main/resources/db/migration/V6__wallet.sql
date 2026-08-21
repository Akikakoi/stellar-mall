-- =====================================================
-- 钱包系统迁移脚本
-- 2026-07-25
-- =====================================================

-- 钱包账户表（一个用户一条记录）
CREATE TABLE IF NOT EXISTS stellar_wallet (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT        NOT NULL                COMMENT '用户 ID',
    balance       DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '可用余额（元）',
    frozen        DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '冻结金额（元）',
    total_recharge DECIMAL(12,2) NOT NULL DEFAULT 0.00  COMMENT '累计充值',
    total_spent   DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '累计消费',
    version       INT           NOT NULL DEFAULT 0       COMMENT '乐观锁版本号',
    create_time   DATETIME      NOT NULL                COMMENT '创建时间',
    create_user   BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人',
    update_time   DATETIME      NOT NULL                COMMENT '更新时间',
    update_user   BIGINT        NOT NULL DEFAULT 0      COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户钱包';

-- 钱包交易流水表
CREATE TABLE IF NOT EXISTS stellar_wallet_transaction (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    wallet_id       BIGINT        NOT NULL                COMMENT '钱包 ID',
    user_id         BIGINT        NOT NULL                COMMENT '用户 ID（冗余加速查询）',
    type            TINYINT       NOT NULL                COMMENT '交易类型：1 充值，2 消费，3 退款，4 提现',
    amount          DECIMAL(12,2) NOT NULL                COMMENT '交易金额（充值为正，消费为负）',
    balance_after   DECIMAL(12,2) NOT NULL                COMMENT '交易后余额',
    channel         VARCHAR(16)   DEFAULT NULL            COMMENT '渠道：WECHAT/ALIPAY/WALLET/ADMIN',
    biz_type        VARCHAR(32)   DEFAULT NULL            COMMENT '关联业务：RECHARGE/ORDER/REFUND',
    biz_id          BIGINT        DEFAULT NULL            COMMENT '关联业务 ID',
    remark          VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    create_time     DATETIME      NOT NULL                COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_wtx_wallet (wallet_id),
    KEY idx_wtx_user (user_id),
    KEY idx_wtx_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包交易流水';
