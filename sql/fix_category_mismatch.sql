-- ============================================================
-- 数据修复：清理 SPU 中 category2_id 与 category_id 不匹配的记录
-- 问题：部分商品的二级分类不属于对应的一级分类，导致商品
--       在多个一级分类下展示
-- 使用前请先执行排查 SELECT 确认影响范围
-- ============================================================

-- ---- 第一步：排查存在错配的商品（只读，不修改） ----
SELECT
    s.id          AS spu_id,
    s.name        AS spu_name,
    s.category_id,
    c1.name       AS category_name,
    s.category2_id,
    c2.name       AS category2_name,
    c2.parent_id  AS category2_real_parent_id
FROM stellar_spu s
LEFT JOIN stellar_category c1 ON c1.id = s.category_id
LEFT JOIN stellar_category c2 ON c2.id = s.category2_id
WHERE s.category2_id IS NOT NULL
  AND (c2.id IS NULL OR c2.parent_id != s.category_id)
ORDER BY s.id;

-- ---- 第二步：修复 - 将错配的 category2_id 置为 NULL ----
-- ⚠️ 请确认第一步排查结果后再执行
-- UPDATE stellar_spu s
-- LEFT JOIN stellar_category c2 ON c2.id = s.category2_id
-- SET s.category2_id = NULL
-- WHERE s.category2_id IS NOT NULL
--   AND (c2.id IS NULL OR c2.parent_id != s.category_id);

-- ---- 第三步：验证修复结果 ----
-- 执行第一步的 SELECT 确认无结果

-- ============================================================
-- 附加：统计每个一级分类下有多少未分配二级分类的商品
-- ============================================================
SELECT
    c.name          AS category_name,
    COUNT(s.id)     AS spu_count,
    SUM(CASE WHEN s.category2_id IS NULL THEN 1 ELSE 0 END) AS no_sub_count
FROM stellar_spu s
JOIN stellar_category c ON c.id = s.category_id
WHERE c.level = 1
GROUP BY c.id, c.name
ORDER BY c.name;
