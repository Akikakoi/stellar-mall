-- MySQL 初始化：授予 stellar 用户 stellar_mall 库完整权限
GRANT ALL PRIVILEGES ON stellar_mall.* TO 'stellar'@'%';
FLUSH PRIVILEGES;
