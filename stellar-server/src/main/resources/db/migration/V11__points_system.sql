-- ============================================================
-- 积分系统数据库迁移脚本
-- 包含：积分规则、用户积分汇总、积分流水、积分商城商品、兑换记录、签到记录
-- ============================================================

-- 1. 积分规则配置
CREATE TABLE IF NOT EXISTS stellar_points_rule (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    rule_type       VARCHAR(50)     NOT NULL COMMENT '规则类型: ORDER(下单) / CHECKIN(签到) / REVIEW(评价)',
    rule_name       VARCHAR(100)    NOT NULL COMMENT '规则名称',
    earn_points     INT             NOT NULL DEFAULT 0 COMMENT '单次获得积分数',
    condition_value DECIMAL(10,2)   DEFAULT NULL COMMENT '条件值(如 ORDER 类型表示"每消费N元得1积分", 这里存 N)',
    max_per_day     INT             DEFAULT NULL COMMENT '每日上限(次或分)',
    max_per_order   INT             DEFAULT NULL COMMENT '每单上限',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '状态: 1启用 0禁用',
    description     VARCHAR(500)    DEFAULT NULL COMMENT '规则描述',
    create_time     DATETIME        NOT NULL COMMENT '创建时间',
    create_user     BIGINT          NOT NULL DEFAULT 0 COMMENT '创建人',
    update_time     DATETIME        NOT NULL COMMENT '更新时间',
    update_user     BIGINT          NOT NULL DEFAULT 0 COMMENT '更新人'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分规则配置';

-- 2. 用户积分汇总 (每个用户一条记录)
CREATE TABLE IF NOT EXISTS stellar_user_points (
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL COMMENT '用户ID',
    total_points     INT          NOT NULL DEFAULT 0 COMMENT '总积分(含即将过期)',
    available_points INT          NOT NULL DEFAULT 0 COMMENT '可用积分',
    frozen_points    INT          NOT NULL DEFAULT 0 COMMENT '冻结积分(兑换中)',
    total_earned     INT          NOT NULL DEFAULT 0 COMMENT '累计获得积分',
    total_spent      INT          NOT NULL DEFAULT 0 COMMENT '累计消费积分',
    version          INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    create_time      DATETIME     NOT NULL COMMENT '创建时间',
    update_time      DATETIME     NOT NULL COMMENT '更新时间',
    UNIQUE KEY uk_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户积分汇总';

-- 3. 积分流水记录 (只追加，不修改/删除)
CREATE TABLE IF NOT EXISTS stellar_points_record (
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       NOT NULL COMMENT '用户ID',
    type           TINYINT      NOT NULL COMMENT '类型: 1获得 2消费 3过期扣除 4管理员调整',
    points         INT          NOT NULL COMMENT '积分变动(正数获得, 负数消费)',
    balance_after  INT          NOT NULL COMMENT '变更后可用余额',
    biz_type       VARCHAR(50)  NOT NULL COMMENT '业务类型: ORDER / CHECKIN / REVIEW / REDEEM / EXPIRE / ADMIN',
    biz_id         VARCHAR(100) DEFAULT NULL COMMENT '关联业务ID',
    description    VARCHAR(200) DEFAULT NULL COMMENT '描述',
    expired_time   DATE         DEFAULT NULL COMMENT '该笔积分到期时间(获得类型时记录)',
    create_time    DATETIME     NOT NULL COMMENT '创建时间',
    INDEX idx_user_time (user_id, create_time),
    INDEX idx_user_biz (user_id, biz_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分流水记录';

-- 4. 积分商城商品
CREATE TABLE IF NOT EXISTS stellar_points_product (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(200) NOT NULL COMMENT '商品名称',
    product_type  VARCHAR(50)  NOT NULL COMMENT '类型: COUPON(优惠券) / PHYSICAL(实物)',
    points_price  INT          NOT NULL COMMENT '所需积分数',
    stock         INT          NOT NULL DEFAULT 0 COMMENT '库存',
    image_url     VARCHAR(500) DEFAULT NULL COMMENT '商品图片',
    description   VARCHAR(1000) DEFAULT NULL COMMENT '商品描述',
    coupon_id     BIGINT       DEFAULT NULL COMMENT '关联优惠券ID(COUPON类型)',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1上架 0下架',
    sort_order    INT          NOT NULL DEFAULT 0 COMMENT '排序(越小越靠前)',
    create_time   DATETIME     NOT NULL COMMENT '创建时间',
    create_user   BIGINT       NOT NULL DEFAULT 0 COMMENT '创建人',
    update_time   DATETIME     NOT NULL COMMENT '更新时间',
    update_user   BIGINT       NOT NULL DEFAULT 0 COMMENT '更新人'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分商城商品';

-- 5. 积分兑换记录
CREATE TABLE IF NOT EXISTS stellar_points_redemption (
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL COMMENT '用户ID',
    product_id    BIGINT       NOT NULL COMMENT '兑换商品ID',
    product_name  VARCHAR(200) NOT NULL COMMENT '商品名称(快照)',
    points_cost   INT          NOT NULL COMMENT '消耗积分数',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1已兑换 2已发放 3已取消',
    coupon_id     BIGINT       DEFAULT NULL COMMENT '发放的用户优惠券ID(COUPON类型)',
    address_id    BIGINT       DEFAULT NULL COMMENT '收货地址ID(PHYSICAL类型)',
    remark        VARCHAR(300) DEFAULT NULL COMMENT '备注',
    create_time   DATETIME     NOT NULL COMMENT '创建时间',
    INDEX idx_user (user_id),
    INDEX idx_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='积分兑换记录';

-- 6. 签到记录 (每天最多一条)
CREATE TABLE IF NOT EXISTS stellar_checkin_record (
    id             BIGINT    AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT    NOT NULL COMMENT '用户ID',
    checkin_date   DATE      NOT NULL COMMENT '签到日期',
    points_earned  INT       NOT NULL DEFAULT 0 COMMENT '获得积分',
    create_time    DATETIME  NOT NULL COMMENT '签到时间',
    UNIQUE KEY uk_user_date (user_id, checkin_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到记录';

-- ============================================================
-- 初始数据：积分规则默认配置
-- ============================================================
INSERT INTO stellar_points_rule (rule_type, rule_name, earn_points, condition_value, max_per_day, max_per_order, status, description, create_time, update_time)
VALUES
    ('ORDER',   '下单赚积分', 1, 1.00, NULL, 500, 1, '每消费1元获得1积分，每单上限500积分', NOW(), NOW()),
    ('CHECKIN', '每日签到',   5, NULL, 1,   NULL, 1, '每日签到获得5积分，每天限1次',       NOW(), NOW()),
    ('REVIEW',  '评价赚积分', 10, NULL, 3,   NULL, 1, '发表商品评价获得10积分，每天限3次',  NOW(), NOW());
