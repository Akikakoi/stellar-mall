-- 订单明细表增加保障服务信息列，存储 JSON 数组格式的已购服务详情
-- 格式示例：[{"id":"screen_insurance","title":"碎屏险 · 1年","price":99}]
ALTER TABLE stellar_mall_order_item
    ADD COLUMN service_info VARCHAR(1000) DEFAULT NULL COMMENT '保障服务信息（JSON）';