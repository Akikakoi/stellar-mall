-- ============================================================
-- V23: 商品浏览历史（用户足迹）表
-- ------------------------------------------------------------
-- 背景：
--   为用户提供"商品浏览历史"（足迹）功能。有别于 V18 的行为埋点
--   （只追加、用户不可见、不可删），本表是用户可见、可管理的数据：
--     1. 仅登录用户产生记录（C 端受保护接口写入，BaseContext 取 userId）。
--     2. (user_id, spu_id) 唯一：同一商品重复浏览只刷新 browse_time，
--        足迹去重不膨胀。
--     3. 已下架/已删除商品记录保留（页面前端据 status 提示"已下架"）。
--     4. 单用户保留最大条数不在此限制（由 Service 层在写入时淘汰最旧，
--        默认 maxKeep=100，见 BrowseHistoryConstants）。
-- 查询场景：按用户浏览时间倒序分页，故建 (user_id, browse_time) 索引。
-- ============================================================

CREATE TABLE IF NOT EXISTS stellar_browse_history (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT          NOT NULL                COMMENT '登录用户 ID',
    spu_id       BIGINT          NOT NULL                COMMENT '商品 SPU ID',
    sku_id       BIGINT          DEFAULT NULL            COMMENT '浏览时的 SKU ID（可选）',
    browse_time  DATETIME        NOT NULL                COMMENT '最近浏览时间',
    create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次浏览时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_browse_user_spu (user_id, spu_id),
    KEY idx_browse_user_time (user_id, browse_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品浏览历史（用户足迹）';