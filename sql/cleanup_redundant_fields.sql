-- ============================================================
-- stellar_mall 数据库冗余/质量问题修复脚本
-- 生成方式：扫描 information_schema + 全量代码引用分析
-- 说明：本脚本不会自动执行，请人工审阅后按需执行
-- ============================================================

USE stellar_mall;

-- ------------------------------------------------------------
-- 一、修复列注释乱码（UTF-8 字节被按 GBK 解码导致，共 48 列）
-- ------------------------------------------------------------

-- stellar_checkin_record
ALTER TABLE `stellar_checkin_record` MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户ID';
ALTER TABLE `stellar_checkin_record` MODIFY COLUMN `checkin_date` date NOT NULL COMMENT '签到日期';
ALTER TABLE `stellar_checkin_record` MODIFY COLUMN `create_time` datetime NOT NULL COMMENT '签到时间';
-- stellar_home_module
ALTER TABLE `stellar_home_module` MODIFY COLUMN `type` varchar(32) NOT NULL DEFAULT 'HOT_PRODUCTS' COMMENT '模块类型：BANNER/HOT_PRODUCTS/NEW_PRODUCTS/CATEGORY_SHOWCASE/COUPON_ENTRY/PRODUCT_GRID/SINGLE_IMAGE';
ALTER TABLE `stellar_home_module` MODIFY COLUMN `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间';
ALTER TABLE `stellar_home_module` MODIFY COLUMN `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间';
-- stellar_mall_order
ALTER TABLE `stellar_mall_order` MODIFY COLUMN `delivery_time` datetime NULL COMMENT '发货时间';
-- stellar_points_payment
ALTER TABLE `stellar_points_payment` MODIFY COLUMN `order_id` bigint NOT NULL COMMENT '订单ID';
ALTER TABLE `stellar_points_payment` MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户ID';
ALTER TABLE `stellar_points_payment` MODIFY COLUMN `points` int NOT NULL COMMENT '变动的积分数';
ALTER TABLE `stellar_points_payment` MODIFY COLUMN `biz_desc` varchar(200) NULL COMMENT '业务描述';
-- stellar_points_product
ALTER TABLE `stellar_points_product` MODIFY COLUMN `name` varchar(200) NOT NULL COMMENT '商品名称';
ALTER TABLE `stellar_points_product` MODIFY COLUMN `stock` int NOT NULL DEFAULT '0' COMMENT '库存';
ALTER TABLE `stellar_points_product` MODIFY COLUMN `image_url` varchar(500) NULL COMMENT '商品图片';
ALTER TABLE `stellar_points_product` MODIFY COLUMN `description` varchar(1000) NULL COMMENT '商品描述';
ALTER TABLE `stellar_points_product` MODIFY COLUMN `coupon_id` bigint NULL COMMENT '关联优惠券ID(COUPON类型)';
ALTER TABLE `stellar_points_product` MODIFY COLUMN `create_time` datetime NOT NULL COMMENT '创建时间';
ALTER TABLE `stellar_points_product` MODIFY COLUMN `update_time` datetime NOT NULL COMMENT '更新时间';
-- stellar_points_record
ALTER TABLE `stellar_points_record` MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户ID';
ALTER TABLE `stellar_points_record` MODIFY COLUMN `biz_type` varchar(50) NOT NULL COMMENT '业务类型: ORDER / CHECKIN / REVIEW / REDEEM / EXPIRE / ADMIN';
ALTER TABLE `stellar_points_record` MODIFY COLUMN `description` varchar(200) NULL COMMENT '描述';
ALTER TABLE `stellar_points_record` MODIFY COLUMN `create_time` datetime NOT NULL COMMENT '创建时间';
-- stellar_points_redemption
ALTER TABLE `stellar_points_redemption` MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户ID';
ALTER TABLE `stellar_points_redemption` MODIFY COLUMN `product_id` bigint NOT NULL COMMENT '兑换商品ID';
ALTER TABLE `stellar_points_redemption` MODIFY COLUMN `coupon_id` bigint NULL COMMENT '发放的用户优惠券ID(COUPON类型)';
ALTER TABLE `stellar_points_redemption` MODIFY COLUMN `address_id` bigint NULL COMMENT '收货地址ID(PHYSICAL类型)';
ALTER TABLE `stellar_points_redemption` MODIFY COLUMN `remark` varchar(300) NULL COMMENT '备注';
ALTER TABLE `stellar_points_redemption` MODIFY COLUMN `create_time` datetime NOT NULL COMMENT '创建时间';
-- stellar_points_rule
ALTER TABLE `stellar_points_rule` MODIFY COLUMN `rule_type` varchar(50) NOT NULL COMMENT '规则类型: ORDER(下单) / CHECKIN(签到) / REVIEW(评价)';
ALTER TABLE `stellar_points_rule` MODIFY COLUMN `rule_name` varchar(100) NOT NULL COMMENT '规则名称';
ALTER TABLE `stellar_points_rule` MODIFY COLUMN `description` varchar(500) NULL COMMENT '规则描述';
ALTER TABLE `stellar_points_rule` MODIFY COLUMN `create_time` datetime NOT NULL COMMENT '创建时间';
ALTER TABLE `stellar_points_rule` MODIFY COLUMN `update_time` datetime NOT NULL COMMENT '更新时间';
-- stellar_user_message
ALTER TABLE `stellar_user_message` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键';
ALTER TABLE `stellar_user_message` MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户 ID';
ALTER TABLE `stellar_user_message` MODIFY COLUMN `type` varchar(32) NOT NULL COMMENT '类型：ORDER_SHIPPED / ORDER_CANCELLED / SYSTEM / COUPON';
ALTER TABLE `stellar_user_message` MODIFY COLUMN `content` varchar(1000) NULL DEFAULT '' COMMENT '消息正文';
ALTER TABLE `stellar_user_message` MODIFY COLUMN `create_time` datetime NOT NULL COMMENT '创建时间';
-- stellar_user_points
ALTER TABLE `stellar_user_points` MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户ID';
ALTER TABLE `stellar_user_points` MODIFY COLUMN `create_time` datetime NOT NULL COMMENT '创建时间';
ALTER TABLE `stellar_user_points` MODIFY COLUMN `update_time` datetime NOT NULL COMMENT '更新时间';
-- stellar_wallet
ALTER TABLE `stellar_wallet` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键';
ALTER TABLE `stellar_wallet` MODIFY COLUMN `user_id` bigint NOT NULL COMMENT '用户 ID';
ALTER TABLE `stellar_wallet` MODIFY COLUMN `create_time` datetime NOT NULL COMMENT '创建时间';
ALTER TABLE `stellar_wallet` MODIFY COLUMN `update_time` datetime NOT NULL COMMENT '更新时间';
-- stellar_wallet_transaction
ALTER TABLE `stellar_wallet_transaction` MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键';
ALTER TABLE `stellar_wallet_transaction` MODIFY COLUMN `remark` varchar(255) NULL COMMENT '备注';
ALTER TABLE `stellar_wallet_transaction` MODIFY COLUMN `create_time` datetime NOT NULL COMMENT '创建时间';

