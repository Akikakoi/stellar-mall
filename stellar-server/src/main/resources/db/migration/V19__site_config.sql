-- ============================================================
-- V19: 站点级配置表（stellar_site_config）
-- ------------------------------------------------------------
-- 背景：
--   首页装修模块（stellar_home_module）只承载"区块"配置（轮播、商品坑位等），
--   站点级外观配置（如商城主页背景图）没有存储位。
--   新建通用 key-value 配置表：一次建表，后续站点 logo、主题色等外观配置
--   都可复用同一张表，无需为每个新配置项单独建表。
--
-- 设计：
--   1. config_key 唯一；config_value 存 JSON 串（如 {"bgImage":"https://..."}），
--      键值语义由代码层约定，演进不需要 DDL 变更。
--   2. 无记录 = 使用默认值（默认背景图路径不进库，前端 CSS fallback 即默认），
--      因此"恢复默认"的实现就是删除配置行。
--   3. update_user 记录最后操作人（管理端员工），update_time 自动维护。
-- ============================================================

CREATE TABLE IF NOT EXISTS `stellar_site_config` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    `config_key`  VARCHAR(64)  NOT NULL COMMENT '配置键（home_bg）',
    `config_value` TEXT        COMMENT '配置值 JSON，如 {"bgImage":"https://..."}',
    `remark`      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '配置说明',
    `update_user` BIGINT       COMMENT '最后操作人',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站点级配置';
