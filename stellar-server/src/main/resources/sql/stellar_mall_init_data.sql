  1→USE stellar_mall;
  2→
  3→-- ============================================================
  4→-- 星耀商城初始化数据：分类、商品示例数据
  5→-- ============================================================
  6→
  7→-- ------------------------------
  8→-- 1. 分类数据
  9→-- ------------------------------
 10→INSERT INTO stellar_category (name, type, sort, status, create_time, create_user, update_time, update_user) VALUES
 11→('家用电器', 1, 100, 1, NOW(), 0, NOW(), 0),
 12→('手机数码', 1, 90, 1, NOW(), 0, NOW(), 0),
 13→('电脑办公', 1, 80, 1, NOW(), 0, NOW(), 0),
 14→('服饰鞋包', 1, 70, 1, NOW(), 0, NOW(), 0),
 15→('食品生鲜', 1, 60, 1, NOW(), 0, NOW(), 0),
 16→('智能电视', 1, 100, 1, NOW(), 0, NOW(), 0),
 17→('空调', 1, 90, 1, NOW(), 0, NOW(), 0),
 18→('冰箱', 1, 80, 1, NOW(), 0, NOW(), 0),
 19→('洗衣机', 1, 70, 1, NOW(), 0, NOW(), 0),
 20→('智能手机', 1, 100, 1, NOW(), 0, NOW(), 0),
 21→('平板电脑', 1, 90, 1, NOW(), 0, NOW(), 0),
 22→('蓝牙耳机', 1, 80, 1, NOW(), 0, NOW(), 0),
 23→('笔记本电脑', 1, 100, 1, NOW(), 0, NOW(), 0),
 24→('台式电脑', 1, 90, 1, NOW(), 0, NOW(), 0),
 25→('办公外设', 1, 80, 1, NOW(), 0, NOW(), 0),
 26→('男装', 1, 100, 1, NOW(), 0, NOW(), 0),
 27→('女装', 1, 90, 1, NOW(), 0, NOW(), 0),
 28→('鞋靴', 1, 80, 1, NOW(), 0, NOW(), 0),
 29→('休闲零食', 1, 100, 1, NOW(), 0, NOW(), 0),
 30→('新鲜水果', 1, 90, 1, NOW(), 0, NOW(), 0);
 31→
 32→-- ------------------------------
 33→-- 2. 商品数据（SPU + SKU）
 34→-- ------------------------------
 35→
 36→-- 商品1：星耀 55 寸 4K 智能电视
 37→INSERT INTO stellar_spu (name, sub_title, category_id, description, description_md, main_image, sub_images, sale_count, comment_count, total_stock, sku_count, min_price, max_price, is_new, is_hot, sort, status, on_shelf_time, create_time, create_user, update_time, update_user) VALUES
 38→('星耀 55 寸 4K 智能电视 Pro', '量子点 · 超薄全面屏 · 杜比视界', 1, '<p>星耀 55 寸智能电视，采用最新量子点技术，色彩更加鲜艳。超薄全面屏设计，让您沉浸在精彩画面中。</p>', '# 星耀 55 寸 4K 智能电视 Pro\n\n## 产品特点\n\n- 量子点显示技术\n- 超薄全面屏设计\n- 杜比视界 HDR\n- AI 语音助手', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=modern%2055%20inch%20smart%20TV%204K%20ultra%20thin%20black%20design&image_size=landscape_16_9', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=smart%20TV%20side%20view%20ultra%20thin;https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=smart%20TV%20interface%20ui', 1234, 56, 200, 2, 2999.00, 3999.00, 1, 1, 100, 1, NOW(), NOW(), 0, NOW(), 0);
 39→
 40→INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, version, warn_stock, weight_g, barcode, image, sort, status, create_time, create_user, update_time, update_user) VALUES
 41→(1, '星耀 55 寸 4K 智能电视 Pro · 标准版', '版本:标准版', '{"版本":"标准版"}', 2999.00, 3499.00, 2500.00, 3299.00, 100, 0, 10, 15000, 'TV-XY-55-PRO-S', NULL, 10, 1, NOW(), 0, NOW(), 0),
 42→(1, '星耀 55 寸 4K 智能电视 Pro · 豪华版', '版本:豪华版', '{"版本":"豪华版"}', 3999.00, 4499.00, 3200.00, 4299.00, 100, 0, 10, 15000, 'TV-XY-55-PRO-L', NULL, 20, 1, NOW(), 0, NOW(), 0);
 43→
 44→-- 商品2：星耀 X100 Pro 智能手机
 45→INSERT INTO stellar_spu (name, sub_title, category_id, description, description_md, main_image, sub_images, sale_count, comment_count, total_stock, sku_count, min_price, max_price, is_new, is_hot, sort, status, on_shelf_time, create_time, create_user, update_time, update_user) VALUES
 46→('星耀 X100 Pro 5G 智能手机', '骁龙 8 Gen3 · 2亿像素 · 5000mAh大电池', 2, '<p>星耀旗舰手机，搭载最新骁龙 8 Gen3 处理器，性能强劲。2亿像素主摄，拍照效果惊艳。</p>', '# 星耀 X100 Pro 5G 智能手机\n\n## 核心配置\n\n- 骁龙 8 Gen3 处理器\n- 2亿像素主摄\n- 5000mAh 大电池\n- 100W 快充', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=modern%20smartphone%20flagship%20black%20camera%20module&image_size=portrait_16_9', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=smartphone%20back%20view%20camera;https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=smartphone%20front%20screen', 5678, 234, 300, 4, 3999.00, 5999.00, 1, 1, 90, 1, NOW(), NOW(), 0, NOW(), 0);
 47→
 48→INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, version, warn_stock, weight_g, barcode, image, sort, status, create_time, create_user, update_time, update_user) VALUES
 49→(2, '星耀 X100 Pro · 钛黑 · 8+256GB', '颜色:钛黑;内存:8GB;存储:256GB', '{"颜色":"钛黑","内存":"8GB","存储":"256GB"}', 3999.00, 4499.00, 3200.00, 4299.00, 80, 0, 10, 200, 'PH-XY-X100-8-256-B', NULL, 10, 1, NOW(), 0, NOW(), 0),
 50→(2, '星耀 X100 Pro · 钛黑 · 12+512GB', '颜色:钛黑;内存:12GB;存储:512GB', '{"颜色":"钛黑","内存":"12GB","存储":"512GB"}', 4999.00, 5499.00, 4000.00, 5299.00, 70, 0, 10, 200, 'PH-XY-X100-12-512-B', NULL, 20, 1, NOW(), 0, NOW(), 0),
 51→(2, '星耀 X100 Pro · 星空蓝 · 8+256GB', '颜色:星空蓝;内存:8GB;存储:256GB', '{"颜色":"星空蓝","内存":"8GB","存储":"256GB"}', 3999.00, 4499.00, 3200.00, 4299.00, 75, 0, 10, 200, 'PH-XY-X100-8-256-BL', NULL, 30, 1, NOW(), 0, NOW(), 0),
 52→(2, '星耀 X100 Pro · 星空蓝 · 12+512GB', '颜色:星空蓝;内存:12GB;存储:512GB', '{"颜色":"星空蓝","内存":"12GB","存储":"512GB"}', 4999.00, 5499.00, 4000.00, 5299.00, 75, 0, 10, 200, 'PH-XY-X100-12-512-BL', NULL, 40, 1, NOW(), 0, NOW(), 0);
 53→
 54→-- 商品3：极净 1.5 匹 变频空调
 55→INSERT INTO stellar_spu (name, sub_title, category_id, description, description_md, main_image, sub_images, sale_count, comment_count, total_stock, sku_count, min_price, max_price, is_new, is_hot, sort, status, on_shelf_time, create_time, create_user, update_time, update_user) VALUES
 56→('极净 1.5 匹 变频冷暖空调', '新一级能效 · 静音舒适 · 智能控温', 1, '<p>极净变频空调，新一级能效认证，省电环保。静音设计，让您安享舒适睡眠。</p>', '# 极净 1.5 匹变频空调\n\n## 产品特点\n\n- 新一级能效\n- 静音运行\n- 智能控温\n- 快速制冷制热', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=modern%20air%20conditioner%20indoor%20unit%20white%20sleek&image_size=square', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=air%20conditioner%20remote%20control;https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=air%20conditioner%20outdoor%20unit', 890, 123, 150, 2, 2499.00, 2999.00, 0, 1, 80, 1, NOW(), NOW(), 0, NOW(), 0);
 57→
 58→INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, version, warn_stock, weight_g, barcode, image, sort, status, create_time, create_user, update_time, update_user) VALUES
 59→(3, '极净 1.5 匹变频空调 · 标准版', '版本:标准版', '{"版本":"标准版"}', 2499.00, 2999.00, 2000.00, 2799.00, 80, 0, 10, 25000, 'AC-JJ-1.5-S', NULL, 10, 1, NOW(), 0, NOW(), 0),
 60→(3, '极净 1.5 匹变频空调 · 智能版', '版本:智能版', '{"版本":"智能版"}', 2999.00, 3499.00, 2400.00, 3299.00, 70, 0, 10, 26000, 'AC-JJ-1.5-I', NULL, 20, 1, NOW(), 0, NOW(), 0);
 61→
 62→-- 商品4：御风轻薄笔记本电脑
 63→INSERT INTO stellar_spu (name, sub_title, category_id, description, description_md, main_image, sub_images, sale_count, comment_count, total_stock, sku_count, min_price, max_price, is_new, is_hot, sort, status, on_shelf_time, create_time, create_user, update_time, update_user) VALUES
 64→('御风 Air 14 轻薄笔记本电脑', '14英寸 · i5-13500H · 16GB · 512GB', 3, '<p>御风轻薄本，仅1.2kg重量，随身携带无压力。搭载最新处理器，办公娱乐两不误。</p>', '# 御风 Air 14 轻薄笔记本\n\n## 核心配置\n\n- 14英寸 2K 屏幕\n- i5-13500H 处理器\n- 16GB LPDDR5 内存\n- 512GB SSD', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=thin%20lightweight%20laptop%20silver%20modern&image_size=landscape_16_9', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=laptop%20keyboard%20view;https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=laptop%20side%20view%20thin', 3456, 456, 200, 3, 4499.00, 6999.00, 1, 0, 70, 1, NOW(), NOW(), 0, NOW(), 0);
 65→
 66→INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, version, warn_stock, weight_g, barcode, image, sort, status, create_time, create_user, update_time, update_user) VALUES
 67→(4, '御风 Air 14 · i5 · 16+512GB · 核显', 'CPU:i5-13500H;内存:16GB;存储:512GB;显卡:核显', '{"CPU":"i5-13500H","内存":"16GB","存储":"512GB","显卡":"核显"}', 4499.00, 4999.00, 3800.00, 4799.00, 80, 0, 10, 1200, 'LP-YF-A14-I5-16-512', NULL, 10, 1, NOW(), 0, NOW(), 0),
 68→(4, '御风 Air 14 · i7 · 16+512GB · 核显', 'CPU:i7-13700H;内存:16GB;存储:512GB;显卡:核显', '{"CPU":"i7-13700H","内存":"16GB","存储":"512GB","显卡":"核显"}', 5499.00, 5999.00, 4600.00, 5799.00, 60, 0, 10, 1200, 'LP-YF-A14-I7-16-512', NULL, 20, 1, NOW(), 0, NOW(), 0),
 69→(4, '御风 Air 14 · i7 · 32+1TB · RTX4050', 'CPU:i7-13700H;内存:32GB;存储:1TB;显卡:RTX4050', '{"CPU":"i7-13700H","内存":"32GB","存储":"1TB","显卡":"RTX4050"}', 6999.00, 7499.00, 5800.00, 7299.00, 60, 0, 10, 1400, 'LP-YF-A14-I7-32-1T', NULL, 30, 1, NOW(), 0, NOW(), 0);
 70→
 71→-- 商品5：清逸无线蓝牙耳机
 72→INSERT INTO stellar_spu (name, sub_title, category_id, description, description_md, main_image, sub_images, sale_count, comment_count, total_stock, sku_count, min_price, max_price, is_new, is_hot, sort, status, on_shelf_time, create_time, create_user, update_time, update_user) VALUES
 73→('清逸 Pods Pro 无线降噪蓝牙耳机', '主动降噪 · 30小时续航 · 蓝牙5.3', 2, '<p>清逸真无线蓝牙耳机，支持主动降噪，让您沉浸在音乐世界。超长续航，全天无忧。</p>', '# 清逸 Pods Pro 无线耳机\n\n## 产品特点\n\n- 主动降噪技术\n- 30小时超长续航\n- 蓝牙5.3\n- 触控操作', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=wireless%20earbuds%20white%20modern%20case&image_size=square', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=wireless%20earbuds%20in%20ear;https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=earbuds%20case%20open', 7890, 890, 500, 2, 299.00, 499.00, 0, 1, 60, 1, NOW(), NOW(), 0, NOW(), 0);
 74→
 75→INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, version, warn_stock, weight_g, barcode, image, sort, status, create_time, create_user, update_time, update_user) VALUES
 76→(5, '清逸 Pods Pro · 珍珠白', '颜色:珍珠白', '{"颜色":"珍珠白"}', 299.00, 399.00, 200.00, 349.00, 300, 0, 10, 50, 'EP-QY-PRO-W', NULL, 10, 1, NOW(), 0, NOW(), 0),
 77→(5, '清逸 Pods Pro · 深空灰', '颜色:深空灰', '{"颜色":"深空灰"}', 499.00, 599.00, 350.00, 549.00, 200, 0, 10, 50, 'EP-QY-PRO-G', NULL, 20, 1, NOW(), 0, NOW(), 0);
 78→
 79→-- 商品6：逸彩休闲运动鞋
 80→INSERT INTO stellar_spu (name, sub_title, category_id, description, description_md, main_image, sub_images, sale_count, comment_count, total_stock, sku_count, min_price, max_price, is_new, is_hot, sort, status, on_shelf_time, create_time, create_user, update_time, update_user) VALUES
 81→('逸彩飞翼 休闲运动鞋', '透气网面 · 轻便舒适 · 时尚百搭', 4, '<p>逸彩休闲运动鞋，采用透气网面材质，轻便舒适。时尚设计，百搭各种穿搭风格。</p>', '# 逸彩飞翼休闲运动鞋\n\n## 产品特点\n\n- 透气网面材质\n- 轻便舒适\n- 时尚百搭\n- 耐磨防滑', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=modern%20sports%20sneakers%20white%20fashion&image_size=square', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=sneakers%20side%20view;https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=sneakers%20top%20view', 4567, 345, 300, 6, 199.00, 399.00, 0, 0, 50, 1, NOW(), NOW(), 0, NOW(), 0);
 82→
 83→INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, version, warn_stock, weight_g, barcode, image, sort, status, create_time, create_user, update_time, update_user) VALUES
 84→(6, '逸彩飞翼 · 白色 · 39码', '颜色:白色;尺码:39', '{"颜色":"白色","尺码":"39"}', 199.00, 299.00, 120.00, 249.00, 50, 0, 5, 400, 'SH-YC-FW-W-39', NULL, 10, 1, NOW(), 0, NOW(), 0),
 85→(6, '逸彩飞翼 · 白色 · 40码', '颜色:白色;尺码:40', '{"颜色":"白色","尺码":"40"}', 199.00, 299.00, 120.00, 249.00, 50, 0, 5, 400, 'SH-YC-FW-W-40', NULL, 20, 1, NOW(), 0, NOW(), 0),
 86→(6, '逸彩飞翼 · 白色 · 41码', '颜色:白色;尺码:41', '{"颜色":"白色","尺码":"41"}', 199.00, 299.00, 120.00, 249.00, 50, 0, 5, 400, 'SH-YC-FW-W-41', NULL, 30, 1, NOW(), 0, NOW(), 0),
 87→(6, '逸彩飞翼 · 黑色 · 39码', '颜色:黑色;尺码:39', '{"颜色":"黑色","尺码":"39"}', 299.00, 399.00, 180.00, 349.00, 50, 0, 5, 400, 'SH-YC-FW-B-39', NULL, 40, 1, NOW(), 0, NOW(), 0),
 88→(6, '逸彩飞翼 · 黑色 · 40码', '颜色:黑色;尺码:40', '{"颜色":"黑色","尺码":"40"}', 299.00, 399.00, 180.00, 349.00, 50, 0, 5, 400, 'SH-YC-FW-B-40', NULL, 50, 1, NOW(), 0, NOW(), 0),
 89→(6, '逸彩飞翼 · 黑色 · 41码', '颜色:黑色;尺码:41', '{"颜色":"黑色","尺码":"41"}', 299.00, 399.00, 180.00, 349.00, 50, 0, 5, 400, 'SH-YC-FW-B-41', NULL, 60, 1, NOW(), 0, NOW(), 0);
 90→
 91→-- 商品7：极净 500L 智能冰箱
 92→INSERT INTO stellar_spu (name, sub_title, category_id, description, description_md, main_image, sub_images, sale_count, comment_count, total_stock, sku_count, min_price, max_price, is_new, is_hot, sort, status, on_shelf_time, create_time, create_user, update_time, update_user) VALUES
 93→('极净 500L 十字对开门智能冰箱', '风冷无霜 · 大容量 · 智能控温', 1, '<p>极净智能冰箱，500升超大容量，十字对开门设计。风冷无霜技术，食物新鲜持久。</p>', '# 极净 500L 智能冰箱\n\n## 产品特点\n\n- 500升超大容量\n- 十字对开门设计\n- 风冷无霜技术\n- 智能控温', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=modern%20smart%20refrigerator%20stainless%20steel%20cross%20door&image_size=square', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=refrigerator%20interior%20shelves;https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=refrigerator%20door%20open', 234, 56, 100, 2, 3999.00, 4999.00, 0, 0, 40, 1, NOW(), NOW(), 0, NOW(), 0);
 94→
 95→INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, version, warn_stock, weight_g, barcode, image, sort, status, create_time, create_user, update_time, update_user) VALUES
 96→(7, '极净 500L冰箱 · 银色', '颜色:银色', '{"颜色":"银色"}', 3999.00, 4499.00, 3200.00, 4299.00, 50, 0, 5, 60000, 'RF-JJ-500-S', NULL, 10, 1, NOW(), 0, NOW(), 0),
 97→(7, '极净 500L冰箱 · 黑色', '颜色:黑色', '{"颜色":"黑色"}', 4999.00, 5499.00, 4000.00, 5299.00, 50, 0, 5, 60000, 'RF-JJ-500-B', NULL, 20, 1, NOW(), 0, NOW(), 0);
 98→
 99→-- 商品8：星耀全自动滚筒洗衣机
