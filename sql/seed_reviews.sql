-- ============================================================
-- 商品评论种子数据（整体偏好评、少数差评）
-- 说明：
--   1. 新增 8 个仅用于评论展示的账号（无手机/邮箱，password 为占位串不可登录）
--   2. 每个在售 SPU 生成 6 条评论；评分从 20 格加权池轮转抽取：
--      5星×9 / 4星×6 / 3星×3 / 2星×1 / 1星×1，全局均分约 4.1
--   3. 1~2 星差评全部附带官方安抚回复
--   4. 创建时间在过去 90 天内错开分布
--   5. 展示账号按昵称动态定位（ROW_NUMBER），不依赖自增 id
-- 幂等性：重复执行会重复插入，执行前请确认 stellar_review 为空或自行去重
-- ============================================================
-- ============================================================

-- 1. 展示用账号（password 为占位串，无法用于登录）
INSERT INTO stellar_mall_user (nickname, password, status, create_time, create_user, update_time, update_user) VALUES
('星河漫步者', 'seed-no-login', 1, NOW(), 0, NOW(), 0),
('晚风不停', 'seed-no-login', 1, NOW(), 0, NOW(), 0),
('Momo酱', 'seed-no-login', 1, NOW(), 0, NOW(), 0),
('山月不知', 'seed-no-login', 1, NOW(), 0, NOW(), 0),
('阿柴今天也很乖', 'seed-no-login', 1, NOW(), 0, NOW(), 0),
('凌晨四点的海', 'seed-no-login', 1, NOW(), 0, NOW(), 0),
('脆皮鸭学生党', 'seed-no-login', 1, NOW(), 0, NOW(), 0),
('不熬夜冠军', 'seed-no-login', 1, NOW(), 0, NOW(), 0);

