-- ============================================
-- 评价评论表（用户对评价的回复/评论）
-- 日期: 2026-07-24
-- ============================================

CREATE TABLE IF NOT EXISTS stellar_review_comment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    review_id BIGINT NOT NULL COMMENT '评价ID',
    user_id BIGINT NOT NULL COMMENT '评论用户ID',
    content VARCHAR(500) NOT NULL COMMENT '评论内容',
    status TINYINT DEFAULT 1 COMMENT '1=显示 0=隐藏',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    create_user BIGINT DEFAULT 0,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_user BIGINT DEFAULT 0,
    INDEX idx_review_id (review_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价评论';
