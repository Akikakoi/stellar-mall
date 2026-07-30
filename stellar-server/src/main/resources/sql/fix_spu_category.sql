USE stellar_mall;

-- 修复商品分类关联错误
-- 商品1：星耀 X100 Pro 旗舰手机 - 应该是手机数码 > 智能手机
UPDATE stellar_spu SET category_id = 1101, category2_id = 1109 WHERE id = 1;

-- 商品2：极净 516L 法式多门冰箱 - 应该是家用电器 > 冰箱
UPDATE stellar_spu SET category_id = 1100, category2_id = 1107 WHERE id = 2;

-- 商品3：御风 3 匹一级变频柜机空调 - 应该是家用电器 > 空调
UPDATE stellar_spu SET category_id = 1100, category2_id = 1106 WHERE id = 3;

-- 商品4：清逸 AirBook 14 轻薄本 - 应该是电脑办公 > 笔记本电脑
UPDATE stellar_spu SET category_id = 1102, category2_id = 1112 WHERE id = 4;

-- 商品5：逸彩 65 寸 QD-MiniLED 电视 - 应该是家用电器 > 智能电视
UPDATE stellar_spu SET category_id = 1100, category2_id = 1105 WHERE id = 5;

-- 商品6：逸彩 VividBar 5.1.2 回音壁套装 - 应该是家用电器 > 智能电视
UPDATE stellar_spu SET category_id = 1100, category2_id = 1105 WHERE id = 6;

-- 商品308：大米10 Pro - 应该是手机数码 > 智能手机
UPDATE stellar_spu SET category_id = 1101, category2_id = 1109 WHERE id = 308;

-- 验证修复结果
SELECT spu.id, spu.name, spu.category_id, c1.name as cat1_name, spu.category2_id, c2.name as cat2_name 
FROM stellar_spu spu 
LEFT JOIN stellar_category c1 ON spu.category_id = c1.id 
LEFT JOIN stellar_category c2 ON spu.category2_id = c2.id 
ORDER BY spu.id;