100→INSERT INTO stellar_spu (name, sub_title, category_id, description, description_md, main_image, sub_images, sale_count, comment_count, total_stock, sku_count, min_price, max_price, is_new, is_hot, sort, status, on_shelf_time, create_time, create_user, update_time, update_user) VALUES
101→('星耀 10kg 全自动滚筒洗衣机', '变频静音 · 高温除菌 · 智能投放', 1, '<p>星耀滚筒洗衣机，10公斤大容量。变频静音技术，高温除菌更安心。</p>', '# 星耀 10kg 滚筒洗衣机\n\n## 产品特点\n\n- 10公斤大容量\n- 变频静音\n- 高温除菌\n- 智能投放', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=modern%20front%20loading%20washing%20machine%20white&image_size=square', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=washing%20machine%20control%20panel;https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=washing%20machine%20door%20open', 567, 89, 150, 2, 2999.00, 3499.00, 0, 0, 30, 1, NOW(), NOW(), 0, NOW(), 0);
102→
103→INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, version, warn_stock, weight_g, barcode, image, sort, status, create_time, create_user, update_time, update_user) VALUES
104→(8, '星耀 10kg洗衣机 · 白色', '颜色:白色', '{"颜色":"白色"}', 2999.00, 3499.00, 2400.00, 3299.00, 80, 0, 5, 55000, 'WM-XY-10-W', NULL, 10, 1, NOW(), 0, NOW(), 0),
105→(8, '星耀 10kg洗衣机 · 银色', '颜色:银色', '{"颜色":"银色"}', 3499.00, 3999.00, 2800.00, 3799.00, 70, 0, 5, 55000, 'WM-XY-10-S', NULL, 20, 1, NOW(), 0, NOW(), 0);
106→
107→-- 商品9：御风机械键盘
108→INSERT INTO stellar_spu (name, sub_title, category_id, description, description_md, main_image, sub_images, sale_count, comment_count, total_stock, sku_count, min_price, max_price, is_new, is_hot, sort, status, on_shelf_time, create_time, create_user, update_time, update_user) VALUES
109→('御风 K87 机械键盘', 'RGB背光 · 青轴/红轴可选 · 87键', 3, '<p>御风机械键盘，RGB背光效果，多种轴体可选。87键紧凑布局，桌面更整洁。</p>', '# 御风 K87 机械键盘\n\n## 产品特点\n\n- RGB 背光\n- 青轴/红轴可选\n- 87键紧凑布局\n- 全键无冲', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=mechanical%20keyboard%20RGB%20backlight%20black&image_size=landscape_16_9', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=keyboard%20keys%20detail;https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=keyboard%20side%20view', 1234, 234, 200, 2, 199.00, 299.00, 0, 0, 20, 1, NOW(), NOW(), 0, NOW(), 0);
110→
111→INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, version, warn_stock, weight_g, barcode, image, sort, status, create_time, create_user, update_time, update_user) VALUES
112→(9, '御风 K87 · 青轴', '轴体:青轴', '{"轴体":"青轴"}', 199.00, 249.00, 140.00, 229.00, 100, 0, 10, 800, 'KB-YF-K87-BL', NULL, 10, 1, NOW(), 0, NOW(), 0),
113→(9, '御风 K87 · 红轴', '轴体:红轴', '{"轴体":"红轴"}', 299.00, 349.00, 200.00, 329.00, 100, 0, 10, 800, 'KB-YF-K87-RD', NULL, 20, 1, NOW(), 0, NOW(), 0);
114→
115→-- 商品10：星耀 13 寸平板电脑
116→INSERT INTO stellar_spu (name, sub_title, category_id, description, description_md, main_image, sub_images, sale_count, comment_count, total_stock, sku_count, min_price, max_price, is_new, is_hot, sort, status, on_shelf_time, create_time, create_user, update_time, update_user) VALUES
117→('星耀 Pad 13 平板电脑', '13英寸 2.5K屏 · 骁龙8Gen2 · 8GB+256GB', 2, '<p>星耀平板电脑，13英寸大屏幕，适合办公娱乐。骁龙8Gen2处理器，性能强劲。</p>', '# 星耀 Pad 13 平板电脑\n\n## 核心配置\n\n- 13英寸 2.5K 屏幕\n- 骁龙 8 Gen2\n- 8GB 内存\n- 256GB 存储', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=tablet%20pc%20black%20modern%20stylus&image_size=landscape_16_9', 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=tablet%20screen%20interface;https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=tablet%20back%20view', 890, 156, 150, 2, 3999.00, 4999.00, 1, 0, 10, 1, NOW(), NOW(), 0, NOW(), 0);
118→
119→INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, version, warn_stock, weight_g, barcode, image, sort, status, create_time, create_user, update_time, update_user) VALUES
120→(10, '星耀 Pad 13 · 8+256GB', '内存:8GB;存储:256GB', '{"内存":"8GB","存储":"256GB"}', 3999.00, 4499.00, 3200.00, 4299.00, 80, 0, 10, 500, 'TB-XY-PAD13-8-256', NULL, 10, 1, NOW(), 0, NOW(), 0),
121→(10, '星耀 Pad 13 · 12+512GB', '内存:12GB;存储:512GB', '{"内存":"12GB","存储":"512GB"}', 4999.00, 5499.00, 4000.00, 5299.00, 70, 0, 10, 500, 'TB-XY-PAD13-12-512', NULL, 20, 1, NOW(), 0, NOW(), 0);
122→
123→SELECT '初始化数据完成' AS result;