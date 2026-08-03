-- ============================================================================
-- 星耀商城 - 插入 4 款手机产品（SPU + SKU）
-- 分类：手机数码(id=22) → 智能手机(id=30)
-- 已有产品：X100 Pro(¥3,999-4,999)
-- ============================================================================

START TRANSACTION;

-- ===================== 1. 星耀 X200 5G 影像旗舰 (id=11) =====================
INSERT INTO stellar_spu (id, name, sub_title, category_id, category2_id, description, description_md,
    main_image, sub_images, total_stock, sku_count, min_price, max_price,
    is_new, is_hot, sort, status, on_shelf_time, create_time, create_user, update_time, update_user)
VALUES (11,
    '星耀 X200 5G 影像旗舰',
    '骁龙 8 Gen4 · 徕卡联名 · 1英寸大底 · 全焦段4K 120fps',
    22, 30,
    '<p>星耀 X200 是星耀品牌的影像旗舰手机，与徕卡联合研发的光学系统，搭载 1 英寸大底传感器，为专业摄影师和摄影爱好者打造。</p>',
    '# 星耀 X200 5G 影像旗舰\n\n## 核心配置\n\n- 骁龙 8 Gen4 3nm 处理器\n- 徕卡联合光学系统，1英寸大底\n- 5400mAh 硅碳负极电池\n- 120W 有线 + 50W 无线充电\n- IP68 防水防尘',
    'https://web-akikakoi.oss-cn-beijing.aliyuncs.com/stellar-mall/spu/x200-main.png',
    'https://web-akikakoi.oss-cn-beijing.aliyuncs.com/stellar-mall/spu/x200-back.png;https://web-akikakoi.oss-cn-beijing.aliyuncs.com/stellar-mall/spu/x200-side.png',
    210, 5, 5499.00, 6999.00,
    1, 1, 100, 1, NOW(), NOW(), 0, NOW(), 0);

INSERT INTO stellar_sku (id, spu_id, name, specs, price, original_price, stock, sort, status, create_time, create_user, update_time, update_user)
VALUES
(28, 11, '星耀 X200 Pro · 钛黑 · 12+256GB', '颜色:钛黑;内存:12GB;存储:256GB', 5499.00, 5999.00, 50, 1, 1, NOW(), 0, NOW(), 0),
(29, 11, '星耀 X200 Pro · 陶瓷白 · 12+256GB', '颜色:陶瓷白;内存:12GB;存储:256GB', 5499.00, 5999.00, 50, 2, 1, NOW(), 0, NOW(), 0),
(30, 11, '星耀 X200 Pro · 钛黑 · 16+512GB', '颜色:钛黑;内存:16GB;存储:512GB', 6299.00, 6799.00, 40, 3, 1, NOW(), 0, NOW(), 0),
(31, 11, '星耀 X200 Pro · 陶瓷白 · 16+512GB', '颜色:陶瓷白;内存:16GB;存储:512GB', 6299.00, 6799.00, 40, 4, 1, NOW(), 0, NOW(), 0),
(32, 11, '星耀 X200 Pro · 钛黑 · 16+1TB',   '颜色:钛黑;内存:16GB;存储:1TB',   6999.00, 7499.00, 30, 5, 1, NOW(), 0, NOW(), 0);

-- ===================== 2. 星耀 Note 14 Pro (id=12) =====================
INSERT INTO stellar_spu (id, name, sub_title, category_id, category2_id, description, description_md,
    main_image, sub_images, total_stock, sku_count, min_price, max_price,
    is_new, is_hot, sort, status, on_shelf_time, create_time, create_user, update_time, update_user)
VALUES (12,
    '星耀 Note 14 Pro',
    '骁龙 8s Gen4 · 2亿像素主摄 · 5500mAh · 120W快充',
    22, 30,
    '<p>星耀 Note 14 Pro 定位中高端全能机型，主打 2 亿像素主摄和超长续航。轻薄机身内塞入 5500mAh 大电池，是同价位综合体验最均衡的选择。</p>',
    '# 星耀 Note 14 Pro\n\n## 核心配置\n\n- 骁龙 8s Gen4 处理器\n- 2亿像素主摄(OIS)\n- 5500mAh 大电池\n- 120W 有线快充\n- 7.8mm 轻薄机身',
    'https://web-akikakoi.oss-cn-beijing.aliyuncs.com/stellar-mall/spu/note14pro-main.png',
    'https://web-akikakoi.oss-cn-beijing.aliyuncs.com/stellar-mall/spu/note14pro-back.png;https://web-akikakoi.oss-cn-beijing.aliyuncs.com/stellar-mall/spu/note14pro-side.png',
    360, 4, 2499.00, 2899.00,
    0, 1, 80, 1, NOW(), NOW(), 0, NOW(), 0);

INSERT INTO stellar_sku (id, spu_id, name, specs, price, original_price, stock, sort, status, create_time, create_user, update_time, update_user)
VALUES
(33, 12, '星耀 Note 14 Pro · 星夜黑 · 8+128GB',  '颜色:星夜黑;内存:8GB;存储:128GB',  2499.00, 2799.00, 100, 1, 1, NOW(), 0, NOW(), 0),
(34, 12, '星耀 Note 14 Pro · 冰霜银 · 8+128GB',  '颜色:冰霜银;内存:8GB;存储:128GB',  2499.00, 2799.00, 100, 2, 1, NOW(), 0, NOW(), 0),
(35, 12, '星耀 Note 14 Pro · 星夜黑 · 12+256GB', '颜色:星夜黑;内存:12GB;存储:256GB', 2899.00, 3199.00, 80,  3, 1, NOW(), 0, NOW(), 0),
(36, 12, '星耀 Note 14 Pro · 冰霜银 · 12+256GB', '颜色:冰霜银;内存:12GB;存储:256GB', 2899.00, 3199.00, 80,  4, 1, NOW(), 0, NOW(), 0);

