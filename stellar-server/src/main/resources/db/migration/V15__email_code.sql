-- ============================================================
-- 邮箱验证码改造：将短信验证码表改为邮箱验证码表
-- ============================================================

-- 1. 重命名表
RENAME TABLE stellar_sms_code TO stellar_email_code;

-- 2. 重命名 phone 列为 email
ALTER TABLE stellar_email_code CHANGE COLUMN phone email VARCHAR(100) NOT NULL COMMENT '邮箱';

-- 3. 重建索引
ALTER TABLE stellar_email_code DROP INDEX idx_phone_type;
ALTER TABLE stellar_email_code ADD INDEX idx_email_type (email, type);