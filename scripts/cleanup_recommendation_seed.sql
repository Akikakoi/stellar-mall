-- 删除 2026-11-17 为推荐算法测试生成的模拟行为数据。
-- 标记：device_id 以 seed-rec-20261117- 开头，extra.seed=rec-20261117。
-- 只影响 stellar_user_behavior；不会删除订单、库存或浏览历史。
DELETE FROM stellar_user_behavior WHERE device_id LIKE 'seed-rec-20261117-%';

-- 校验：应为 0
SELECT COUNT(*) AS remaining_seed
FROM stellar_user_behavior
WHERE device_id LIKE 'seed-rec-20261117-%';
