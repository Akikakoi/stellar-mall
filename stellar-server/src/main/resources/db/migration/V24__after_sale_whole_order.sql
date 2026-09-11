-- 整单退款支持：售后单 sku_id 允许为空（NULL = 整单退款，退订单下全部商品）
ALTER TABLE stellar_after_sale MODIFY COLUMN sku_id BIGINT NULL COMMENT '申请售后的SKU ID；NULL=整单退款';
