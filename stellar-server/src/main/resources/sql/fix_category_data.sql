USE stellar_mall;

-- 更新二级分类的parent_id，使其指向正确的一级分类
UPDATE stellar_category SET parent_id = 1100 WHERE id = 1105 AND parent_id = 1;
UPDATE stellar_category SET parent_id = 1100 WHERE id = 1106 AND parent_id = 1;
UPDATE stellar_category SET parent_id = 1100 WHERE id = 1107 AND parent_id = 1;
UPDATE stellar_category SET parent_id = 1100 WHERE id = 1108 AND parent_id = 1;

UPDATE stellar_category SET parent_id = 1101 WHERE id = 1109 AND parent_id = 2;
UPDATE stellar_category SET parent_id = 1101 WHERE id = 1110 AND parent_id = 2;
UPDATE stellar_category SET parent_id = 1101 WHERE id = 1111 AND parent_id = 2;

UPDATE stellar_category SET parent_id = 1102 WHERE id = 1112 AND parent_id = 3;
UPDATE stellar_category SET parent_id = 1102 WHERE id = 1113 AND parent_id = 3;
UPDATE stellar_category SET parent_id = 1102 WHERE id = 1114 AND parent_id = 3;

UPDATE stellar_category SET parent_id = 1103 WHERE id = 1115 AND parent_id = 4;
UPDATE stellar_category SET parent_id = 1103 WHERE id = 1116 AND parent_id = 4;
UPDATE stellar_category SET parent_id = 1103 WHERE id = 1117 AND parent_id = 4;

UPDATE stellar_category SET parent_id = 1104 WHERE id = 1118 AND parent_id = 5;
UPDATE stellar_category SET parent_id = 1104 WHERE id = 1119 AND parent_id = 5;

-- 删除旧的错误分类数据（id=1-7）
DELETE FROM stellar_category WHERE id IN (1, 2, 3, 4, 5, 6, 7);

-- 更新商品的category_id和category2_id为正确的值
UPDATE stellar_spu SET category_id = 1100 WHERE category_id = 1;
UPDATE stellar_spu SET category_id = 1101 WHERE category_id = 2;
UPDATE stellar_spu SET category_id = 1102 WHERE category_id = 3;
UPDATE stellar_spu SET category_id = 1103 WHERE category_id = 4;
UPDATE stellar_spu SET category_id = 1104 WHERE category_id = 5;

UPDATE stellar_spu SET category2_id = 1105 WHERE category2_id = 6;
UPDATE stellar_spu SET category2_id = 1106 WHERE category2_id = 7;
UPDATE stellar_spu SET category2_id = 1107 WHERE category2_id = 8;
UPDATE stellar_spu SET category2_id = 1108 WHERE category2_id = 9;
UPDATE stellar_spu SET category2_id = 1109 WHERE category2_id = 10;
UPDATE stellar_spu SET category2_id = 1110 WHERE category2_id = 11;
UPDATE stellar_spu SET category2_id = 1111 WHERE category2_id = 12;
UPDATE stellar_spu SET category2_id = 1112 WHERE category2_id = 13;
UPDATE stellar_spu SET category2_id = 1113 WHERE category2_id = 14;
UPDATE stellar_spu SET category2_id = 1114 WHERE category2_id = 15;
UPDATE stellar_spu SET category2_id = 1115 WHERE category2_id = 16;
UPDATE stellar_spu SET category2_id = 1116 WHERE category2_id = 17;
UPDATE stellar_spu SET category2_id = 1117 WHERE category2_id = 18;
UPDATE stellar_spu SET category2_id = 1118 WHERE category2_id = 19;
UPDATE stellar_spu SET category2_id = 1119 WHERE category2_id = 20;
