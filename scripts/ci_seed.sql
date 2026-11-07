-- CI 真实后端链路种子：管理员与测试用户，密码均为 123456。
-- 仅用于 CI/本地一次性测试库，不要在生产执行。
INSERT INTO stellar_employee
    (id, username, name, password_hash, phone, sex, id_number, avatar, status, role,
     create_time, create_user, update_time, update_user)
VALUES
    (1, 'admin', 'CI Admin',
     '$2b$10$GVn99SZYM4CueM5pNRImuOi1ysJib26MDOAw0wWsOTalV.z7EaHfy',
     '13800000000', '男', NULL, NULL, 1, 1, NOW(), 0, NOW(), 0)
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), status = 1, role = 1;

INSERT INTO stellar_mall_user
    (id, email, nickname, password, status, create_time, create_user, update_time, update_user)
VALUES
    (1, 'ci-user@stellar.test', 'CI User',
     '$2b$10$GVn99SZYM4CueM5pNRImuOi1ysJib26MDOAw0wWsOTalV.z7EaHfy',
     1, NOW(), 0, NOW(), 0)
ON DUPLICATE KEY UPDATE password = VALUES(password), status = 1;

-- 积分账户：5200 可用，其中一笔 200 积分已到期，供 e2e 验证过期扫描。
-- 扫描后应剩余 5000，后续下单抵扣/取消/退款断言都以该值为基线。
INSERT INTO stellar_user_points
    (user_id, total_points, available_points, frozen_points, total_earned, total_spent,
     version, create_time, update_time)
VALUES
    (1, 5200, 5200, 0, 5200, 0, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE total_points = 5200, available_points = 5200, frozen_points = 0,
                        total_earned = 5200, total_spent = 0, version = 0;

INSERT INTO stellar_points_record
    (user_id, type, points, balance_after, biz_type, biz_id, description, expired_time, create_time)
SELECT 1, 1, 200, 5200, 'ADMIN', 'ci-expired-points', 'CI 过期积分种子', CURDATE(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM stellar_points_record WHERE user_id = 1 AND biz_id = 'ci-expired-points'
);