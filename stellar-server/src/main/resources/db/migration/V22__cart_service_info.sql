-- 购物车表增加保障服务信息列，使加购时选择的保障服务可以持久化到服务端
-- extra_amount：单个商品的保障服务费（单价口径，未乘数量）
-- service_info：保障服务 JSON 数组，如 [{"id":"screen_insurance","title":"碎屏险 · 1年","price":99}]
ALTER TABLE stellar_cart
    ADD COLUMN extra_amount DECIMAL(10, 2) DEFAULT NULL COMMENT '保障服务费（单个商品）',
    ADD COLUMN service_info VARCHAR(1000) DEFAULT NULL COMMENT '保障服务信息（JSON）';
