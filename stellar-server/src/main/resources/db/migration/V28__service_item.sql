-- 保障服务后端化（按单个商品配置服务组合）
-- 1) 服务字典表：管理端维护服务名称/价格/状态；code 为稳定业务标识（前端 serviceInfo JSON 快照中的 id）
CREATE TABLE stellar_service_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL COMMENT '业务标识（快照 id，稳定不变）',
    title VARCHAR(128) NOT NULL COMMENT '展示名称，如：碎屏险 · 1年',
    price DECIMAL(10, 2) NOT NULL DEFAULT 0 COMMENT '单价（元/件，随商品数量倍乘）',
    description VARCHAR(255) DEFAULT NULL COMMENT '说明（选填）',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    UNIQUE KEY uk_service_code (code)
) COMMENT '保障服务字典';

-- 2) SPU-服务 关联表：某商品可选购哪些保障服务（按单个商品配置）
CREATE TABLE stellar_spu_service (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    spu_id BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    UNIQUE KEY uk_spu_service (spu_id, service_id)
) COMMENT '商品-保障服务关联';

-- 3) 种子数据：与原前端写死的三项服务保持同 code 同价格，存量购物车/订单快照兼容
INSERT INTO stellar_service_item (code, title, price, description, status, create_time, update_time) VALUES
('screen_insurance',  '碎屏险 · 1年',   99.00, '屏幕意外损坏免费维修/换新', 1, NOW(), NOW()),
('extend_warranty',   '延长保修 · 2年', 159.00, '整机保修延长 2 年',         1, NOW(), NOW()),
('battery_insurance', '电池换新险',     49.00, '电池健康度低于 80% 免费换新', 1, NOW(), NOW());

-- 4) 存量 SPU 预置关联（镜像原前端 SERVICE_CATEGORIES 映射）：
--    延长保修 → 全部分类；碎屏险/电池换新险 → 智能手机(1)/笔记本电脑(4)/智能穿戴(8)/平板电脑(10)
INSERT INTO stellar_spu_service (spu_id, service_id)
SELECT s.id, si.id FROM stellar_spu s
JOIN stellar_service_item si ON si.code = 'extend_warranty';

INSERT INTO stellar_spu_service (spu_id, service_id)
SELECT s.id, si.id FROM stellar_spu s
JOIN stellar_service_item si ON si.code IN ('screen_insurance', 'battery_insurance')
WHERE s.category_id IN (1, 4, 8, 10);