-- ===================== 3. 星耀 A60 5G 学生机 (id=13) =====================
INSERT INTO stellar_spu (id, name, sub_title, category_id, category2_id, description, description_md,
    main_image, sub_images, total_stock, sku_count, min_price, max_price,
    is_new, is_hot, sort, status, on_shelf_time, create_time, create_user, update_time, update_user)
VALUES (13,
    '星耀 A60 5G 学生机',
    '天玑 7300 · 5000mAh · 家长管控 · 全局DC调光',
    22, 30,
    '<p>专为青少年和学生打造的入门 5G 手机。内置家长管控功能，支持护眼模式、应用时长管理、定位追踪。</p>',
    '# 星耀 A60 5G 学生机\n\n## 核心配置\n\n- 天玑 7300 处理器\n- 5000mAh 大电池\n- 家长管控模式\n- 全局 DC 调光护眼屏\n- 学习模式',
    'https://web-akikakoi.oss-cn-beijing.aliyuncs.com/stellar-mall/spu/a60-main.png',
    'https://web-akikakoi.oss-cn-beijing.aliyuncs.com/stellar-mall/spu/a60-back.png',
    540, 4, 1199.00, 1499.00,
    1, 0, 50, 1, NOW(), NOW(), 0, NOW(), 0);

INSERT INTO stellar_sku (id, spu_id, name, specs, price, original_price, stock, sort, status, create_time, create_user, update_time, update_user)
VALUES
(37, 13, '星耀 A60 5G · 星空蓝 · 6+128GB', '颜色:星空蓝;内存:6GB;存储:128GB',  1199.00, 1299.00, 150, 1, 1, NOW(), 0, NOW(), 0),
(38, 13, '星耀 A60 5G · 薄荷白 · 6+128GB', '颜色:薄荷白;内存:6GB;存储:128GB',  1199.00, 1299.00, 150, 2, 1, NOW(), 0, NOW(), 0),
(39, 13, '星耀 A60 5G · 星空蓝 · 8+256GB', '颜色:星空蓝;内存:8GB;存储:256GB',  1499.00, 1699.00, 120, 3, 1, NOW(), 0, NOW(), 0),
(40, 13, '星耀 A60 5G · 薄荷白 · 8+256GB', '颜色:薄荷白;内存:8GB;存储:256GB',  1499.00, 1699.00, 120, 4, 1, NOW(), 0, NOW(), 0);

-- ===================== 4. 星耀 Flip 折叠屏 (id=14) =====================
INSERT INTO stellar_spu (id, name, sub_title, category_id, category2_id, description, description_md,
    main_image, sub_images, total_stock, sku_count, min_price, max_price,
    is_new, is_hot, sort, status, on_shelf_time, create_time, create_user, update_time, update_user)
VALUES (14,
    '星耀 Flip 折叠屏',
    '骁龙 8 Gen3 · 6.8寸内屏 · 3.6寸外屏 · 50万次折叠认证',
    22, 30,
    '<p>星耀首款竖向折叠屏手机，翻盖式设计致敬经典。展开是 6.8 寸旗舰内屏，折叠后仅掌心大小。3.6 寸外屏支持全功能操作。</p>',
    '# 星耀 Flip 折叠屏\n\n## 核心配置\n\n- 骁龙 8 Gen3 处理器\n- 6.8寸内屏 + 3.6寸外屏\n- 50万次折叠认证(莱茵)\n- UTG 超薄柔性玻璃\n- IPX8 防水',
    'https://web-akikakoi.oss-cn-beijing.aliyuncs.com/stellar-mall/spu/flip-main.png',
    'https://web-akikakoi.oss-cn-beijing.aliyuncs.com/stellar-mall/spu/flip-folded.png;https://web-akikakoi.oss-cn-beijing.aliyuncs.com/stellar-mall/spu/flip-open.png',
    140, 4, 5199.00, 5699.00,
    1, 1, 95, 1, NOW(), NOW(), 0, NOW(), 0);

INSERT INTO stellar_sku (id, spu_id, name, specs, price, original_price, stock, sort, status, create_time, create_user, update_time, update_user)
VALUES
(41, 14, '星耀 Flip · 星夜黑 · 12+256GB',    '颜色:星夜黑;内存:12GB;存储:256GB',   5199.00, 5699.00, 40, 1, 1, NOW(), 0, NOW(), 0),
(42, 14, '星耀 Flip · 薰衣草紫 · 12+256GB',  '颜色:薰衣草紫;内存:12GB;存储:256GB', 5199.00, 5699.00, 40, 2, 1, NOW(), 0, NOW(), 0),
(43, 14, '星耀 Flip · 星夜黑 · 12+512GB',    '颜色:星夜黑;内存:12GB;存储:512GB',   5699.00, 6199.00, 30, 3, 1, NOW(), 0, NOW(), 0),
(44, 14, '星耀 Flip · 薰衣草紫 · 12+512GB',  '颜色:薰衣草紫;内存:12GB;存储:512GB', 5699.00, 6199.00, 30, 4, 1, NOW(), 0, NOW(), 0);

COMMIT;
