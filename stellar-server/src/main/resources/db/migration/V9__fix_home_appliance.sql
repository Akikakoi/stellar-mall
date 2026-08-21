-- ============================================================
-- 修复：商城主页缺少"生活家电"分类展示
-- 根因分析：
--   1. stellar_home_module 表漏配了生活家电 CATEGORY_SHOWCASE 条目
--   2. 之前误配成"家用电器"，但数据库实际分类名是"生活家电"（add_more_products.sql, id=9）
--   3. HomeModuleServiceImpl.listEnabled() 有 @Cacheable 缓存，直接 SQL 需重启后端
-- 日期：2026-07-27
-- ============================================================

-- 1. 删除之前误配的"家用电器"条目（如果存在）
DELETE FROM stellar_home_module WHERE type = 'CATEGORY_SHOWCASE' AND title = '家用电器';

-- 2. 确保热门推荐、领券中心的 sort_order 正确
UPDATE stellar_home_module SET sort_order = 7 WHERE type = 'HOT_PRODUCTS' AND title = '热门推荐';
UPDATE stellar_home_module SET sort_order = 8 WHERE type = 'COUPON_ENTRY' AND title = '领券中心';

-- 3. 插入生活家电分类展示模块
INSERT INTO stellar_home_module (type, title, config, sort_order, status)
VALUES ('CATEGORY_SHOWCASE', '生活家电', '{"categoryName":"生活家电","displayCount":8}', 6, 1);

-- 4. 验证
SELECT id, type, title, LEFT(config, 50) AS config_preview, sort_order, status
FROM stellar_home_module
ORDER BY sort_order;

-- ⚠️ 执行完 SQL 后必须重启 Spring Boot 后端！
-- listEnabled() 的 @Cacheable 缓存不会因 SQL 操作而失效。
