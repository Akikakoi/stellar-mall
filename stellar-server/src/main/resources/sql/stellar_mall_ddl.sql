-- ============================================================
-- 星耀商城 Stellar Mall —— 17 张表 DDL（MySQL 5.7+ / 8.x）
-- 编码：utf8mb4，排序：utf8mb4_unicode_ci（支持 emoji、生僻字）
-- 执行前请先：
--   CREATE DATABASE stellar_mall DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
--   USE stellar_mall;
--
-- 公共字段（每张表末尾的 4 个字段，由 @AutoFill AOP 自动填充）：
--   create_time      DATETIME         记录创建时间
--   create_user      BIGINT           操作人 ID（员工/用户）
--   update_time      DATETIME         最后更新时间
--   update_user      BIGINT           最后操作人 ID
-- ============================================================

-- ------------------------------------------------------------
-- 1. 分类表（商品分类 / 售后分类）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_category;
CREATE TABLE stellar_category (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    name          VARCHAR(32)   NOT NULL                COMMENT '分类名称',
    type          TINYINT       NOT NULL DEFAULT 1      COMMENT '类型：1 商品分类，2 售后分类',
    sort          INT           NOT NULL DEFAULT 0      COMMENT '排序，越大越靠前',
    status        TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 启用，0 禁用',
    create_time   DATETIME      NOT NULL                COMMENT '创建时间',
    create_user   BIGINT        NOT NULL                COMMENT '创建人',
    update_time   DATETIME      NOT NULL                COMMENT '更新时间',
    update_user   BIGINT        NOT NULL                COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name_type (name, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品/售后分类表';


-- ------------------------------------------------------------
-- 2. 员工表（商城后台：超级管理员/运营/客服/财务）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_employee;
CREATE TABLE stellar_employee (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(32)   NOT NULL                COMMENT '登录用户名',
    name          VARCHAR(32)   NOT NULL                COMMENT '姓名',
    password_hash VARCHAR(64)   NOT NULL                COMMENT '密码哈希（BCrypt）',
    phone         VARCHAR(11)   DEFAULT NULL            COMMENT '手机号',
    sex           VARCHAR(2)    DEFAULT NULL            COMMENT '性别：男/女',
    id_number     VARCHAR(18)   DEFAULT NULL            COMMENT '身份证号',
    avatar        VARCHAR(255)  DEFAULT NULL            COMMENT '头像 URL',
    status        TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 启用，0 锁定',
    role          TINYINT       NOT NULL DEFAULT 2      COMMENT '角色：1 超级管理员，2 运营，3 客服，4 财务',
    create_time   DATETIME      NOT NULL                COMMENT '创建时间',
    create_user   BIGINT        NOT NULL                COMMENT '创建人',
    update_time   DATETIME      NOT NULL                COMMENT '更新时间',
    update_user   BIGINT        NOT NULL                COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_employee_username (username),
    KEY idx_employee_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城员工表';


-- ------------------------------------------------------------
-- 3. C 端用户表（买家）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_user;
CREATE TABLE stellar_user (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    openid        VARCHAR(64)   DEFAULT NULL            COMMENT '微信 openid（真实微信登录用）',
    phone         VARCHAR(11)   DEFAULT NULL            COMMENT '手机号（快捷登录用）',
    nickname      VARCHAR(64)   DEFAULT NULL            COMMENT '昵称',
    avatar        VARCHAR(255)  DEFAULT NULL            COMMENT '头像',
    sex           VARCHAR(2)    DEFAULT NULL            COMMENT '性别',
    birthday      DATE          DEFAULT NULL            COMMENT '生日',
    status        TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 正常，0 冻结',
    register_time DATETIME      NOT NULL                COMMENT '注册时间',
    create_time   DATETIME      NOT NULL                COMMENT '创建时间',
    create_user   BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人（0=系统/用户自注册）',
    update_time   DATETIME      NOT NULL                COMMENT '更新时间',
    update_user   BIGINT        NOT NULL DEFAULT 0      COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_openid (openid),
    UNIQUE KEY uk_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='C端用户表';


-- ------------------------------------------------------------
-- 4. 收货地址簿
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_address_book;
CREATE TABLE stellar_address_book (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT        NOT NULL                COMMENT '用户 ID',
    consignee     VARCHAR(32)   NOT NULL                COMMENT '收货人',
    phone         VARCHAR(11)   NOT NULL                COMMENT '收货人手机号',
    sex           VARCHAR(2)    DEFAULT NULL            COMMENT '性别：先生/女士',
    province_code VARCHAR(16)   DEFAULT NULL            COMMENT '省编码',
    city_code     VARCHAR(16)   DEFAULT NULL            COMMENT '市编码',
    district_code VARCHAR(16)   DEFAULT NULL            COMMENT '区/县编码',
    province      VARCHAR(32)   DEFAULT NULL            COMMENT '省',
    city          VARCHAR(32)   DEFAULT NULL            COMMENT '市',
    district      VARCHAR(32)   DEFAULT NULL            COMMENT '区/县',
    detail        VARCHAR(255)  NOT NULL                COMMENT '详细地址',
    label         VARCHAR(16)   DEFAULT NULL            COMMENT '地址标签：家/公司/学校',
    is_default    TINYINT       NOT NULL DEFAULT 0      COMMENT '是否默认：1 默认，0 否',
    create_time   DATETIME      NOT NULL                COMMENT '创建时间',
    create_user   BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人（0=用户）',
    update_time   DATETIME      NOT NULL                COMMENT '更新时间',
    update_user   BIGINT        NOT NULL DEFAULT 0      COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_address_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收货地址簿';


-- ------------------------------------------------------------
-- 5. SPU 标准产品单元（如「星耀 X100Pro」）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_spu;
CREATE TABLE stellar_spu (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    name            VARCHAR(128)  NOT NULL                COMMENT 'SPU 名称',
    sub_title       VARCHAR(255)  DEFAULT NULL            COMMENT '副标题/卖点一句话',
    category_id     BIGINT        NOT NULL                COMMENT '分类 ID',
    description     MEDIUMTEXT    DEFAULT NULL            COMMENT '商品详情长文本（HTML/富文本，可选）',
    description_md  MEDIUMTEXT    DEFAULT NULL            COMMENT '商品详情 Markdown —— 主要同步给 RAG 知识库',
    main_image      VARCHAR(255)  NOT NULL DEFAULT ''     COMMENT '主图 URL',
    sub_images      VARCHAR(2000) DEFAULT NULL            COMMENT '副图 URL 列表（分号分隔）',
    sale_count      INT           NOT NULL DEFAULT 0      COMMENT '累计销量',
    comment_count   INT           NOT NULL DEFAULT 0      COMMENT '评价数',
    total_stock     INT           NOT NULL DEFAULT 0      COMMENT '总库存（所有 SKU stock 之和，展示用）',
    sku_count       INT           NOT NULL DEFAULT 0      COMMENT 'SKU 数量',
    min_price       DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '最低 SKU 价格（展示用）',
    max_price       DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '最高 SKU 价格',
    is_new          TINYINT       NOT NULL DEFAULT 0      COMMENT '是否新品 1 是 0 否',
    is_hot          TINYINT       NOT NULL DEFAULT 0      COMMENT '是否热卖 1 是 0 否',
    sort            INT           NOT NULL DEFAULT 0      COMMENT '排序值',
    status          TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 上架，0 下架',
    on_shelf_time   DATETIME      DEFAULT NULL            COMMENT '最近一次上架时间',
    off_shelf_time  DATETIME      DEFAULT NULL            COMMENT '最近一次下架时间',
    create_time     DATETIME      NOT NULL                COMMENT '创建时间',
    create_user     BIGINT        NOT NULL                COMMENT '创建人',
    update_time     DATETIME      NOT NULL                COMMENT '更新时间',
    update_user     BIGINT        NOT NULL                COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_spu_category (category_id),
    KEY idx_spu_status (status),
    KEY idx_spu_sort (sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SPU标准产品单元';


-- ------------------------------------------------------------
-- 6. SPU 规格参数表（如内存/颜色/容量）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_spu_spec;
CREATE TABLE stellar_spu_spec (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    spu_id        BIGINT        NOT NULL                COMMENT 'SPU ID',
    spec_group    VARCHAR(32)   NOT NULL                COMMENT '规格组：基本参数/屏幕/性能/存储…',
    spec_name     VARCHAR(64)   NOT NULL                COMMENT '规格项名：CPU/内存/颜色',
    spec_value    VARCHAR(255)  NOT NULL                COMMENT '规格值：骁龙 8 Gen3/8GB/钛黑',
    sort          INT           NOT NULL DEFAULT 0      COMMENT '排序',
    create_time   DATETIME      NOT NULL                COMMENT '创建时间',
    create_user   BIGINT        NOT NULL                COMMENT '创建人',
    update_time   DATETIME      NOT NULL                COMMENT '更新时间',
    update_user   BIGINT        NOT NULL                COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_spu_spec_spu (spu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SPU规格参数表';


-- ------------------------------------------------------------
-- 7. SKU 库存单元（如「星耀 X100Pro 12+256G 钛黑」）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_sku;
CREATE TABLE stellar_sku (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    spu_id        BIGINT        NOT NULL                COMMENT '所属 SPU ID',
    name          VARCHAR(255)  NOT NULL                COMMENT 'SKU 完整名（自动拼接规格，建议「SPU 名 · 规格1 · 规格2」形式）',
    specs         VARCHAR(1000) DEFAULT NULL            COMMENT '规格文本（分号分隔，例：屏幕:55寸;内存:4G+64G）— 轻量展示优先',
    specs_json    VARCHAR(1000) DEFAULT NULL            COMMENT '规格键值对 JSON：{"颜色":"钛黑","内存":"12GB","存储":"256GB"}',
    price         DECIMAL(10,2) NOT NULL                COMMENT '销售价',
    original_price DECIMAL(10,2) DEFAULT NULL            COMMENT '原价/划线价（M1 测试用）',
    cost_price    DECIMAL(10,2) DEFAULT NULL            COMMENT '成本价',
    stock         INT           NOT NULL DEFAULT 0      COMMENT '当前库存（乐观锁扣减）',
    version       INT           NOT NULL DEFAULT 0      COMMENT '乐观锁版本号：stock 扣减/回滚 WHERE id=? AND version=?',
    warn_stock    INT           NOT NULL DEFAULT 10     COMMENT '预警库存',
    weight_g      INT           DEFAULT NULL            COMMENT '重量（克）',
    barcode       VARCHAR(64)   DEFAULT NULL            COMMENT '条形码',
    image         VARCHAR(255)  DEFAULT NULL            COMMENT 'SKU 独立图',
    sort          INT           NOT NULL DEFAULT 0      COMMENT '排序',
    status        TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 在售，0 停售',
    create_time   DATETIME      NOT NULL                COMMENT '创建时间',
    create_user   BIGINT        NOT NULL                COMMENT '创建人',
    update_time   DATETIME      NOT NULL                COMMENT '更新时间',
    update_user   BIGINT        NOT NULL                COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_sku_spu (spu_id),
    KEY idx_sku_barcode (barcode),
    KEY idx_sku_version (id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='SKU库存单元';


-- ------------------------------------------------------------
-- 8. 商品评价表（C 端用户对已完成订单的 SKU 评价）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_spu_comment;
CREATE TABLE stellar_spu_comment (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    spu_id        BIGINT        NOT NULL                COMMENT 'SPU ID',
    sku_id        BIGINT        DEFAULT NULL            COMMENT 'SKU ID',
    user_id       BIGINT        NOT NULL                COMMENT '评价用户 ID',
    order_id      BIGINT        DEFAULT NULL            COMMENT '来源订单 ID',
    rating        TINYINT       NOT NULL                COMMENT '评分：1~5 星',
    content       VARCHAR(1000) NOT NULL                COMMENT '评价正文',
    images        VARCHAR(2000) DEFAULT NULL            COMMENT '晒图 JSON 数组',
    reply         VARCHAR(500)  DEFAULT NULL            COMMENT '商家回复',
    is_show       TINYINT       NOT NULL DEFAULT 1      COMMENT '是否展示：1 展示，0 隐藏',
    create_time   DATETIME      NOT NULL                COMMENT '评价时间',
    create_user   BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人',
    update_time   DATETIME      NOT NULL                COMMENT '更新时间',
    update_user   BIGINT        NOT NULL DEFAULT 0      COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_comment_spu (spu_id),
    KEY idx_comment_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品评价表';


-- ------------------------------------------------------------
-- 9. 订单主表（7 状态机：1 待付款 2 待发货 3 已发货 4 已完成 5 已取消 6 售后中 7 已退款）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_orders;
CREATE TABLE stellar_orders (
    id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id           BIGINT        NOT NULL                COMMENT '下单用户 ID',
    order_no          VARCHAR(32)   NOT NULL                COMMENT '订单号（雪花/年月日+随机）',
    status            TINYINT       NOT NULL DEFAULT 1      COMMENT '订单状态：1 待付款，2 待发货，3 已发货，4 已完成，5 已取消，6 售后中，7 已退款',
    pay_method        TINYINT       DEFAULT NULL            COMMENT '支付方式：1 微信，2 支付宝，3 余额，4 模拟支付',
    pay_status        TINYINT       NOT NULL DEFAULT 0      COMMENT '支付状态：0 未付，1 已付，2 已退款',
    pay_time          DATETIME      DEFAULT NULL            COMMENT '支付时间',
    amount            DECIMAL(10,2) NOT NULL                COMMENT '商品总金额（SKU 小计之和）',
    pay_amount        DECIMAL(10,2) NOT NULL                COMMENT '实付金额（最终支付）',
    freight_amount    DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '运费',
    discount_amount   DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '总优惠金额（含优惠券/满减）',
    coupon_id         BIGINT        DEFAULT NULL            COMMENT '使用优惠券 ID',
    pack_amount       DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '包装费/服务费',
    -- 收货地址快照（下单时快照，不随用户地址簿变化）
    address_id        BIGINT        DEFAULT NULL            COMMENT '下单时的地址簿 ID',
    consignee         VARCHAR(50)   NOT NULL DEFAULT ''     COMMENT '收货人快照',
    phone             VARCHAR(20)   NOT NULL DEFAULT ''     COMMENT '手机号快照',
    address           VARCHAR(500)  NOT NULL                COMMENT '完整地址快照：省市区+详细',
    remark            VARCHAR(255)  DEFAULT NULL            COMMENT '买家备注',
    invoice_info      VARCHAR(500)  DEFAULT NULL            COMMENT '发票信息 JSON',
    -- 取消/拒绝
    cancel_reason     VARCHAR(255)  DEFAULT NULL            COMMENT '取消原因',
    cancel_time       DATETIME      DEFAULT NULL            COMMENT '取消时间',
    rejection_reason  VARCHAR(255)  DEFAULT NULL            COMMENT '商家拒单原因（极少用，商城一般不拒）',
    -- 发货/完成
    checkout_time     DATETIME      DEFAULT NULL            COMMENT '下单时间快照（方便统计 T+N 完成率）',
    delivery_time     DATETIME      DEFAULT NULL            COMMENT '发货时间',
    delivery_company  VARCHAR(32)   DEFAULT NULL            COMMENT '快递公司：顺丰/京东/圆通…',
    tracking_no       VARCHAR(64)   DEFAULT NULL            COMMENT '快递单号',
    estimated_time    VARCHAR(32)   DEFAULT NULL            COMMENT '预计送达时间文本',
    expected_delivery DATE          DEFAULT NULL            COMMENT '预计送达日期',
    receive_time      DATETIME      DEFAULT NULL            COMMENT '用户确认收货时间（或系统自动）',
    complete_time     DATETIME      DEFAULT NULL            COMMENT '订单完成时间',
    create_time       DATETIME      NOT NULL                COMMENT '创建时间',
    create_user       BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人（0=C端用户）',
    update_time       DATETIME      NOT NULL                COMMENT '更新时间',
    update_user       BIGINT        NOT NULL DEFAULT 0      COMMENT '更新人（0=系统/定时器）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_no (order_no),
    KEY idx_orders_user (user_id),
    KEY idx_orders_status (status),
    KEY idx_orders_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单主表';


-- ------------------------------------------------------------
-- 10. 订单明细表（每个 SKU 一行，价格/规格下单时快照）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_order_detail;
CREATE TABLE stellar_order_detail (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id      BIGINT        NOT NULL                COMMENT '订单 ID',
    spu_id        BIGINT        NOT NULL                COMMENT 'SPU ID 快照',
    sku_id        BIGINT        NOT NULL                COMMENT 'SKU ID 快照',
    name          VARCHAR(255)  NOT NULL                COMMENT 'SKU 名称快照',
    sku_text      VARCHAR(255)  DEFAULT NULL            COMMENT '规格文字快照：8GB+256GB 钛黑',
    specs         VARCHAR(1000) DEFAULT NULL            COMMENT '规格 JSON 快照',
    image         VARCHAR(255)  DEFAULT NULL            COMMENT '主图快照',
    price         DECIMAL(10,2) NOT NULL                COMMENT 'SKU 销售单价快照（DECIMAL 精确）',
    quantity      INT           NOT NULL                COMMENT '购买数量',
    subtotal      DECIMAL(10,2) NOT NULL                COMMENT '小计 = price * quantity',
    create_time   DATETIME      NOT NULL                COMMENT '创建时间',
    create_user   BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人',
    update_time   DATETIME      NOT NULL                COMMENT '更新时间',
    update_user   BIGINT        NOT NULL DEFAULT 0      COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_order_detail_order (order_id),
    KEY idx_order_detail_sku (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单明细表';


-- ------------------------------------------------------------
-- 11. 支付流水表（对账/审计用）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_payment_log;
CREATE TABLE stellar_payment_log (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id      BIGINT        NOT NULL                COMMENT '订单 ID',
    pay_no        VARCHAR(64)   NOT NULL                COMMENT '商城支付流水号',
    pay_method    TINYINT       NOT NULL                COMMENT '支付方式：同订单表',
    pay_time      DATETIME      NOT NULL                COMMENT '支付时间',
    trade_no      VARCHAR(128)  DEFAULT NULL            COMMENT '第三方交易号（微信/支付宝）',
    amount        DECIMAL(10,2) NOT NULL                COMMENT '支付金额',
    status        TINYINT       NOT NULL                COMMENT '结果：1 成功，2 失败，3 已退款',
    raw_response  TEXT          DEFAULT NULL            COMMENT '第三方原始响应（脱敏存储）',
    create_time   DATETIME      NOT NULL                COMMENT '创建时间',
    create_user   BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人',
    update_time   DATETIME      NOT NULL                COMMENT '更新时间',
    update_user   BIGINT        NOT NULL DEFAULT 0      COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_log_pay_no (pay_no),
    KEY idx_payment_log_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付流水表';


-- ------------------------------------------------------------
-- 12. 售后表（退款/退货退款/换货）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_after_sale;
CREATE TABLE stellar_after_sale (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id        BIGINT        NOT NULL                COMMENT '订单 ID',
    sku_id          BIGINT        NOT NULL                COMMENT '申请售后的 SKU',
    user_id         BIGINT        NOT NULL                COMMENT '申请人用户 ID',
    type            TINYINT       NOT NULL                COMMENT '售后类型：1 仅退款，2 退货退款，3 换货',
    status          TINYINT       NOT NULL DEFAULT 1      COMMENT '售后状态：1 申请，2 商家审核中，3 用户退货中，4 退款中，5 完成，6 已拒绝，7 已取消',
    reason          VARCHAR(255)  NOT NULL                COMMENT '申请原因',
    detail          VARCHAR(1000) DEFAULT NULL            COMMENT '详细描述',
    amount          DECIMAL(10,2) NOT NULL                COMMENT '申请退款金额',
    images          VARCHAR(2000) DEFAULT NULL            COMMENT '凭证图片 JSON',
    audit_user_id   BIGINT        DEFAULT NULL            COMMENT '审核人员工 ID',
    audit_remark    VARCHAR(255)  DEFAULT NULL            COMMENT '审核备注',
    audit_time      DATETIME      DEFAULT NULL            COMMENT '审核时间',
    return_tracking VARCHAR(128) DEFAULT NULL            COMMENT '退货快递单号',
    refund_no       VARCHAR(128) DEFAULT NULL            COMMENT '第三方退款流水号',
    refund_time     DATETIME      DEFAULT NULL            COMMENT '退款完成时间',
    exchange_sku_id BIGINT        DEFAULT NULL            COMMENT '换货目标 SKU（换货专用）',
    create_time     DATETIME      NOT NULL                COMMENT '创建时间',
    create_user     BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人',
    update_time     DATETIME      NOT NULL                COMMENT '更新时间',
    update_user     BIGINT        NOT NULL DEFAULT 0      COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_after_sale_order (order_id),
    KEY idx_after_sale_user (user_id),
    KEY idx_after_sale_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后申请表';


-- ------------------------------------------------------------
-- 13. 优惠券模板表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_coupon;
CREATE TABLE stellar_coupon (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    name            VARCHAR(100)  NOT NULL                COMMENT '优惠券名称',
    type            TINYINT       NOT NULL DEFAULT 1      COMMENT '类型：1 满减券，2 折扣券',
    condition_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00  COMMENT '使用门槛：满多少可用（满减）',
    discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '满减金额 / 折扣比例（0.85=85折）',
    total_count     INT           NOT NULL                COMMENT '总发放数量',
    received_count  INT           NOT NULL DEFAULT 0      COMMENT '已领取数量',
    used_count      INT           NOT NULL DEFAULT 0      COMMENT '已使用数量',
    per_user_limit  INT           NOT NULL DEFAULT 1      COMMENT '每人限领张数',
    start_time      DATETIME      NOT NULL                COMMENT '领取开始时间',
    end_time        DATETIME      NOT NULL                COMMENT '领取结束时间',
    status          TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 启用，0 停用',
    create_time     DATETIME      NOT NULL                COMMENT '创建时间',
    create_user     BIGINT        NOT NULL                COMMENT '创建人',
    update_time     DATETIME      NOT NULL                COMMENT '更新时间',
    update_user     BIGINT        NOT NULL                COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_coupon_status (status, start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板表';


-- ------------------------------------------------------------
-- 14. 用户领券记录表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_user_coupon;
CREATE TABLE stellar_user_coupon (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT        NOT NULL                COMMENT '用户 ID',
    coupon_id     BIGINT        NOT NULL                COMMENT '券模板 ID',
    status        TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1 未使用，2 已使用，3 已过期',
    order_id      BIGINT        DEFAULT NULL            COMMENT '使用的订单 ID',
    used_time     DATETIME      DEFAULT NULL            COMMENT '使用时间',
    create_time   DATETIME      NOT NULL                COMMENT '创建时间',
    create_user   BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人',
    update_time   DATETIME      NOT NULL                COMMENT '更新时间',
    update_user   BIGINT        NOT NULL DEFAULT 0      COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_user_coupon_user (user_id),
    KEY idx_user_coupon_coupon (coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户领券记录';


-- ------------------------------------------------------------
-- 15. 商城全局配置表（单行单例：营业状态/公告/包邮门槛/运费）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_shop_settings;
CREATE TABLE stellar_shop_settings (
    id                      BIGINT        NOT NULL DEFAULT 1  COMMENT '主键（单例，永远=1）',
    shop_status             TINYINT       NOT NULL DEFAULT 1  COMMENT '营业状态：1 营业中，0 打烊/暂停',
    notice                  VARCHAR(500)  DEFAULT NULL        COMMENT '商城顶部公告',
    customer_service_phone  VARCHAR(20)   DEFAULT NULL        COMMENT '客服电话',
    customer_service_hours  VARCHAR(64)   DEFAULT NULL        COMMENT '客服时间：9:00-21:00',
    shipping_free_amount    DECIMAL(10,2) NOT NULL DEFAULT 99.00 COMMENT '满多少包邮（元）',
    default_freight         DECIMAL(10,2) NOT NULL DEFAULT 8.00  COMMENT '默认运费（元）',
    business_hours          VARCHAR(500)  DEFAULT NULL        COMMENT '每日营业时间 JSON：[{"open":"09:00","close":"22:00"}]',
    return_policy_md        MEDIUMTEXT    DEFAULT NULL        COMMENT '售后政策 Markdown（同步给 RAG 知识库）',
    shipping_policy_md      MEDIUMTEXT    DEFAULT NULL        COMMENT '配送政策 Markdown',
    privacy_policy_md       MEDIUMTEXT    DEFAULT NULL        COMMENT '隐私政策 Markdown',
    tos_md                  MEDIUMTEXT    DEFAULT NULL        COMMENT '用户服务条款 Markdown',
    create_time             DATETIME      NOT NULL            COMMENT '创建时间',
    create_user             BIGINT        NOT NULL DEFAULT 0  COMMENT '创建人',
    update_time             DATETIME      NOT NULL            COMMENT '更新时间',
    update_user             BIGINT        NOT NULL DEFAULT 0  COMMENT '更新人',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城全局配置（单例）';


-- ------------------------------------------------------------
-- 16. 购物车表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_shopping_cart;
CREATE TABLE stellar_shopping_cart (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT        NOT NULL                COMMENT '用户 ID',
    spu_id        BIGINT        NOT NULL                COMMENT 'SPU ID',
    sku_id        BIGINT        NOT NULL                COMMENT 'SKU ID',
    quantity      INT           NOT NULL DEFAULT 1      COMMENT '数量',
    selected      TINYINT       NOT NULL DEFAULT 1      COMMENT '是否勾选：1 选中（参与结算），0 未选',
    create_time   DATETIME      NOT NULL                COMMENT '创建时间',
    create_user   BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人',
    update_time   DATETIME      NOT NULL                COMMENT '更新时间',
    update_user   BIGINT        NOT NULL DEFAULT 0      COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_user_sku (user_id, sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车';


-- ------------------------------------------------------------
-- 17. Mall → RAG 知识库同步日志表（极其重要：保证知识一致性）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_rag_sync_log;
CREATE TABLE stellar_rag_sync_log (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    sync_type     VARCHAR(16)   NOT NULL                COMMENT '同步类型：spu/policy/coupon/category/delete_tag',
    ref_id        VARCHAR(64)   NOT NULL                COMMENT '关联业务 ID：spu_id/coupon_id/tagName',
    title         VARCHAR(255)  NOT NULL                COMMENT '同步标题（调试用）',
    status        TINYINT       NOT NULL DEFAULT 3      COMMENT '状态：1 成功，2 失败，3 重试中',
    attempt       INT           NOT NULL DEFAULT 0      COMMENT '已重试次数（0=首次）',
    max_attempt   INT           NOT NULL DEFAULT 3      COMMENT '最大重试次数',
    request_body  MEDIUMTEXT    DEFAULT NULL            COMMENT '请求 RAG 接口的 Body JSON（脱敏）',
    response_body TEXT          DEFAULT NULL            COMMENT 'RAG 返回结果（失败时存错误信息）',
    error_msg     VARCHAR(1000) DEFAULT NULL            COMMENT '错误摘要',
    next_retry_at DATETIME      DEFAULT NULL            COMMENT '下次重试时间（指数退避）',
    last_try_at   DATETIME      DEFAULT NULL            COMMENT '最后尝试时间',
    create_time   DATETIME      NOT NULL                COMMENT '创建时间',
    create_user   BIGINT        NOT NULL DEFAULT 0      COMMENT '创建人',
    update_time   DATETIME      NOT NULL                COMMENT '更新时间',
    update_user   BIGINT        NOT NULL DEFAULT 0      COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_rag_sync_status (status, next_retry_at),
    KEY idx_rag_sync_type_ref (sync_type, ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Mall→RAG知识库同步日志表';


-- ------------------------------------------------------------
-- 18. Mall → RAG 同步发件箱 outbox（事务性发件箱模式）
--     业务写 SPU 和写 outbox 放在同一本地事务里，保证业务变更与同步意图的一致性。
--     后台定时任务批量拉取 synced=0 且 failed=0 的记录推送给 RAG 接口。
-- ------------------------------------------------------------
DROP TABLE IF EXISTS stellar_rag_sync_outbox;
CREATE TABLE stellar_rag_sync_outbox (
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_type       VARCHAR(16)   NOT NULL                COMMENT '业务类型：SPU / POLICY / COUPON / CATEGORY / DOC',
    biz_id         BIGINT        NOT NULL                COMMENT '业务主键 id（spu_id / coupon_id / ...）',
    op_type        VARCHAR(32)   NOT NULL DEFAULT 'SAVE' COMMENT '操作：SAVE/UPDATE/ONSHELF/OFFSHELF/DELETE/SYNC_POLICY…',
    synced         TINYINT       NOT NULL DEFAULT 0      COMMENT '是否已同步：1 是 / 0 否',
    failed         TINYINT       NOT NULL DEFAULT 0      COMMENT '是否已达最大重试次数：1 是 / 0 否',
    retry_count    INT           NOT NULL DEFAULT 0      COMMENT '已尝试次数（成功也会记 1，失败每次 +1）',
    max_attempt    INT           NOT NULL DEFAULT 3      COMMENT '最大尝试次数，默认 3',
    last_try_time  DATETIME      DEFAULT NULL            COMMENT '最后一次尝试时间',
    last_error_msg VARCHAR(1000) DEFAULT NULL            COMMENT '最后一次失败摘要',
    payload_json   MEDIUMTEXT    DEFAULT NULL            COMMENT '请求体快照（可空，便于手工重放）',
    create_time    DATETIME      NOT NULL                COMMENT '创建时间',
    create_user    BIGINT        NOT NULL                COMMENT '创建人',
    update_time    DATETIME      NOT NULL                COMMENT '更新时间',
    update_user    BIGINT        NOT NULL                COMMENT '更新人',
    PRIMARY KEY (id),
    KEY idx_outbox_status (synced, failed, last_try_time),
    KEY idx_outbox_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG同步发件箱（事务性发件箱模式）';


-- ============================================================
-- 种子数据（P0 最小化：1 个超级管理员 + 1 条商城配置 + 6 分类）
-- ============================================================

-- 员工：超级管理员 admin / 123456
--   ⚠️ 初始密码统一为 123456（BCrypt $2b$10 哈希，Java Spring Security BCryptPasswordEncoder 完全兼容）
--   生成命令：python -c "import bcrypt; print(bcrypt.hashpw(b'123456', bcrypt.gensalt(10)).decode())"
INSERT INTO stellar_employee (id, username, name, password_hash, phone, sex, id_number, avatar, status, role,
                              create_time, create_user, update_time, update_user) VALUES
(1, 'admin', '超级管理员',
 '$2b$10$bey3bY4MddvOKfjUBRxxtuAixcohMgc9h0dsDvPXZSB7cy10/OfVK',
 '13800000000', '男', '110101199001010000', NULL, 1, 1,
 NOW(), 0, NOW(), 0);

-- 商城配置（单例 id=1）
INSERT INTO stellar_shop_settings (id, shop_status, notice, customer_service_phone, customer_service_hours,
                                   shipping_free_amount, default_freight,
                                   return_policy_md, shipping_policy_md, privacy_policy_md, tos_md,
                                   create_time, create_user, update_time, update_user) VALUES
(1, 1, '🎉 【新用户专享】注册立领 99-10 券，全场满 99 元包邮！',
 '400-800-1234', '9:00-21:00 全年无休',
 99.00, 8.00,
 '# 星耀商城 售后服务政策\n\n1. 7 天无理由退换（未拆封）…',
 '# 星耀商城 配送政策\n\n满 99 元包邮，默认顺丰速运…',
 '# 星耀商城 隐私政策\n\n我们郑重承诺不收集非必要用户信息…',
 '# 星耀商城 用户服务条款\n\n1. 用户注册后即视为同意本条款…',
 NOW(), 0, NOW(), 0);

-- 6 个精简分类（对应 6 个 SPU 大类）
INSERT INTO stellar_category (id, name, type, sort, status, create_time, create_user, update_time, update_user) VALUES
(1, '智能手机',   1, 1, 1, NOW(), 1, NOW(), 1),
(2, '家用电冰箱', 1, 2, 1, NOW(), 1, NOW(), 1),
(3, '家用空调',   1, 3, 1, NOW(), 1, NOW(), 1),
(4, '笔记本电脑', 1, 4, 1, NOW(), 1, NOW(), 1),
(5, '平板电视',   1, 5, 1, NOW(), 1, NOW(), 1),
(6, '智能影音',   1, 6, 1, NOW(), 1, NOW(), 1),
(7, '售后分类-退款', 2, 99, 1, NOW(), 1, NOW(), 1);

-- ============================================================
-- 6 SPU 精简版样例数据 + 对应 1-2 个 SKU（M1 精简版够用）
-- ============================================================
INSERT INTO stellar_spu (id, name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES
(1, '星耀 X100 Pro 旗舰手机', '骁龙 8 至尊版 · 徕卡三摄 · IP68',
    1,
    '# 星耀 X100 Pro\n\n## 核心性能\n- 骁龙 8 至尊版 4nm 工艺\n- 12GB LPDDR5X + 1TB UFS 4.1\n## 影像系统\n- 徕卡主摄 1 英寸 IMX989\n- 3.5X 潜望长焦 OIS\n## 续航\n- 5600mAh 硅碳负极电池 · 120W 有线 + 50W 无线',
    'https://cdn.example.com/spu-1-main.jpg', NULL,
    100, 1, NOW(), 3999.00, 4999.00, 150, 2, 1, 1,
    NOW(), 1, NOW(), 1),
(2, '极净 516L 法式多门冰箱', '一级能效 · 零度保鲜 · 母婴舱',
    2,
    '# 极净 516L 法式冰箱\n\n- **容量**：516L（冷藏 348L / 冷冻 168L）\n- **能效**：新国标一级，日耗电 0.88 度\n- **核心功能**：零度保鲜舱 · 银离子净味 · 双变频压缩机\n- **静音**：35dB 图书馆级静音',
    'https://cdn.example.com/spu-2-main.jpg', NULL,
    90, 1, NOW(), 4599.00, 5199.00, 80, 2, 0, 1,
    NOW(), 1, NOW(), 1),
(3, '御风 3 匹一级变频柜机空调', '新一级 · 急速冷暖 · 自清洁',
    3,
    '# 御风 3 匹柜机\n\n- **制冷量**：7200W，适用 30-45㎡\n- **能效**：APF 4.42，新国标一级\n- **黑科技**：0.5℃ 精准控温 · 57℃ 高温除菌自清洁 · 智能除湿\n- **噪音**：低至 22dB 睡眠模式',
    'https://cdn.example.com/spu-3-main.jpg', NULL,
    85, 1, NOW(), 5299.00, 5999.00, 40, 1, 0, 0,
    NOW(), 1, NOW(), 1),
(4, '清逸 AirBook 14 轻薄本', '2.8K OLED · 1.1kg · 18h 续航',
    4,
    '# 清逸 AirBook 14\n\n- **屏幕**：14 寸 2.8K 120Hz OLED 100% DCI-P3\n- **平台**：Intel Core Ultra 7 258H · 32GB LPDDR5X · 2TB PCIe 4.0\n- **重量/厚度**：1.1kg · 13.9mm\n- **续航**：75Wh 电池，本地视频 18 小时\n- **接口**：双 Thunderbolt 4 · USB-A 3.2 · HDMI 2.1 · SD 读卡器',
    'https://cdn.example.com/spu-4-main.jpg', NULL,
    95, 1, NOW(), 7299.00, 8799.00, 60, 2, 1, 1,
    NOW(), 1, NOW(), 1),
(5, '逸彩 65 寸 QD-MiniLED 电视', '量子点 · 2000nits · 全阵列 2000 分区',
    5,
    '# 逸彩 65Q80 MiniLED\n\n## 画质\n- 65 寸 QD-MiniLED，量子点广色域 99% DCI-P3\n- 峰值亮度 2000nits，HDR10+ / Dolby Vision\n- 全阵列 2000 分区背光，暗部细节拉满\n## 音质\n- 8 单元 60W 音响 · Dolby Atmos 全景声\n## 智能\n- 星耀 SmartHub 8 核芯片 · 远场语音 3.0 · HDMI 2.1 4K144Hz 游戏模式',
    'https://cdn.example.com/spu-5-main.jpg', NULL,
    92, 1, NOW(), 4299.00, 4999.00, 100, 2, 1, 1,
    NOW(), 1, NOW(), 1),
(6, '逸彩 VividBar 5.1.2 回音壁套装', '杜比全景声 · DTS:X · 无线低音炮',
    6,
    '# 逸彩 VividBar 5.1.2 回音壁\n\n- **声道布局**：5.1.2（天空反射声道 × 2）\n- **解码**：Dolby Atmos · DTS:X · eARC 回传\n- **配置**：回音壁主体 + 8 寸 120W 无线低音炮 + 2 只后环绕\n- **总功率**：620W 峰值\n- **连接**：HDMI 2.1 eARC · 光纤 · 蓝牙 5.3 · Wi-Fi 无线播流',
    'https://cdn.example.com/spu-6-main.jpg', NULL,
    88, 1, NOW(), 2599.00, 2599.00, 200, 1, 0, 0,
    NOW(), 1, NOW(), 1);

INSERT INTO stellar_sku (id, spu_id, name, specs, price, original_price, stock, version, sort, status,
                         create_time, create_user, update_time, update_user) VALUES
-- SPU 1 手机
(1, 1, '星耀 X100 Pro · 12+256GB', '内存:12GB;存储:256GB', 3999.00, 4499.00, 100, 0, 1, 1, NOW(), 1, NOW(), 1),
(2, 1, '星耀 X100 Pro · 16+1TB',   '内存:16GB;存储:1TB',    4999.00, 5499.00, 50,  0, 2, 1, NOW(), 1, NOW(), 1),
-- SPU 2 冰箱
(3, 2, '极净 516L 法式冰箱 · 莫兰迪灰',  '颜色:莫兰迪灰', 4599.00, 4999.00, 50, 0, 1, 1, NOW(), 1, NOW(), 1),
(4, 2, '极净 516L 法式冰箱 · 珍珠白',    '颜色:珍珠白',   5199.00, 5599.00, 30, 0, 2, 1, NOW(), 1, NOW(), 1),
-- SPU 3 空调
(5, 3, '御风 3 匹变频柜机 · 星空灰', '颜色:星空灰;能效:一级', 5299.00, 5999.00, 40, 0, 1, 1, NOW(), 1, NOW(), 1),
-- SPU 4 笔记本
(6, 4, '清逸 AirBook 14 · Ultra7 32G 1T',  'CPU:Ultra7 258H;内存:32G;存储:1T',  7299.00, 7999.00, 40, 0, 1, 1, NOW(), 1, NOW(), 1),
(7, 4, '清逸 AirBook 14 · Ultra7 32G 2T',  'CPU:Ultra7 258H;内存:32G;存储:2T',  8799.00, 9599.00, 20, 0, 2, 1, NOW(), 1, NOW(), 1),
-- SPU 5 电视
(8, 5, '逸彩 65Q80 MiniLED · 标准版',   '型号:标准版;挂架:无',  4299.00, 4799.00, 70, 0, 1, 1, NOW(), 1, NOW(), 1),
(9, 5, '逸彩 65Q80 MiniLED · 挂架套装', '型号:标准版;挂架:含原装挂架', 4599.00, 5099.00, 30, 0, 2, 1, NOW(), 1, NOW(), 1),
-- SPU 6 音响
(10, 6, '逸彩 VividBar 5.1.2 回音壁套装', '配置:标配', 2599.00, 2999.00, 200, 0, 1, 1, NOW(), 1, NOW(), 1);


-- ============================================================
-- 20. MallUser 表：C 端用户（和原 stellar_user 并存，简化版只保 phone+password 登录用）
-- ============================================================
DROP TABLE IF EXISTS stellar_mall_user;
CREATE TABLE stellar_mall_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    phone         VARCHAR(11)  NOT NULL               COMMENT '手机号=登录账号',
    nickname      VARCHAR(64)  DEFAULT NULL           COMMENT '昵称',
    password      VARCHAR(255) NOT NULL               COMMENT 'BCrypt 密码哈希（和 stellar_employee.password_hash 同算法）',
    status        TINYINT      NOT NULL DEFAULT 1     COMMENT '1 正常，0 冻结',
    create_time   DATETIME     NOT NULL               COMMENT '创建时间',
    create_user   BIGINT       NOT NULL DEFAULT 0     COMMENT '创建人，0=自注册',
    update_time   DATETIME     NOT NULL               COMMENT '更新时间',
    update_user   BIGINT       NOT NULL DEFAULT 0     COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='C端用户表（简化）';

-- 种子用户：13900000000 / 123456（BCrypt）+ 2 个备用 13800000001/13800000002
INSERT INTO stellar_mall_user (id, phone, nickname, password, status, create_time, create_user, update_time, update_user) VALUES
(1, '13900000000', '测试用户',   '$2b$10$bey3bY4MddvOKfjUBRxxtuAixcohMgc9h0dsDvPXZSB7cy10/OfVK', 1, NOW(), 0, NOW(), 0),
(2, '13800000001', '测试用户一', '$2b$10$bey3bY4MddvOKfjUBRxxtuAixcohMgc9h0dsDvPXZSB7cy10/OfVK', 1, NOW(), 0, NOW(), 0),
(3, '13800000002', '测试用户二', '$2b$10$bey3bY4MddvOKfjUBRxxtuAixcohMgc9h0dsDvPXZSB7cy10/OfVK', 1, NOW(), 0, NOW(), 0);


-- ============================================================
-- 21. stellar_cart：购物车（和原 stellar_shopping_cart 并存，简化版）
-- ============================================================
DROP TABLE IF EXISTS stellar_cart;
CREATE TABLE stellar_cart (
    id            BIGINT    NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT    NOT NULL               COMMENT '用户 ID',
    spu_id        BIGINT    NOT NULL               COMMENT 'SPU ID',
    sku_id        BIGINT    NOT NULL               COMMENT 'SKU ID',
    qty           INT       NOT NULL DEFAULT 1     COMMENT '数量',
    checked       TINYINT   NOT NULL DEFAULT 1     COMMENT '1 已勾选=参与结算，0 未勾选',
    create_time   DATETIME  NOT NULL               COMMENT '创建时间',
    create_user   BIGINT    NOT NULL DEFAULT 0     COMMENT '创建人',
    update_time   DATETIME  NOT NULL               COMMENT '更新时间',
    update_user   BIGINT    NOT NULL DEFAULT 0     COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_user_sku (user_id, sku_id),
    KEY idx_cart_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车（简化）';


-- ============================================================
-- 22. stellar_mall_order：商城订单（和原 stellar_orders 并存，简化版）
-- ============================================================
DROP TABLE IF EXISTS stellar_mall_order;
CREATE TABLE stellar_mall_order (
    id            BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no      VARCHAR(32)    NOT NULL               COMMENT '订单号',
    user_id       BIGINT         NOT NULL               COMMENT '用户 ID',
    total_amount  DECIMAL(10,2)  NOT NULL               COMMENT '商品总金额',
    pay_amount    DECIMAL(10,2)  NOT NULL               COMMENT '实付金额',
    status        VARCHAR(16)    NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待付款 / PAID 已付款 / COMPLETED 已完成 / CANCELLED 已取消',
    address       VARCHAR(500)   DEFAULT NULL           COMMENT '收货地址快照',
    pay_method    TINYINT      DEFAULT NULL           COMMENT '支付方式：1 微信 2 支付宝 3 模拟支付 4 银行卡',
    remark        VARCHAR(255)   DEFAULT NULL           COMMENT '买家备注',
    tracking_no   VARCHAR(64)    DEFAULT NULL           COMMENT '快递单号',
    delivery_company VARCHAR(32) DEFAULT NULL           COMMENT '快递公司',
    delivery_time DATETIME       DEFAULT NULL           COMMENT '发货时间',
    create_time   DATETIME       NOT NULL               COMMENT '创建时间',
    create_user   BIGINT         NOT NULL DEFAULT 0     COMMENT '创建人',
    update_time   DATETIME       NOT NULL               COMMENT '更新时间',
    update_user   BIGINT         NOT NULL DEFAULT 0     COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_mall_order_no (order_no),
    KEY idx_mall_order_user (user_id),
    KEY idx_mall_order_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城订单（简化）';


-- ============================================================
-- 23. stellar_favorite：用户收藏夹
-- ============================================================
DROP TABLE IF EXISTS stellar_favorite;
CREATE TABLE stellar_favorite (
    id            BIGINT    NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id       BIGINT    NOT NULL               COMMENT '用户 ID',
    spu_id        BIGINT    NOT NULL               COMMENT 'SPU ID',
    create_time   DATETIME  NOT NULL               COMMENT '创建时间',
    create_user   BIGINT    NOT NULL DEFAULT 0     COMMENT '创建人',
    update_time   DATETIME  NOT NULL               COMMENT '更新时间',
    update_user   BIGINT    NOT NULL DEFAULT 0     COMMENT '更新人',
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorite_user_spu (user_id, spu_id),
    KEY idx_favorite_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏夹';


-- ============================================================
-- 24. stellar_mall_order_item：商城订单明细（和原 stellar_order_detail 并存，简化版）
-- ============================================================
DROP TABLE IF EXISTS stellar_mall_order_item;
CREATE TABLE stellar_mall_order_item (
    id         BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id   BIGINT         NOT NULL               COMMENT '订单 ID',
    spu_id     BIGINT         NOT NULL               COMMENT 'SPU ID 快照',
    sku_id     BIGINT         NOT NULL               COMMENT 'SKU ID 快照',
    spu_name   VARCHAR(255)   NOT NULL               COMMENT 'SPU 名称快照',
    sku_specs  VARCHAR(500)   DEFAULT NULL           COMMENT 'SKU 规格文本快照',
    price      DECIMAL(10,2)  NOT NULL               COMMENT '单价快照',
    qty        INT            NOT NULL               COMMENT '数量快照',
    subtotal   DECIMAL(10,2)  NOT NULL               COMMENT '小计快照 = price * qty',
    extra_amount DECIMAL(12,2) DEFAULT NULL           COMMENT '额外费用（保障服务等），单位元',
    PRIMARY KEY (id),
    KEY idx_mall_order_item_order (order_id),
    KEY idx_mall_order_item_sku (sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商城订单明细（简化）';


-- ============================================================
-- 25. stellar_user_message：用户消息通知
-- ============================================================
DROP TABLE IF EXISTS stellar_user_message;
CREATE TABLE stellar_user_message (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL               COMMENT '用户 ID',
    type        VARCHAR(32)  NOT NULL               COMMENT '类型：ORDER_SHIPPED / ORDER_CANCELLED / SYSTEM / COUPON',
    title       VARCHAR(200) NOT NULL               COMMENT '消息标题',
    content     VARCHAR(1000) DEFAULT ''            COMMENT '消息正文',
    ref_id      BIGINT       DEFAULT NULL           COMMENT '关联业务 ID（如订单 ID）',
    is_read     TINYINT      NOT NULL DEFAULT 0     COMMENT '0 未读 / 1 已读',
    create_time DATETIME     NOT NULL               COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_msg_user (user_id),
    KEY idx_user_msg_unread (user_id, is_read)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户消息通知';


-- ============================================================
-- 26. stellar_user_behavior：用户行为埋点日志（只追加，供推荐/搜索评测/风控）
--    对应 migration：V18__user_behavior.sql
-- ============================================================
DROP TABLE IF EXISTS stellar_user_behavior;
CREATE TABLE stellar_user_behavior (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT          DEFAULT NULL        COMMENT '登录用户 ID（游客为 NULL）',
    device_id   VARCHAR(64)     NOT NULL DEFAULT '' COMMENT '前端匿名设备 ID（localStorage 生成）',
    event_type  VARCHAR(32)     NOT NULL            COMMENT '事件类型：view_item_list/view_item/search/add_to_cart/order_placed/favorite/page_view',
    spu_id      BIGINT          DEFAULT NULL        COMMENT '关联 SPU ID',
    sku_id      BIGINT          DEFAULT NULL        COMMENT '关联 SKU ID',
    category_id BIGINT          DEFAULT NULL        COMMENT '关联分类 ID',
    keyword     VARCHAR(100)    DEFAULT NULL        COMMENT '搜索词（search 事件）',
    scene       VARCHAR(32)     DEFAULT NULL        COMMENT '来源场景：home/search/category/detail/cart/order/favorites',
    position    INT             DEFAULT NULL        COMMENT '列表位次（从 1 开始，view_item_list 用）',
    amount      DECIMAL(10,2)   DEFAULT NULL        COMMENT '金额（商品价/下单金额）',
    duration_ms INT             DEFAULT NULL        COMMENT '停留时长（view_item 离开时补报）',
    extra       VARCHAR(500)    DEFAULT NULL        COMMENT '扩展 JSON（排序方式/分页等）',
    client_ip   VARCHAR(45)     DEFAULT NULL        COMMENT '客户端 IP（后端记录）',
    user_agent  VARCHAR(255)    DEFAULT NULL        COMMENT 'User-Agent（后端记录）',
    event_time  DATETIME        NOT NULL            COMMENT '前端事件发生时间',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    PRIMARY KEY (id),
    KEY idx_behavior_user_time (user_id, event_time),
    KEY idx_behavior_dev_time (device_id, event_time),
    KEY idx_behavior_type_time (event_type, event_time),
    KEY idx_behavior_spu (spu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户行为埋点日志（只追加）';
