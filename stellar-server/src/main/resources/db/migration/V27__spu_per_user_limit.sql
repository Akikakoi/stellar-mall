-- SPU 增加每人限购数量列（方案 B：真实限购功能）
-- per_user_limit：每人限购件数，0 表示不限购
-- 限购口径：用户对同一 SPU 的累计购买件数（排除 PENDING 待付款 / CANCELLED 已取消 / REFUNDED 已整单退款）
ALTER TABLE stellar_spu
    ADD COLUMN per_user_limit INT NOT NULL DEFAULT 0 COMMENT '每人限购数量，0=不限购';