-- ------------------------------------------------------------
-- 二、确认冗余字段（代码零引用，建议删除）
--     执行前请先备份对应表！当前为注释状态，确认后去掉 -- 即可执行
-- ------------------------------------------------------------

-- stellar_sku.market_price     市场价/参考价：Java/XML/前端 全库零引用（204 行中 60 行有值，属历史遗留）
-- stellar_spu.slider_images    轮播图 JSON：Java/XML/前端 全库零引用（80 行中 4 行有值），
--                              实际轮播已由 main_image + sub_images 承载

-- 备份（建议先执行）:
-- CREATE TABLE stellar_sku_bak_20260829 AS SELECT * FROM stellar_sku;
-- CREATE TABLE stellar_spu_bak_20260829 AS SELECT * FROM stellar_spu;

-- ALTER TABLE `stellar_sku` DROP COLUMN `market_price`;
-- ALTER TABLE `stellar_spu` DROP COLUMN `slider_images`;

-- ------------------------------------------------------------
-- 三、暂不建议删除，但需确认（功能预留 / 数据未填充）
-- ------------------------------------------------------------
-- stellar_mall_order.tracking_no / delivery_company / delivery_time  物流字段，已在 Mapper 映射但业务未写入
-- stellar_mall_order_item.extra_amount                               保障服务费，代码已接入但数据全 NULL（功能未完成）
-- stellar_after_sale.images / audit_remark / refund_no / exchange_sku_id  售后高级功能预留，数据全 NULL
-- stellar_review.pics / reply_time                                    评价图片与回复时间，前端未开放入口
-- stellar_sku.cost_price                                              成本价，仅 Mapper 映射，业务未使用
-- stellar_points_product.coupon_id                                    积分商城优惠券关联，暂无 COUPON 类型商品
-- stellar_notification_log.phone                                      短信通道预留
-- stellar_spu.off_shelf_time                                          下架时间，上下架未走该字段
