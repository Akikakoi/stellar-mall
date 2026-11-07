SET FOREIGN_KEY_CHECKS=0;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_after_sale` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '订单 ID',
  `sku_id` bigint DEFAULT NULL COMMENT '申请售后的SKU ID；NULL=整单退款',
  `user_id` bigint NOT NULL COMMENT '申请人用户 ID',
  `type` tinyint NOT NULL COMMENT '售后类型：1 仅退款，2 退货退款，3 换货',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '售后状态：1 申请，2 商家审核中，3 用户退货中，4 退款中，5 完成，6 已拒绝，7 已取消',
  `reason` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '申请原因',
  `detail` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '详细描述',
  `amount` decimal(10,2) NOT NULL COMMENT '申请退款金额',
  `images` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '凭证图片 JSON',
  `audit_user_id` bigint DEFAULT NULL COMMENT '审核人员工 ID',
  `audit_remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审核备注',
  `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
  `return_tracking` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '退货快递单号',
  `refund_no` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '第三方退款流水号',
  `refund_time` datetime DEFAULT NULL COMMENT '退款完成时间',
  `exchange_sku_id` bigint DEFAULT NULL COMMENT '换货目标 SKU（换货专用）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `create_user` bigint NOT NULL DEFAULT '0' COMMENT '创建人',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `update_user` bigint NOT NULL DEFAULT '0' COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_after_sale_order` (`order_id`),
  KEY `idx_after_sale_user` (`user_id`),
  KEY `idx_after_sale_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后申请表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_banner` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(100) NOT NULL COMMENT '标题',
  `image_url` varchar(500) NOT NULL COMMENT '图片URL',
  `link_url` varchar(500) DEFAULT '' COMMENT '点击跳转链接',
  `sort` int DEFAULT '0' COMMENT '排序值，越大越靠前',
  `status` tinyint DEFAULT '1' COMMENT '1=启用 0=禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_user` bigint DEFAULT '0',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `update_user` bigint DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='首页轮播图';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_browse_history` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `spu_id` bigint NOT NULL,
  `sku_id` bigint DEFAULT NULL,
  `browse_time` datetime NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_browse_user_spu` (`user_id`,`spu_id`),
  KEY `idx_browse_user_time` (`user_id`,`browse_time`)
) ENGINE=InnoDB AUTO_INCREMENT=90 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='browse history';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_cart` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `spu_id` bigint NOT NULL COMMENT 'SPU ID',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `qty` int NOT NULL DEFAULT '1' COMMENT '数量',
  `checked` tinyint NOT NULL DEFAULT '1' COMMENT '1 已勾选=参与结算，0 未勾选',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `create_user` bigint NOT NULL DEFAULT '0' COMMENT '创建人',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `update_user` bigint NOT NULL DEFAULT '0' COMMENT '更新人',
  `extra_amount` decimal(10,2) DEFAULT NULL COMMENT '保障服务费（单个商品）',
  `service_info` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '保障服务信息（JSON）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cart_user_sku` (`user_id`,`sku_id`),
  KEY `idx_cart_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=59 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车（简化）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类名称',
  `type` tinyint NOT NULL DEFAULT '1' COMMENT '类型：1 商品分类，2 售后分类',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序，越大越靠前',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 启用，0 禁用',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `create_user` bigint NOT NULL COMMENT '创建人',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `update_user` bigint NOT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_name_type` (`name`,`type`)
) ENGINE=InnoDB AUTO_INCREMENT=284 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品/售后分类表（最多 2 级）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_checkin_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '鐢ㄦ埛ID',
  `checkin_date` date NOT NULL COMMENT '绛惧埌鏃ユ湡',
  `points_earned` int NOT NULL DEFAULT '0' COMMENT '鑾峰緱绉?垎',
  `create_time` datetime NOT NULL COMMENT '绛惧埌鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`,`checkin_date`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='绛惧埌璁板綍';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '优惠券名称',
  `type` tinyint NOT NULL DEFAULT '1' COMMENT '类型：1 满减券，2 折扣券',
  `condition_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '使用门槛：满多少可用（满减）',
  `discount_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '满减金额 / 折扣比例（0.85=85折）',
  `total_count` int NOT NULL COMMENT '总发放数量',
  `received_count` int NOT NULL DEFAULT '0' COMMENT '已领取数量',
  `used_count` int NOT NULL DEFAULT '0' COMMENT '已使用数量',
  `per_user_limit` int NOT NULL DEFAULT '1' COMMENT '每人限领张数',
  `start_time` datetime NOT NULL COMMENT '领取开始时间',
  `end_time` datetime NOT NULL COMMENT '领取结束时间',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 启用，0 停用',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `create_user` bigint NOT NULL COMMENT '创建人',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `update_user` bigint NOT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_coupon_status` (`status`,`start_time`,`end_time`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_email_code` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '邮箱',
  `code` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '验证码',
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LOGIN' COMMENT '类型：LOGIN/REGISTER/BIND',
  `used` tinyint NOT NULL DEFAULT '0' COMMENT '是否已使用：0否 1是',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_expire` (`expire_time`),
  KEY `idx_email_type` (`email`,`type`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='短信验证���';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_employee` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录用户名',
  `name` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '姓名',
  `password_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码哈希（BCrypt）',
  `phone` varchar(11) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `sex` varchar(2) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '性别：男/女',
  `id_number` varchar(18) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '身份证号',
  `avatar` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像 URL',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 启用，0 锁定',
  `role` tinyint NOT NULL DEFAULT '2' COMMENT '角色：1 超级管理员，2 运营，3 客服，4 财务',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `create_user` bigint NOT NULL COMMENT '创建人',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `update_user` bigint NOT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_employee_username` (`username`),
  KEY `idx_employee_phone` (`phone`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城员工表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `spu_id` bigint NOT NULL COMMENT 'SPU ID',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `create_user` bigint NOT NULL DEFAULT '0' COMMENT '创建人',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `update_user` bigint NOT NULL DEFAULT '0' COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_user_spu` (`user_id`,`spu_id`),
  KEY `idx_favorite_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏夹';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_home_module` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '妯″潡ID',
  `type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'HOT_PRODUCTS' COMMENT '妯″潡绫诲瀷锛欱ANNER/HOT_PRODUCTS/NEW_PRODUCTS/CATEGORY_SHOWCASE/COUPON_ENTRY/PRODUCT_GRID/SINGLE_IMAGE',
  `title` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '妯″潡鏍囬?',
  `config` text COLLATE utf8mb4_unicode_ci COMMENT '妯″潡閰嶇疆JSON',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '鎺掑簭鍊硷紝瓒婂皬瓒婇潬鍓',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '鐘舵?锛?鍚?敤 0绂佺敤',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍒涘缓鏃堕棿',
  `create_user` bigint DEFAULT NULL COMMENT '鍒涘缓浜',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  `update_user` bigint DEFAULT NULL COMMENT '鏇存柊浜',
  PRIMARY KEY (`id`),
  KEY `idx_type` (`type`),
  KEY `idx_sort` (`sort_order`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='棣栭〉瑁呬慨妯″潡閰嶇疆';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_mall_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订单号',
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `total_amount` decimal(10,2) NOT NULL COMMENT '商品总金额',
  `pay_amount` decimal(10,2) NOT NULL COMMENT '实付金额',
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待付款 / PAID 已付款 / COMPLETED 已完成 / CANCELLED 已取消',
  `is_refunded` tinyint NOT NULL DEFAULT '0' COMMENT '是否已退款：1 是 0 否',
  `address` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '收货地址快照',
  `consignee` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '收货人快照',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '手机号快照',
  `pay_method` tinyint DEFAULT NULL COMMENT '支付方式：1 微信 2 支付宝 3 模拟支付 4 银行卡',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '买家备注',
  `tracking_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '蹇??鍗曞彿',
  `delivery_company` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '蹇??鍏?徃',
  `delivery_time` datetime DEFAULT NULL COMMENT '鍙戣揣鏃堕棿',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `create_user` bigint NOT NULL DEFAULT '0' COMMENT '创建人',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `update_user` bigint NOT NULL DEFAULT '0' COMMENT '更新人',
  `points_deducted` int NOT NULL DEFAULT '0' COMMENT '浣跨敤鐨勭Н鍒嗘暟锛堝喕缁撳悗瀹為檯鎵ｉ櫎锛',
  `points_amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '绉?垎鎶垫墸閲戦?(鍏?, 100绉?垎=1鍏',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mall_order_no` (`order_no`),
  KEY `idx_mall_order_user` (`user_id`),
  KEY `idx_mall_order_status` (`status`)
) ENGINE=InnoDB AUTO_INCREMENT=89 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城订单（简化）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_mall_order_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '订单 ID',
  `spu_id` bigint NOT NULL COMMENT 'SPU ID 快照',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID 快照',
  `spu_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SPU 名称快照',
  `sku_specs` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SKU 规格文本快照',
  `price` decimal(10,2) NOT NULL COMMENT '单价快照',
  `qty` int NOT NULL COMMENT '数量快照',
  `subtotal` decimal(10,2) NOT NULL COMMENT '小计快照 = price * qty',
  `extra_amount` decimal(12,2) DEFAULT NULL COMMENT '额外费用（保障服务等），单位元',
  `service_info` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '保障服务信息（JSON）',
  PRIMARY KEY (`id`),
  KEY `idx_mall_order_item_order` (`order_id`),
  KEY `idx_mall_order_item_sku` (`sku_id`)
) ENGINE=InnoDB AUTO_INCREMENT=98 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城订单明细（简化）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_mall_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `nickname` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称',
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'BCrypt 密码哈希（和 stellar_employee.password_hash 同算法）',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1 正常，0 冻结',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `create_user` bigint NOT NULL DEFAULT '0' COMMENT '创建人，0=自注册',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `update_user` bigint NOT NULL DEFAULT '0' COMMENT '更新人',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='C端用户表（简化）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_notification_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id` bigint DEFAULT NULL COMMENT '接收用户ID',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '接收手机号',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '接收邮箱',
  `channel` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SMS' COMMENT '渠道：SMS/EMAIL',
  `type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型：VERIFY_CODE/ORDER_SHIPPED/ORDER_RECEIVED/COUPON_EXPIRE/MARKETING',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标题（邮件用）',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知内容',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '发送状态：0待发送 1成功 2失败',
  `error_msg` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '失败原因',
  `send_time` datetime DEFAULT NULL COMMENT '实际发送时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_type` (`type`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=56 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知日志';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_points_payment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL COMMENT '璁㈠崟ID',
  `user_id` bigint NOT NULL COMMENT '鐢ㄦ埛ID',
  `points` int NOT NULL COMMENT '鍙樺姩鐨勭Н鍒嗘暟',
  `amount` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '瀵瑰簲鐨勯噾棰',
  `type` tinyint NOT NULL COMMENT '绫诲瀷: 1鍐荤粨 2瀹為檯鎵ｉ櫎 3閫?繕 4鍙栨秷瑙ｅ喕',
  `biz_desc` varchar(200) DEFAULT NULL COMMENT '涓氬姟鎻忚堪',
  `create_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='绉?垎鎶垫墸鏀?粯璁板綍锛堣拷婧?敤锛';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_points_product` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(200) NOT NULL COMMENT '鍟嗗搧鍚嶇О',
  `product_type` varchar(50) NOT NULL COMMENT '绫诲瀷: COUPON(浼樻儬鍒? / PHYSICAL(瀹炵墿)',
  `points_price` int NOT NULL COMMENT '鎵?渶绉?垎鏁',
  `stock` int NOT NULL DEFAULT '0' COMMENT '搴撳瓨',
  `image_url` varchar(500) DEFAULT NULL COMMENT '鍟嗗搧鍥剧墖',
  `description` varchar(1000) DEFAULT NULL COMMENT '鍟嗗搧鎻忚堪',
  `coupon_id` bigint DEFAULT NULL COMMENT '鍏宠仈浼樻儬鍒窱D(COUPON绫诲瀷)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '鐘舵?: 1涓婃灦 0涓嬫灦',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '鎺掑簭(瓒婂皬瓒婇潬鍓?',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  `create_user` bigint NOT NULL DEFAULT '0' COMMENT '鍒涘缓浜',
  `update_time` datetime NOT NULL COMMENT '鏇存柊鏃堕棿',
  `update_user` bigint NOT NULL DEFAULT '0' COMMENT '鏇存柊浜',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='绉?垎鍟嗗煄鍟嗗搧';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_points_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '鐢ㄦ埛ID',
  `type` tinyint NOT NULL COMMENT '绫诲瀷: 1鑾峰緱 2娑堣垂 3杩囨湡鎵ｉ櫎 4绠＄悊鍛樿皟鏁',
  `points` int NOT NULL COMMENT '绉?垎鍙樺姩(姝ｆ暟鑾峰緱, 璐熸暟娑堣垂)',
  `balance_after` int NOT NULL COMMENT '鍙樻洿鍚庡彲鐢ㄤ綑棰',
  `biz_type` varchar(50) NOT NULL COMMENT '涓氬姟绫诲瀷: ORDER / CHECKIN / REVIEW / REDEEM / EXPIRE / ADMIN',
  `biz_id` varchar(100) DEFAULT NULL COMMENT '鍏宠仈涓氬姟ID',
  `description` varchar(200) DEFAULT NULL COMMENT '鎻忚堪',
  `expired_time` date DEFAULT NULL COMMENT '璇ョ瑪绉?垎鍒版湡鏃堕棿(鑾峰緱绫诲瀷鏃惰?褰?',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`),
  KEY `idx_user_biz` (`user_id`,`biz_type`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='绉?垎娴佹按璁板綍';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_points_redemption` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '鐢ㄦ埛ID',
  `product_id` bigint NOT NULL COMMENT '鍏戞崲鍟嗗搧ID',
  `product_name` varchar(200) NOT NULL COMMENT '鍟嗗搧鍚嶇О(蹇?収)',
  `points_cost` int NOT NULL COMMENT '娑堣?绉?垎鏁',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '鐘舵?: 1宸插厬鎹?2宸插彂鏀?3宸插彇娑',
  `coupon_id` bigint DEFAULT NULL COMMENT '鍙戞斁鐨勭敤鎴蜂紭鎯犲埜ID(COUPON绫诲瀷)',
  `address_id` bigint DEFAULT NULL COMMENT '鏀惰揣鍦板潃ID(PHYSICAL绫诲瀷)',
  `remark` varchar(300) DEFAULT NULL COMMENT '澶囨敞',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='绉?垎鍏戞崲璁板綍';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_points_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `rule_type` varchar(50) NOT NULL COMMENT '瑙勫垯绫诲瀷: ORDER(涓嬪崟) / CHECKIN(绛惧埌) / REVIEW(璇勪环)',
  `rule_name` varchar(100) NOT NULL COMMENT '瑙勫垯鍚嶇О',
  `earn_points` int NOT NULL DEFAULT '0' COMMENT '鍗曟?鑾峰緱绉?垎鏁',
  `condition_value` decimal(10,2) DEFAULT NULL COMMENT '鏉′欢鍊?濡?ORDER 绫诲瀷琛ㄧず"姣忔秷璐筃鍏冨緱1绉?垎", 杩欓噷瀛?N)',
  `max_per_day` int DEFAULT NULL COMMENT '姣忔棩涓婇檺(娆℃垨鍒?',
  `max_per_order` int DEFAULT NULL COMMENT '姣忓崟涓婇檺',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '鐘舵?: 1鍚?敤 0绂佺敤',
  `description` varchar(500) DEFAULT NULL COMMENT '瑙勫垯鎻忚堪',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  `create_user` bigint NOT NULL DEFAULT '0' COMMENT '鍒涘缓浜',
  `update_time` datetime NOT NULL COMMENT '鏇存柊鏃堕棿',
  `update_user` bigint NOT NULL DEFAULT '0' COMMENT '鏇存柊浜',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='绉?垎瑙勫垯閰嶇疆';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_rag_conversations` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `title` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `summary` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `pinned` tinyint(1) NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_stellar_rag_conversations_id` (`id`),
  KEY `ix_stellar_rag_conversations_user_id` (`user_id`),
  KEY `ix_stellar_rag_conversations_created_at` (`created_at`),
  CONSTRAINT `stellar_rag_conversations_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `stellar_rag_users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=110 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_rag_kb_documents` (
  `id` int NOT NULL AUTO_INCREMENT,
  `filename` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_path` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_ext` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_size` bigint NOT NULL,
  `chunk_count` int NOT NULL,
  `tags` varchar(512) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `error_msg` text COLLATE utf8mb4_unicode_ci,
  `created_by` int NOT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_stellar_rag_kb_documents_filename` (`filename`),
  KEY `ix_stellar_rag_kb_documents_id` (`id`),
  KEY `ix_stellar_rag_kb_documents_created_at` (`created_at`)
) ENGINE=InnoDB AUTO_INCREMENT=88 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_rag_messages` (
  `id` int NOT NULL AUTO_INCREMENT,
  `conversation_id` int NOT NULL,
  `user_id` int NOT NULL,
  `role` enum('USER','ASSISTANT','SYSTEM') COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `sources` json DEFAULT NULL,
  `feedback` int NOT NULL,
  `tokens_used` int NOT NULL,
  `latency_ms` int NOT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_stellar_rag_messages_user_id` (`user_id`),
  KEY `ix_stellar_rag_messages_created_at` (`created_at`),
  KEY `ix_stellar_rag_messages_id` (`id`),
  KEY `ix_stellar_rag_messages_conversation_id` (`conversation_id`),
  CONSTRAINT `stellar_rag_messages_ibfk_1` FOREIGN KEY (`conversation_id`) REFERENCES `stellar_rag_conversations` (`id`) ON DELETE CASCADE,
  CONSTRAINT `stellar_rag_messages_ibfk_2` FOREIGN KEY (`user_id`) REFERENCES `stellar_rag_users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=380 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_rag_operation_logs` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int DEFAULT NULL,
  `username` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `action` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `resource` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `detail` text COLLATE utf8mb4_unicode_ci,
  `ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_stellar_rag_operation_logs_created_at` (`created_at`),
  KEY `ix_stellar_rag_operation_logs_action` (`action`),
  KEY `ix_stellar_rag_operation_logs_user_id` (`user_id`),
  KEY `ix_stellar_rag_operation_logs_id` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=164 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_rag_sync_outbox` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `biz_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务类型：SPU / POLICY / COUPON / BRAND / CATEGORY / DOC',
  `biz_id` bigint NOT NULL COMMENT '业务主键 id（spu_id / coupon_id / ...）',
  `op_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SAVE' COMMENT '操作：SAVE/UPDATE/ONSHELF/OFFSHELF/DELETE/SYNC_POLICY…',
  `synced` tinyint NOT NULL DEFAULT '0' COMMENT '是否已同步：1 是 / 0 否',
  `failed` tinyint NOT NULL DEFAULT '0' COMMENT '是否已达最大重试次数：1 是 / 0 否',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '已尝试次数（成功也会记 1，失败每次 +1）',
  `max_attempt` int NOT NULL DEFAULT '3' COMMENT '最大尝试次数，默认 3',
  `last_try_time` datetime DEFAULT NULL COMMENT '最后一次尝试时间',
  `last_error_msg` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最后一次失败摘要',
  `payload_json` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '请求体快照（可空，便于手工重放）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `create_user` bigint NOT NULL COMMENT '创建人',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `update_user` bigint NOT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_outbox_status` (`synced`,`failed`,`last_try_time`),
  KEY `idx_outbox_biz` (`biz_type`,`biz_id`)
) ENGINE=InnoDB AUTO_INCREMENT=225 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG同步发件箱（事务性发件箱模式）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_rag_users` (
  `id` int NOT NULL AUTO_INCREMENT,
  `username` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `nickname` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role` enum('ADMIN','USER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_active` tinyint(1) NOT NULL,
  `last_login_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `updated_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ix_stellar_rag_users_username` (`username`),
  UNIQUE KEY `ix_stellar_rag_users_email` (`email`),
  KEY `ix_stellar_rag_users_id` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `spu_id` bigint NOT NULL COMMENT 'SPU ID',
  `sku_id` bigint DEFAULT NULL COMMENT 'SKU ID',
  `order_id` bigint DEFAULT NULL COMMENT '订单ID',
  `order_no` varchar(50) DEFAULT '' COMMENT '订单号',
  `rating` tinyint NOT NULL DEFAULT '5' COMMENT '评分 1-5',
  `content` text COMMENT '评价内容',
  `pics` varchar(1000) DEFAULT '' COMMENT '图片URL，逗号分隔',
  `reply` varchar(500) DEFAULT '' COMMENT '管理员回复',
  `reply_time` datetime DEFAULT NULL COMMENT '回复时间',
  `status` tinyint DEFAULT '1' COMMENT '1=显示 0=隐藏',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_user` bigint DEFAULT '0',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `update_user` bigint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_spu_id` (`spu_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB AUTO_INCREMENT=496 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品评价';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_review_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `review_id` bigint NOT NULL COMMENT '评价ID',
  `user_id` bigint NOT NULL COMMENT '回复用户ID',
  `content` varchar(500) NOT NULL COMMENT '回复内容',
  `status` tinyint DEFAULT '1' COMMENT '1=正常 0=隐藏',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_user` bigint DEFAULT '0',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `update_user` bigint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_review_id` (`review_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='评价回复';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_service_item` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '涓氬姟鏍囪瘑锛堝揩鐓?id锛岀ǔ瀹氫笉鍙橈級',
  `title` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '灞曠ず鍚嶇О锛屽?锛氱?灞忛櫓 路 1骞',
  `price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '鍗曚环锛堝厓/浠讹紝闅忓晢鍝佹暟閲忓?涔橈級',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '璇存槑锛堥?濉?級',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1 鍚?敤 0 鍋滅敤',
  `create_time` datetime NOT NULL,
  `update_time` datetime NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='淇濋殰鏈嶅姟瀛楀吀';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_site_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '閰嶇疆ID',
  `config_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '閰嶇疆閿?紙home_bg锛',
  `config_value` text COLLATE utf8mb4_unicode_ci COMMENT '閰嶇疆鍊?JSON锛屽? {"bgImage":"https://..."}',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '閰嶇疆璇存槑',
  `update_user` bigint DEFAULT NULL COMMENT '鏈?悗鎿嶄綔浜',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='绔欑偣绾ч厤缃';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_sku` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `spu_id` bigint NOT NULL COMMENT '所属 SPU ID',
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SKU 完整名（自动拼接规格，建议「SPU 名 · 规格1 · 规格2」形式）',
  `specs` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规格文本（分号分隔，例：屏幕:55寸;内存:4G+64G）— 轻量展示优先',
  `specs_json` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '规格键值对 JSON：{"颜色":"钛黑","内存":"12GB","存储":"256GB"}',
  `price` decimal(10,2) NOT NULL COMMENT '销售价',
  `original_price` decimal(10,2) DEFAULT NULL COMMENT '原价/划线价（M1 测试用）',
  `cost_price` decimal(10,2) DEFAULT NULL COMMENT '成本价',
  `market_price` decimal(10,2) DEFAULT NULL COMMENT '市场价/参考价',
  `stock` int NOT NULL DEFAULT '0' COMMENT '当前库存（乐观锁扣减）',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号：stock 扣减/回滚 WHERE id=? AND version=?',
  `warn_stock` int NOT NULL DEFAULT '10' COMMENT '预警库存',
  `weight_g` int DEFAULT NULL COMMENT '重量（克）',
  `barcode` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '条形码',
  `image` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'SKU 独立图',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 在售，0 停售',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `create_user` bigint NOT NULL COMMENT '创建人',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `update_user` bigint NOT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_sku_spu` (`spu_id`),
  KEY `idx_sku_barcode` (`barcode`),
  KEY `idx_sku_version` (`id`,`version`)
) ENGINE=InnoDB AUTO_INCREMENT=531 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SKU库存单元';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_spu` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'SPU 名称',
  `sub_title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '副标题/卖点一句话',
  `category_id` bigint NOT NULL COMMENT '一级分类 ID',
  `description` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '商品详情长文本（HTML/富文本，可选）',
  `description_md` mediumtext COLLATE utf8mb4_unicode_ci COMMENT '商品详情 Markdown —— 主要同步给 RAG 知识库',
  `main_image` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '主图 URL',
  `sub_images` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '副图 URL 列表（分号分隔）',
  `slider_images` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '轮播图 JSON 数组',
  `sale_count` int NOT NULL DEFAULT '0' COMMENT '累计销量',
  `comment_count` int NOT NULL DEFAULT '0' COMMENT '评价数',
  `total_stock` int NOT NULL DEFAULT '0' COMMENT '总库存（所有 SKU stock 之和，展示用）',
  `sku_count` int NOT NULL DEFAULT '0' COMMENT 'SKU 数量',
  `min_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '最低 SKU 价格（展示用）',
  `max_price` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '最高 SKU 价格',
  `is_new` tinyint NOT NULL DEFAULT '0' COMMENT '是否新品 1 是 0 否',
  `is_hot` tinyint NOT NULL DEFAULT '0' COMMENT '是否热卖 1 是 0 否',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序值',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 上架，0 下架',
  `on_shelf_time` datetime DEFAULT NULL COMMENT '最近一次上架时间',
  `off_shelf_time` datetime DEFAULT NULL COMMENT '最近一次下架时间',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `create_user` bigint NOT NULL COMMENT '创建人',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `update_user` bigint NOT NULL COMMENT '更新人',
  `per_user_limit` int NOT NULL DEFAULT '0' COMMENT '每人限购数量，0=不限购',
  PRIMARY KEY (`id`),
  KEY `idx_spu_status` (`status`),
  KEY `idx_spu_sort` (`sort`)
) ENGINE=InnoDB AUTO_INCREMENT=181 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SPU标准产品单元';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_spu_service` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `spu_id` bigint NOT NULL,
  `service_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_spu_service` (`spu_id`,`service_id`)
) ENGINE=InnoDB AUTO_INCREMENT=184 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鍟嗗搧-淇濋殰鏈嶅姟鍏宠仈';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_stock_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `sku_id` bigint NOT NULL COMMENT 'SKU ID',
  `type` tinyint NOT NULL COMMENT '变动类型：1 入库，2 出库，3 盘盈，4 盘亏，5 调整',
  `quantity` int NOT NULL COMMENT '变动数量（正数增加，负数减少）',
  `stock_before` int NOT NULL COMMENT '变动前库存',
  `stock_after` int NOT NULL COMMENT '变动后库存',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `business_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务类型：PURCHASE_IN / SALE_OUT / INVENTORY_PROFIT / INVENTORY_LOSS / ADJUSTMENT',
  `business_no` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联业务单号',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `create_user` bigint NOT NULL DEFAULT '0' COMMENT '操作人',
  PRIMARY KEY (`id`),
  KEY `idx_stock_log_sku` (`sku_id`),
  KEY `idx_stock_log_type` (`type`),
  KEY `idx_stock_log_business` (`business_type`,`business_no`),
  KEY `idx_stock_log_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存变动流水表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_user_address` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `consignee` varchar(50) NOT NULL COMMENT '收货人姓名',
  `phone` varchar(20) NOT NULL COMMENT '联系电话',
  `province` varchar(50) DEFAULT '' COMMENT '省',
  `city` varchar(50) DEFAULT '' COMMENT '市',
  `district` varchar(50) DEFAULT '' COMMENT '区',
  `detail` varchar(255) NOT NULL COMMENT '详细地址',
  `is_default` tinyint DEFAULT '0' COMMENT '1=默认地址 0=非默认',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `create_user` bigint DEFAULT '0',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `update_user` bigint DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户收货地址';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_user_behavior` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `user_id` bigint DEFAULT NULL COMMENT '鐧诲綍鐢ㄦ埛 ID锛堟父瀹?负 NULL锛',
  `device_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '' COMMENT '鍓嶇?鍖垮悕璁惧? ID锛坙ocalStorage 鐢熸垚锛',
  `event_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '浜嬩欢绫诲瀷锛歷iew_item_list/view_item/search/add_to_cart/order_placed/favorite/page_view',
  `spu_id` bigint DEFAULT NULL COMMENT '鍏宠仈 SPU ID',
  `sku_id` bigint DEFAULT NULL COMMENT '鍏宠仈 SKU ID',
  `category_id` bigint DEFAULT NULL COMMENT '鍏宠仈鍒嗙被 ID',
  `keyword` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鎼滅储璇嶏紙search 浜嬩欢锛',
  `scene` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鏉ユ簮鍦烘櫙锛歨ome/search/category/detail/cart/order/favorites',
  `position` int DEFAULT NULL COMMENT '鍒楄〃浣嶆?锛堜粠 1 寮??锛寁iew_item_list 鐢?級',
  `amount` decimal(10,2) DEFAULT NULL COMMENT '閲戦?锛堝晢鍝佷环/涓嬪崟閲戦?锛',
  `duration_ms` int DEFAULT NULL COMMENT '鍋滅暀鏃堕暱锛坴iew_item 绂诲紑鏃惰ˉ鎶ワ級',
  `extra` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鎵╁睍 JSON锛堟帓搴忔柟寮?鍒嗛〉绛夛級',
  `client_ip` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '瀹㈡埛绔?IP锛堝悗绔??褰曪級',
  `user_agent` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'User-Agent锛堝悗绔??褰曪級',
  `event_time` datetime NOT NULL COMMENT '鍓嶇?浜嬩欢鍙戠敓鏃堕棿',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '鍏ュ簱鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_behavior_user_time` (`user_id`,`event_time`),
  KEY `idx_behavior_dev_time` (`device_id`,`event_time`),
  KEY `idx_behavior_type_time` (`event_type`,`event_time`),
  KEY `idx_behavior_spu` (`spu_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1090 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鐢ㄦ埛琛屼负鍩嬬偣鏃ュ織锛堝彧杩藉姞锛';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_user_coupon` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户 ID',
  `coupon_id` bigint NOT NULL COMMENT '券模板 ID',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 未使用，2 已使用，3 已过期',
  `order_id` bigint DEFAULT NULL COMMENT '使用的订单 ID',
  `used_time` datetime DEFAULT NULL COMMENT '使用时间',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `create_user` bigint NOT NULL DEFAULT '0' COMMENT '创建人',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `update_user` bigint NOT NULL DEFAULT '0' COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_user_coupon_user` (`user_id`),
  KEY `idx_user_coupon_coupon` (`coupon_id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户领券记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_user_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `user_id` bigint NOT NULL COMMENT '鐢ㄦ埛 ID',
  `type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '绫诲瀷锛歄RDER_SHIPPED / ORDER_CANCELLED / SYSTEM / COUPON',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '娑堟伅鏍囬?',
  `content` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '娑堟伅姝ｆ枃',
  `ref_id` bigint DEFAULT NULL COMMENT '鍏宠仈涓氬姟 ID锛堝?璁㈠崟 ID锛',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '0 鏈?? / 1 宸茶?',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_user_msg_user` (`user_id`),
  KEY `idx_user_msg_unread` (`user_id`,`is_read`)
) ENGINE=InnoDB AUTO_INCREMENT=129 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鐢ㄦ埛娑堟伅閫氱煡';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_user_points` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '鐢ㄦ埛ID',
  `total_points` int NOT NULL DEFAULT '0' COMMENT '鎬荤Н鍒?鍚?嵆灏嗚繃鏈?',
  `available_points` int NOT NULL DEFAULT '0' COMMENT '鍙?敤绉?垎',
  `frozen_points` int NOT NULL DEFAULT '0' COMMENT '鍐荤粨绉?垎(鍏戞崲涓?',
  `total_earned` int NOT NULL DEFAULT '0' COMMENT '绱??鑾峰緱绉?垎',
  `total_spent` int NOT NULL DEFAULT '0' COMMENT '绱??娑堣垂绉?垎',
  `version` int NOT NULL DEFAULT '0' COMMENT '涔愯?閿佺増鏈?彿',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  `update_time` datetime NOT NULL COMMENT '鏇存柊鏃堕棿',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鐢ㄦ埛绉?垎姹囨?';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_wallet` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `user_id` bigint NOT NULL COMMENT '鐢ㄦ埛 ID',
  `balance` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '鍙?敤浣欓?锛堝厓锛',
  `frozen` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '鍐荤粨閲戦?锛堝厓锛',
  `total_recharge` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '绱??鍏呭?',
  `total_spent` decimal(12,2) NOT NULL DEFAULT '0.00' COMMENT '绱??娑堣垂',
  `version` int NOT NULL DEFAULT '0' COMMENT '涔愯?閿佺増鏈?彿',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  `create_user` bigint NOT NULL DEFAULT '0' COMMENT '鍒涘缓浜',
  `update_time` datetime NOT NULL COMMENT '鏇存柊鏃堕棿',
  `update_user` bigint NOT NULL DEFAULT '0' COMMENT '鏇存柊浜',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wallet_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='鐢ㄦ埛閽卞寘';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `stellar_wallet_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '涓婚敭',
  `wallet_id` bigint NOT NULL COMMENT '閽卞寘 ID',
  `user_id` bigint NOT NULL COMMENT '鐢ㄦ埛 ID锛堝啑浣欏姞閫熸煡璇?級',
  `type` tinyint NOT NULL COMMENT '浜ゆ槗绫诲瀷锛? 鍏呭?锛? 娑堣垂锛? 閫??锛? 鎻愮幇',
  `amount` decimal(12,2) NOT NULL COMMENT '浜ゆ槗閲戦?锛堝厖鍊间负姝ｏ紝娑堣垂涓鸿礋锛',
  `balance_after` decimal(12,2) NOT NULL COMMENT '浜ゆ槗鍚庝綑棰',
  `channel` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '娓犻亾锛歐ECHAT/ALIPAY/WALLET/ADMIN',
  `biz_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '鍏宠仈涓氬姟锛歊ECHARGE/ORDER/REFUND',
  `biz_id` bigint DEFAULT NULL COMMENT '鍏宠仈涓氬姟 ID',
  `remark` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '澶囨敞',
  `create_time` datetime NOT NULL COMMENT '鍒涘缓鏃堕棿',
  PRIMARY KEY (`id`),
  KEY `idx_wtx_wallet` (`wallet_id`),
  KEY `idx_wtx_user` (`user_id`),
  KEY `idx_wtx_biz` (`biz_type`,`biz_id`)
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='閽卞寘浜ゆ槗娴佹按';
/*!40101 SET character_set_client = @saved_cs_client */;
SET FOREIGN_KEY_CHECKS=1;
