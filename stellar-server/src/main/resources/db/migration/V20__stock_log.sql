-- ============================================================
-- V20: 库存变动流水表（stellar_stock_log）
-- ------------------------------------------------------------
-- 背景：
--   管理端调库存直接 UPDATE stellar_sku，无任何变动记录，
--   出问题后无法追溯"谁在什么时候做了什么操作"。
--   新建流水表，每次库存变动（入库/出库/盘盈/盘亏/调整）自动写入一条记录。
--
-- 设计要点：
--   1. quantity 为正表示库存增加（入库/盘盈），为负表示减少（出库/盘亏）
--   2. stock_before / stock_after 记录变动前后的库存快照，便于对账
--   3. business_type + business_no 支持关联业务单据（采购单、销售单等），
--      无关联时可为 NULL
-- ============================================================

CREATE TABLE IF NOT EXISTS `stellar_stock_log` (
    `id`            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    `sku_id`        BIGINT        NOT NULL                COMMENT 'SKU ID',
    `type`          TINYINT       NOT NULL                COMMENT '变动类型：1 入库，2 出库，3 盘盈，4 盘亏，5 调整',
    `quantity`      INT           NOT NULL                COMMENT '变动数量（正数增加，负数减少）',
    `stock_before`  INT           NOT NULL                COMMENT '变动前库存',
    `stock_after`   INT           NOT NULL                COMMENT '变动后库存',
    `remark`        VARCHAR(255)  DEFAULT NULL            COMMENT '备注',
    `business_type` VARCHAR(32)   DEFAULT NULL            COMMENT '业务类型：PURCHASE_IN / SALE_OUT / INVENTORY_PROFIT / INVENTORY_LOSS / ADJUSTMENT',
    `business_no`   VARCHAR(64)   DEFAULT NULL            COMMENT '关联业务单号',
    `create_time`   DATETIME      NOT NULL                COMMENT '创建时间',
    `create_user`   BIGINT        NOT NULL DEFAULT 0      COMMENT '操作人',
    PRIMARY KEY (`id`),
    KEY `idx_stock_log_sku` (`sku_id`),
    KEY `idx_stock_log_type` (`type`),
    KEY `idx_stock_log_business` (`business_type`, `business_no`),
    KEY `idx_stock_log_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存变动流水表';