-- 2. 生成评论
INSERT INTO stellar_review (user_id, spu_id, sku_id, rating, content, reply, reply_time, status, create_time)
WITH
spus AS (SELECT id FROM stellar_spu WHERE status = 1),
slots AS (
    SELECT 0 AS i UNION ALL SELECT 1 UNION ALL SELECT 2
    UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
),
pool(rating, idx, content) AS (
    -- 1 星 · 差评池
    SELECT 1, 0, '和描述差得太远了，收到就后悔，这个价格买了个寂寞。' UNION ALL
    SELECT 1, 1, '用了三天就出问题，找客服半天不回，体验极差。' UNION ALL
    SELECT 1, 2, '包装破损，里面都有划痕，这也叫严检发货？' UNION ALL
    SELECT 1, 3, '做工粗糙，接缝处毛边明显，一分钱一分货吧。' UNION ALL
    SELECT 1, 4, '物流慢得离谱，等了一个多星期才到，期间问客服也是敷衍。' UNION ALL
    SELECT 1, 5, '不值这个价，功能缩水，说明书还和实物对不上。' UNION ALL
    SELECT 1, 6, '第一次遇到质量这么差的，果断申请售后了。' UNION ALL
    SELECT 1, 7, '图片是买家秀，实物是卖家秀，落差太大，差评。' UNION ALL
    -- 2 星 · 偏差评池
    SELECT 2, 0, '整体一般，能用但谈不上满意，细节处理比较潦草。' UNION ALL
    SELECT 2, 1, '到手有点小瑕疵，不影响使用，但心情不太美丽。' UNION ALL
    SELECT 2, 2, '比预期差一些，凑合用吧，应该不会再回购了。' UNION ALL
    SELECT 2, 3, '物流倒是还行，但品质对不上宣传，略有失望。' UNION ALL
    SELECT 2, 4, '用了几天感觉一般，同类产品里有更好的选择。' UNION ALL
    SELECT 2, 5, '客服回复慢，问题解决得拖拖拉拉，不太愉快的一次购物。' UNION ALL
    SELECT 2, 6, '性价比一般，这个价位可以买到更好的。' UNION ALL
    SELECT 2, 7, '包装简陋，附件还少了一个，好在主体没大问题。' UNION ALL
    -- 3 星 · 中评池
    SELECT 3, 0, '中规中矩，该有的都有，没什么惊喜也没什么槽点。' UNION ALL
    SELECT 3, 1, '品质对得起价格，但离惊艳还差一点。' UNION ALL
    SELECT 3, 2, '用了两周，基本功能没问题，个别细节还有提升空间。' UNION ALL
    SELECT 3, 3, '物流挺快，产品中规中矩，适合预算有限的朋友。' UNION ALL
    SELECT 3, 4, '整体还行，就是有点小瑕疵，可以接受。' UNION ALL
    SELECT 3, 5, '不好不坏，符合预期，家里用够了。' UNION ALL
    SELECT 3, 6, '做工比想象中普通，胜在价格实惠。' UNION ALL
    SELECT 3, 7, '能正常用，但包装和细节一般，看个人需求吧。' UNION ALL
    -- 4 星 · 偏好评池
    SELECT 4, 0, '整体很满意，做工不错物流也快，就是包装略简单。' UNION ALL
    SELECT 4, 1, '性价比很高，用了几天体验良好，推荐给同事了。' UNION ALL
    SELECT 4, 2, '细节比预期好，客服响应也及时，小扣一星在物流。' UNION ALL
    SELECT 4, 3, '质量在线，颜值也高，差一点是说明书不太详细。' UNION ALL
    SELECT 4, 4, '用了一周没什么问题，运行稳定，值得入手。' UNION ALL
    SELECT 4, 5, '比实体店便宜不少，品质没缩水，就是送货慢了半天。' UNION ALL
    SELECT 4, 6, '朋友推荐买的，果然没让人失望，就是配色选择少了点。' UNION ALL
    SELECT 4, 7, '手感不错，功能齐全，日常使用完全够，性价比可以。' UNION ALL
    -- 5 星 · 好评池
    SELECT 5, 0, '非常满意！包装严实，做工精致，比图片还好看，五星不解释。' UNION ALL
    SELECT 5, 1, '物流飞快，第二天就到了，开箱惊喜，用起来顺手，回购预定！' UNION ALL
    SELECT 5, 2, '用过最好的一个，品质、颜值、性价比全都在线，闭眼入。' UNION ALL
    SELECT 5, 3, '客服很耐心，选型建议专业，收到的货完美无瑕疵，好评！' UNION ALL
    SELECT 5, 4, '用了半个月来追评，质量稳定没出过任何问题，真心推荐。' UNION ALL
    SELECT 5, 5, '一次非常愉快的购物，从下单到收货体验拉满，家人都说买得好。' UNION ALL
    SELECT 5, 6, '细节控狂喜，边角处理得很到位，质感超出这个价位。' UNION ALL
    SELECT 5, 7, '收到就迫不及待试了，效果远超预期，已经推荐给身边朋友了。'
),
seed_users AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) - 1 AS rn
    FROM stellar_mall_user
    WHERE password = 'seed-no-login'
),
weighted(rn, rating) AS (
    -- 20 格加权池：5星×9 / 4星×6 / 3星×3 / 2星×1 / 1星×1
    SELECT 0,5 UNION ALL SELECT 1,4 UNION ALL SELECT 2,5 UNION ALL SELECT 3,3
    UNION ALL SELECT 4,5 UNION ALL SELECT 5,4 UNION ALL SELECT 6,5 UNION ALL SELECT 7,2
    UNION ALL SELECT 8,4 UNION ALL SELECT 9,5 UNION ALL SELECT 10,3 UNION ALL SELECT 11,5
    UNION ALL SELECT 12,4 UNION ALL SELECT 13,1 UNION ALL SELECT 14,5 UNION ALL SELECT 15,3
    UNION ALL SELECT 16,4 UNION ALL SELECT 17,5 UNION ALL SELECT 18,5 UNION ALL SELECT 19,4
),
combined AS (
    SELECT
        su.id AS uid,
        p.id AS spu_id,
        (SELECT MIN(k.id) FROM stellar_sku k WHERE k.spu_id = p.id) AS sku_id,
        w.rating AS rating,
        ((p.id * 3 + s.i) MOD 8) AS pidx,                                  -- 同星级的文案轮转
        (p.id * 13 + s.i * 7) MOD 90 AS days_ago                           -- 时间错开分布
    FROM spus p
    CROSS JOIN slots s
    JOIN weighted w ON w.rn = (s.i + p.id) MOD 20
    JOIN seed_users su ON su.rn = (s.i + p.id) MOD 8
)
SELECT
    c.uid,
    c.spu_id,
    c.sku_id,
    c.rating,
    pool.content,
    CASE WHEN c.rating <= 2
         THEN '您好，非常抱歉给您带来不好的体验，已安排专属客服一对一跟进，请留意站内私信，我们会尽快给您一个满意的解决方案。'
         ELSE '' END,
    CASE WHEN c.rating <= 2
         THEN DATE_SUB(NOW(), INTERVAL GREATEST(c.days_ago - 2, 0) DAY)
         ELSE NULL END,
    1,
    DATE_SUB(NOW(), INTERVAL c.days_ago DAY)
FROM combined c
JOIN pool ON pool.rating = c.rating AND pool.idx = c.pidx;
