-- =========================================================================
-- 星耀商城 stellar-mall —— MySQL 账号与授权（2 个账号，密码都为 123456）
-- 执行顺序：用 root 账号登录后，直接 source 本文件即可
-- MySQL 版本：5.7+ / 8.x
-- =========================================================================

-- 1. 创建数据库（如果没创建）
CREATE DATABASE IF NOT EXISTS stellar_mall
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE stellar_mall;

-- -------------------------------------------------------------------------
-- 2. 主账号 stellar / 123456 —— 给 Java Mall 后端用（DML + DDL 全权限）
--    ⚠️ 生产环境请务必改成强随机密码！
-- -------------------------------------------------------------------------
-- MySQL 8.x 里，CREATE USER 前如果用户已存在会报错 —— 我们用 DROP IF EXISTS + CREATE 兼容写法：
DROP USER IF EXISTS 'stellar'@'%';
CREATE USER 'stellar'@'%' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON stellar_mall.* TO 'stellar'@'%' WITH GRANT OPTION;

-- 也允许本地 127.0.0.1 / localhost 连接（避免 localhost 走 socket 认不到 %）
DROP USER IF EXISTS 'stellar'@'localhost';
CREATE USER 'stellar'@'localhost' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON stellar_mall.* TO 'stellar'@'localhost' WITH GRANT OPTION;

DROP USER IF EXISTS 'stellar'@'127.0.0.1';
CREATE USER 'stellar'@'127.0.0.1' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON stellar_mall.* TO 'stellar'@'127.0.0.1' WITH GRANT OPTION;

-- -------------------------------------------------------------------------
-- 3. 只读账号 stellar_ro / 123456 —— 给 RAG Python 桥接层用（只能 SELECT）
--    按用户 Q2 「密码都为 123456」要求，和主账号保持同密码。
--    ⚠️ 这样做的风险说明：
--       - 低风险原因：该账号只有 SELECT，即使同密码也不会改坏业务数据
--       - 部署上建议生产环境换各自强随机密码（用 ENV 注入，不要写死）
-- -------------------------------------------------------------------------
DROP USER IF EXISTS 'stellar_ro'@'%';
CREATE USER 'stellar_ro'@'%' IDENTIFIED BY '123456';
GRANT SELECT ON stellar_mall.* TO 'stellar_ro'@'%';

DROP USER IF EXISTS 'stellar_ro'@'localhost';
CREATE USER 'stellar_ro'@'localhost' IDENTIFIED BY '123456';
GRANT SELECT ON stellar_mall.* TO 'stellar_ro'@'localhost';

DROP USER IF EXISTS 'stellar_ro'@'127.0.0.1';
CREATE USER 'stellar_ro'@'127.0.0.1' IDENTIFIED BY '123456';
GRANT SELECT ON stellar_mall.* TO 'stellar_ro'@'127.0.0.1';

-- -------------------------------------------------------------------------
-- 4. 刷新权限
-- -------------------------------------------------------------------------
FLUSH PRIVILEGES;

-- =========================================================================
-- 执行完验证（root 登录后执行）：
--   SELECT user, host FROM mysql.user WHERE user IN ('stellar','stellar_ro');
--   SHOW GRANTS FOR 'stellar'@'%';
--   SHOW GRANTS FOR 'stellar_ro'@'%';
--
-- 验证账号登录（系统终端）：
--   mysql -ustellar    -p123456 -D stellar_mall -e "SELECT 1;"
--   mysql -ustellar_ro -p123456 -D stellar_mall -e "SELECT 1;"
-- =========================================================================
