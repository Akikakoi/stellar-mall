-- ============================================================
-- 为已有商品扩充 SKU 多规格选择
-- 当前大部分商品只有 2-3 个 SKU（颜色+存储混绑），
-- 本脚本为每个 SPU 补充更多独立可选的规格组合
-- ============================================================

USE stellar_mall;

-- 临时表：获取所有 SPU 及其现有 SKU 数
DROP TEMPORARY TABLE IF EXISTS tmp_spu_targets;
CREATE TEMPORARY TABLE tmp_spu_targets (
    spu_id   BIGINT,
    spu_name VARCHAR(200),
    sku_cnt  INT,
    cat_id   BIGINT
);

INSERT INTO tmp_spu_targets (spu_id, spu_name, sku_cnt, cat_id)
SELECT s.id, s.name,
       (SELECT COUNT(*) FROM stellar_sku WHERE spu_id = s.id),
       s.category_id
FROM stellar_spu s
WHERE s.status = 1
  AND (SELECT COUNT(*) FROM stellar_sku WHERE spu_id = s.id) < 4;

-- ============================================================
-- 手机类 — 补充颜色+存储组合
-- ============================================================

-- 星耀 A60 (2SKU → 4SKU) — 补薄荷绿 8+256 + 星空黑 6+128
SELECT @spu_a60_id := id, @spu_a60_nm := name FROM stellar_spu WHERE name = '星耀 A60 5G 学生机' LIMIT 1;
INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, stock, warn_stock, weight_g, sort, status, create_time, create_user, update_time, update_user)
VALUES
(@spu_a60_id, '星耀 A60 · 薄荷绿 · 8+256GB',  '颜色:薄荷绿;内存:8GB;存储:256GB',  '{"颜色":"薄荷绿","内存":"8GB","存储":"256GB"}',  1599.00, 1799.00, 300, 20, 195, 30, 1, NOW(), 1, NOW(), 1),
(@spu_a60_id, '星耀 A60 · 星空黑 · 6+128GB',  '颜色:星空黑;内存:6GB;存储:128GB',  '{"颜色":"星空黑","内存":"6GB","存储":"128GB"}',  1299.00, 1499.00, 400, 20, 195, 40, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 4, total_stock = (SELECT SUM(stock) FROM stellar_sku WHERE spu_id = @spu_a60_id) WHERE id = @spu_a60_id;

-- 星耀 Flip (2SKU → 4SKU) — 补香槟粉 12+512 + 曜石黑 12+256
SELECT @spu_flip_id := id FROM stellar_spu WHERE name = '星耀 Flip 折叠屏' LIMIT 1;
INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, stock, warn_stock, weight_g, sort, status, create_time, create_user, update_time, update_user)
VALUES
(@spu_flip_id, '星耀 Flip · 香槟粉 · 12+512GB',  '颜色:香槟粉;内存:12GB;存储:512GB',  '{"颜色":"香槟粉","内存":"12GB","存储":"512GB"}', 5699.00, 6199.00, 80, 10, 188, 30, 1, NOW(), 1, NOW(), 1),
(@spu_flip_id, '星耀 Flip · 曜石黑 · 12+256GB',  '颜色:曜石黑;内存:12GB;存储:256GB',  '{"颜色":"曜石黑","内存":"12GB","存储":"256GB"}', 4999.00, 5499.00, 120, 10, 188, 40, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 4, total_stock = (SELECT SUM(stock) FROM stellar_sku WHERE spu_id = @spu_flip_id) WHERE id = @spu_flip_id;

-- 星耀 X200 (3SKU → 5SKU) — 补陶瓷白 12+256 + 陶瓷白 16+512
SELECT @spu_x200_id := id FROM stellar_spu WHERE name = '星耀 X200 5G 影像旗舰' LIMIT 1;
INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, stock, warn_stock, weight_g, sort, status, create_time, create_user, update_time, update_user)
VALUES
(@spu_x200_id, '星耀 X200 · 陶瓷白 · 12+256GB',  '颜色:陶瓷白;内存:12GB;存储:256GB',  '{"颜色":"陶瓷白","内存":"12GB","存储":"256GB"}', 6299.00, 6799.00, 120, 10, 223, 40, 1, NOW(), 1, NOW(), 1),
(@spu_x200_id, '星耀 X200 · 陶瓷白 · 16+512GB',  '颜色:陶瓷白;内存:16GB;存储:512GB',  '{"颜色":"陶瓷白","内存":"16GB","存储":"512GB"}', 7299.00, 7799.00, 100, 10, 223, 50, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 5, total_stock = (SELECT SUM(stock) FROM stellar_sku WHERE spu_id = @spu_x200_id) WHERE id = @spu_x200_id;

-- ============================================================
-- 笔记本类 — 补充颜色+存储组合
-- ============================================================

-- ThinkBook 16 (2→4) — 补银色 32G+2T + 深空灰 32G+1T
SELECT @spu_tb16_id := id FROM stellar_spu WHERE name = '星耀 ThinkBook 16 商务本' LIMIT 1;
INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, stock, warn_stock, weight_g, sort, status, create_time, create_user, update_time, update_user)
VALUES
(@spu_tb16_id, '星耀 ThinkBook 16 · 银色 · 32GB+2TB',   '颜色:银色;内存:32GB;存储:2TB',   '{"颜色":"银色","内存":"32GB","存储":"2TB"}',   7699.00, 8199.00, 80, 10, 1800, 30, 1, NOW(), 1, NOW(), 1),
(@spu_tb16_id, '星耀 ThinkBook 16 · 深空灰 · 32GB+1TB', '颜色:深空灰;内存:32GB;存储:1TB', '{"颜色":"深空灰","内存":"32GB","存储":"1TB"}', 6999.00, 7499.00, 120, 10, 1800, 40, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 4, total_stock = (SELECT SUM(stock) FROM stellar_sku WHERE spu_id = @spu_tb16_id) WHERE id = @spu_tb16_id;

-- Creator 14 (2→4)
SELECT @spu_creator_id := id FROM stellar_spu WHERE name = '星耀 Creator 14 设计师本' LIMIT 1;
INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, stock, warn_stock, weight_g, sort, status, create_time, create_user, update_time, update_user)
VALUES
(@spu_creator_id, '星耀 Creator 14 · 月岩白 · 64GB+2TB',  '颜色:月岩白;内存:64GB;存储:2TB',  '{"颜色":"月岩白","内存":"64GB","存储":"2TB"}',  13499.00, 14499.00, 60, 5, 1650, 30, 1, NOW(), 1, NOW(), 1),
(@spu_creator_id, '星耀 Creator 14 · 深空灰 · 64GB+4TB', '颜色:深空灰;内存:64GB;存储:4TB', '{"颜色":"深空灰","内存":"64GB","存储":"4TB"}', 14999.00, 15999.00, 40, 5, 1650, 40, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 4, total_stock = (SELECT SUM(stock) FROM stellar_sku WHERE spu_id = @spu_creator_id) WHERE id = @spu_creator_id;

-- Book 14 学生本 (2→4)
SELECT @spu_book14_id := id FROM stellar_spu WHERE name = '星耀 Book 14 学生本' LIMIT 1;
INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, stock, warn_stock, weight_g, sort, status, create_time, create_user, update_time, update_user)
VALUES
(@spu_book14_id, '星耀 Book 14 · 星空银 · 16GB+1TB',  '颜色:星空银;内存:16GB;存储:1TB',  '{"颜色":"星空银","内存":"16GB","存储":"1TB"}',  4699.00, 4999.00, 180, 15, 1450, 30, 1, NOW(), 1, NOW(), 1),
(@spu_book14_id, '星耀 Book 14 · 雾霾蓝 · 16GB+512GB', '颜色:雾霾蓝;内存:16GB;存储:512GB', '{"颜色":"雾霾蓝","内存":"16GB","存储":"512GB"}', 4299.00, 4699.00, 250, 15, 1450, 40, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 4, total_stock = (SELECT SUM(stock) FROM stellar_sku WHERE spu_id = @spu_book14_id) WHERE id = @spu_book14_id;

-- ============================================================
-- 平板类 — 为每款补全更多 SKU
-- ============================================================

-- Tab Pro (2→4) — 补 WiFi版 12+512 星云灰 + 5G版 12+256 极光蓝  
SELECT @spu_tab_pro_id := id FROM stellar_spu WHERE name = '星耀 Tab Pro 12.4' LIMIT 1;
INSERT INTO stellar_sku (spu_id, name, specs, price, original_price, stock, warn_stock, sort, status, create_time, create_user, update_time, update_user)
VALUES
(@spu_tab_pro_id, '星耀 Tab Pro 12.4 · WiFi版 12G+512G 星云灰', '网络:WiFi;内存:12G;存储:512G;颜色:星云灰', 4799.00, 5099.00, 60, 10, 3, 1, NOW(), 1, NOW(), 1),
(@spu_tab_pro_id, '星耀 Tab Pro 12.4 · 5G版 12G+256G 极光蓝',  '网络:5G;内存:12G;存储:256G;颜色:极光蓝',  4999.00, 5399.00, 50, 10, 4, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 4, total_stock = (SELECT SUM(stock) FROM stellar_sku WHERE spu_id = @spu_tab_pro_id) WHERE id = @spu_tab_pro_id;

-- Tab Air (2→4) — 补 WiFi版 8+256 深空黑 + WiFi版 12+128 银翼白
SELECT @spu_tab_air_id := id FROM stellar_spu WHERE name = '星耀 Tab Air 11' LIMIT 1;
INSERT INTO stellar_sku (spu_id, name, specs, price, original_price, stock, warn_stock, sort, status, create_time, create_user, update_time, update_user)
VALUES
(@spu_tab_air_id, '星耀 Tab Air 11 · WiFi版 8G+256G 深空黑',  '网络:WiFi;内存:8G;存储:256G;颜色:深空黑',  2499.00, 2699.00, 120, 10, 3, 1, NOW(), 1, NOW(), 1),
(@spu_tab_air_id, '星耀 Tab Air 11 · WiFi版 12G+128G 银翼白', '网络:WiFi;内存:12G;存储:128G;颜色:银翼白', 2499.00, 2699.00, 100, 10, 4, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 4, total_stock = (SELECT SUM(stock) FROM stellar_sku WHERE spu_id = @spu_tab_air_id) WHERE id = @spu_tab_air_id;

-- Tab Max (2→4)
SELECT @spu_tab_max_id := id FROM stellar_spu WHERE name = '星耀 Tab Max 14.6' LIMIT 1;
INSERT INTO stellar_sku (spu_id, name, specs, price, original_price, stock, warn_stock, sort, status, create_time, create_user, update_time, update_user)
VALUES
(@spu_tab_max_id, '星耀 Tab Max 14.6 · WiFi版 16G+1T 深空灰', '网络:WiFi;内存:16G;存储:1T;颜色:深空灰', 6799.00, 7299.00, 40, 5, 3, 1, NOW(), 1, NOW(), 1),
(@spu_tab_max_id, '星耀 Tab Max 14.6 · 5G版 16G+512G 极光银',  '网络:5G;内存:16G;存储:512G;颜色:极光银', 6999.00, 7499.00, 35, 5, 4, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 4, total_stock = (SELECT SUM(stock) FROM stellar_sku WHERE spu_id = @spu_tab_max_id) WHERE id = @spu_tab_max_id;

-- Tab Lite (2→4)
SELECT @spu_tab_lite_id := id FROM stellar_spu WHERE name = '星耀 Tab Lite 10.4' LIMIT 1;
INSERT INTO stellar_sku (spu_id, name, specs, price, original_price, stock, warn_stock, sort, status, create_time, create_user, update_time, update_user)
VALUES
(@spu_tab_lite_id, '星耀 Tab Lite 10.4 · WiFi版 6G+256G 星夜灰', '网络:WiFi;内存:6G;存储:256G;颜色:星夜灰', 1499.00, 1699.00, 200, 20, 3, 1, NOW(), 1, NOW(), 1),
(@spu_tab_lite_id, '星耀 Tab Lite 10.4 · WiFi版 8G+128G 晴空蓝', '网络:WiFi;内存:8G;存储:128G;颜色:晴空蓝', 1399.00, 1599.00, 220, 20, 4, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 4, total_stock = (SELECT SUM(stock) FROM stellar_sku WHERE spu_id = @spu_tab_lite_id) WHERE id = @spu_tab_lite_id;

-- ============================================================
-- 刷新所有 SPU 的 minPrice/maxPrice（取 SKU 中的最值）
-- ============================================================
UPDATE stellar_spu s
JOIN (SELECT spu_id, MIN(price) AS pmin, MAX(price) AS pmax FROM stellar_sku WHERE status = 1 GROUP BY spu_id) t
  ON s.id = t.spu_id
SET s.min_price = t.pmin, s.max_price = t.pmax;

-- ============================================================
-- 验证
-- ============================================================
SELECT s.id, s.name, s.sku_count, s.min_price, s.max_price,
       COUNT(sk.id) AS actual_sku,
       SUM(sk.stock) AS total_stock
FROM stellar_spu s
LEFT JOIN stellar_sku sk ON sk.spu_id = s.id
WHERE s.status = 1
GROUP BY s.id
ORDER BY s.id;
