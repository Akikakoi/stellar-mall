-- ============================================================
-- 首页装修模块化 - 首页模块配置表
-- ============================================================

CREATE TABLE IF NOT EXISTS `stellar_home_module` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '模块ID',
    `type`        VARCHAR(32)  NOT NULL DEFAULT 'HOT_PRODUCTS' COMMENT '模块类型：BANNER/HOT_PRODUCTS/NEW_PRODUCTS/CATEGORY_SHOWCASE/COUPON_ENTRY/PRODUCT_GRID/SINGLE_IMAGE',
    `title`       VARCHAR(100) NOT NULL DEFAULT '' COMMENT '模块标题',
    `config`      TEXT         COMMENT '模块配置JSON',
    `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `create_user` BIGINT       COMMENT '创建人',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `update_user` BIGINT       COMMENT '更新人',
    PRIMARY KEY (`id`),
    KEY `idx_type` (`type`),
    KEY `idx_sort` (`sort_order`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='首页装修模块配置';

-- 插入默认模块数据（与现有首页分类展示一致）
INSERT INTO `stellar_home_module` (`type`, `title`, `config`, `sort_order`, `status`) VALUES
('BANNER',           '首页轮播',       '{}',                                                                                       1, 1),
('CATEGORY_SHOWCASE', '智能手机',       '{"categoryName":"智能手机","displayCount":8}',                                               2, 1),
('CATEGORY_SHOWCASE', '笔记本电脑',     '{"categoryName":"笔记本电脑","displayCount":8}',                                             3, 1),
('CATEGORY_SHOWCASE', '平板电脑',       '{"categoryName":"平板电脑","displayCount":8}',                                               4, 1),
('CATEGORY_SHOWCASE', '智能穿戴',       '{"categoryName":"智能穿戴","displayCount":8}',                                               5, 1),
('CATEGORY_SHOWCASE', '生活家电',       '{"categoryName":"生活家电","displayCount":8}',                                               6, 1),
('HOT_PRODUCTS',      '热门推荐',       '{"displayCount":10}',                                                                       7, 1),
('COUPON_ENTRY',      '领券中心',       '{}',                                                                                       8, 1);
