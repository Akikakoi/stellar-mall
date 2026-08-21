-- ============================================================
-- 订单明细增加额外费用字段（保障服务等）
-- 日期：2026-07-27
-- ============================================================

ALTER TABLE `stellar_mall_order_item`
    ADD COLUMN `extra_amount` DECIMAL(12,2) DEFAULT NULL COMMENT '额外费用（保障服务等），单位元，NULL 表示无额外费用'
    AFTER `subtotal`;
