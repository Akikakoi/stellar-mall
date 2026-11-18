-- 删除 2026-11-17 为新增商品生成的模拟评价，并重算评论数。
-- 标记：order_no 以 SEEDREVIEW-20261117- 开头。
DELETE FROM stellar_review WHERE order_no LIKE 'SEEDREVIEW-20261117-%';

UPDATE stellar_spu s
SET comment_count = (
    SELECT COUNT(*) FROM stellar_review r
    WHERE r.spu_id = s.id AND r.status = 1
)
WHERE s.id BETWEEN 181 AND 213;

SELECT COUNT(*) AS remaining_seed_reviews
FROM stellar_review
WHERE order_no LIKE 'SEEDREVIEW-20261117-%';
