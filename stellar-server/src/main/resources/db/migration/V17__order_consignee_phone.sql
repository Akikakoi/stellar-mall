-- ============================================================
-- V17: 订单补齐收货人快照字段（consignee / phone）
-- ------------------------------------------------------------
-- 背景：
--   C 端下单页（OrderSubmit.vue）已采集收货人姓名与电话，并随
--   OrderSubmitDTO 一并提交；但 stellar_mall_order 表缺这两列，
--   后端实体与 Mapper 也未映射，导致收货人信息被静默丢弃，
--   售后 / 物流场景拿不到联系人。
--
-- 设计：
--   1. 字段规格与 stellar_user_address（VARCHAR(50) / VARCHAR(20)）保持一致，
--      避免地址簿能存、订单快照反而被截断。
--   2. NOT NULL DEFAULT '' —— 兼容已存在的历史订单（它们本就没有收货人数据），
--      同时保证新订单字段不为 NULL。
--   3. 下单时写入的是快照，用户后续修改地址簿不影响历史订单。
--
-- 注：V2__address_review_coupon_banner.sql 中曾预留过这两句 ALTER（被注释），
--     正式落地在本脚本，V2 保持不动以免 Flyway checksum 校验失败。
-- ============================================================

ALTER TABLE stellar_mall_order
    ADD COLUMN consignee VARCHAR(50) NOT NULL DEFAULT '' COMMENT '收货人快照' AFTER address,
    ADD COLUMN phone     VARCHAR(20) NOT NULL DEFAULT '' COMMENT '手机号快照' AFTER consignee;
