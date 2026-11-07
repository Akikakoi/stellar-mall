-- ============================================================
-- V29: 补齐 stellar_mall_order.is_refunded
-- 说明：完整 DDL 新建的库已包含该列；本迁移只服务已有 Flyway 历史的库。
-- 使用 information_schema + PREPARE，保证重复执行/列已存在时不报错。
-- ============================================================

SET @table_exists := (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'stellar_mall_order'
);
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'stellar_mall_order'
      AND column_name = 'is_refunded'
);
SET @ddl := IF(
    @table_exists = 0,
    'SELECT 1',
    IF(
        @col_exists = 0,
        'ALTER TABLE stellar_mall_order ADD COLUMN is_refunded TINYINT NOT NULL DEFAULT 0 COMMENT ''是否整单退款：0 否 1 是''',
        'SELECT 1'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
