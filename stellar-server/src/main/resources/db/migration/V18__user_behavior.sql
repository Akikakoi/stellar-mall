-- ============================================================
-- V18: 用户行为埋点表（供推荐/搜索优化/风控使用的只追加日志）
-- ------------------------------------------------------------
-- 背景：
--   商城此前没有任何用户行为日志（浏览/搜索/加购/下单/收藏全部无痕），
--   深度学习要落地的推荐、搜索相关性评估、异常检测都拿不到训练与评测数据。
--   本表记录 C 端用户路径上的关键行为，作为后续模型的数据底座。
--
-- 设计：
--   1. 只追加、不更新不删除；表结构宽泛（JSON extra 兜底扩展），
--      事件类型用 VARCHAR 而非枚举——埋点协议演进不需要 DDL 变更。
--   2. user_id 允许 NULL：游客浏览同样埋点，device_id 负责游客归因；
--      登录用户由后端从 JWT 解析补齐（接口放行 + 可选鉴权）。
--   3. 主要消费场景查询：按用户/设备的时间序列、按事件类型聚合、
--      按 spu 聚合（商品热度），故建三组复合索引。
--   4. 写入频率高，禁止走业务事务，由异步线程池批量落库；
--      单条失败绝不影响主流程（积分/订单链路均不受埋点拖累）。
-- ============================================================

CREATE TABLE IF NOT EXISTS stellar_user_behavior (
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
