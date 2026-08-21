-- ============================================
-- 星耀商城 功能增强 数据库迁移脚本
-- 日期: 2026-07-20
-- 包含: 收货地址、商品评价、优惠券、订单中增加收货人字段
-- ============================================

-- 1. 收货地址表
CREATE TABLE IF NOT EXISTS stellar_user_address (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    consignee VARCHAR(50) NOT NULL COMMENT '收货人姓名',
    phone VARCHAR(20) NOT NULL COMMENT '联系电话',
    province VARCHAR(50) DEFAULT '' COMMENT '省',
    city VARCHAR(50) DEFAULT '' COMMENT '市',
    district VARCHAR(50) DEFAULT '' COMMENT '区',
    detail VARCHAR(255) NOT NULL COMMENT '详细地址',
    is_default TINYINT DEFAULT 0 COMMENT '1=默认地址 0=非默认',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_user BIGINT DEFAULT 0,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_user BIGINT DEFAULT 0,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收货地址';

-- 2. 商品评价表
CREATE TABLE IF NOT EXISTS stellar_review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    spu_id BIGINT NOT NULL COMMENT 'SPU ID',
    sku_id BIGINT DEFAULT NULL COMMENT 'SKU ID',
    order_id BIGINT DEFAULT NULL COMMENT '订单ID',
    order_no VARCHAR(50) DEFAULT '' COMMENT '订单号',
    rating TINYINT NOT NULL DEFAULT 5 COMMENT '评分 1-5',
    content TEXT COMMENT '评价内容',
    pics VARCHAR(1000) DEFAULT '' COMMENT '图片URL，逗号分隔',
    reply VARCHAR(500) DEFAULT '' COMMENT '管理员回复',
    reply_time DATETIME DEFAULT NULL COMMENT '回复时间',
    status TINYINT DEFAULT 1 COMMENT '1=显示 0=隐藏',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_user BIGINT DEFAULT 0,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_user BIGINT DEFAULT 0,
    INDEX idx_spu_id (spu_id),
    INDEX idx_user_id (user_id),
    INDEX idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品评价';

-- 3. 优惠券模板表
CREATE TABLE IF NOT EXISTS stellar_coupon (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL COMMENT '优惠券名称',
    type TINYINT NOT NULL DEFAULT 1 COMMENT '1=满减券 2=折扣券',
    condition_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '使用门槛金额',
    discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '满减金额或折扣比例(0.85=85折)',
    total_count INT NOT NULL DEFAULT 100 COMMENT '发放总量',
    received_count INT NOT NULL DEFAULT 0 COMMENT '已领取量',
    used_count INT NOT NULL DEFAULT 0 COMMENT '已使用量',
    per_user_limit INT NOT NULL DEFAULT 1 COMMENT '每人限领数量',
    start_time DATETIME NOT NULL COMMENT '有效期开始',
    end_time DATETIME NOT NULL COMMENT '有效期结束',
    status TINYINT DEFAULT 1 COMMENT '1=启用 0=禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_user BIGINT DEFAULT 0,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_user BIGINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板';

-- 4. 用户优惠券表
CREATE TABLE IF NOT EXISTS stellar_user_coupon (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    coupon_id BIGINT NOT NULL COMMENT '优惠券ID',
    status TINYINT DEFAULT 1 COMMENT '1=未使用 2=已使用 3=已过期',
    order_id BIGINT DEFAULT NULL COMMENT '使用订单ID',
    used_time DATETIME DEFAULT NULL COMMENT '使用时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_user BIGINT DEFAULT 0,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_user BIGINT DEFAULT 0,
    INDEX idx_user_id (user_id),
    INDEX idx_coupon_id (coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券';

-- 5. 订单表增加收货人字段（如果不存在）
-- ALTER TABLE stellar_mall_order ADD COLUMN consignee VARCHAR(50) DEFAULT '' COMMENT '收货人' AFTER address;
-- ALTER TABLE stellar_mall_order ADD COLUMN phone VARCHAR(20) DEFAULT '' COMMENT '联系电话' AFTER consignee;

-- 6. 首页轮播图表
CREATE TABLE IF NOT EXISTS stellar_banner (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL COMMENT '标题',
    image_url VARCHAR(500) NOT NULL COMMENT '图片URL',
    link_url VARCHAR(500) DEFAULT '' COMMENT '点击跳转链接',
    sort INT DEFAULT 0 COMMENT '排序值，越大越靠前',
    status TINYINT DEFAULT 1 COMMENT '1=启用 0=禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_user BIGINT DEFAULT 0,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_user BIGINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页轮播图';

-- 7. 评价回复表（用户可以互相回复评价）
CREATE TABLE IF NOT EXISTS stellar_review_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL COMMENT '评价ID',
    user_id BIGINT NOT NULL COMMENT '回复用户ID',
    content VARCHAR(500) NOT NULL COMMENT '回复内容',
    status TINYINT DEFAULT 1 COMMENT '1=正常 0=隐藏',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_user BIGINT DEFAULT 0,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_user BIGINT DEFAULT 0,
    INDEX idx_review_id (review_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价回复';