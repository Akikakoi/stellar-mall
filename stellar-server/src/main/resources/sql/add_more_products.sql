-- ============================================================
-- 商品数据扩充脚本：更新图片 + 新增分类 + 新增商品
-- 执行方式：mysql -u stellar -p123456 stellar_mall < this_file.sql
-- ============================================================

-- 1. 更新已有商品图片为真实占位图
UPDATE stellar_spu SET main_image = 'https://picsum.photos/seed/phone-x100pro/400/400' WHERE id = 1;
UPDATE stellar_spu SET main_image = 'https://picsum.photos/seed/fridge-516L/400/400'      WHERE id = 2;
UPDATE stellar_spu SET main_image = 'https://picsum.photos/seed/ac-cabinet/400/400'        WHERE id = 3;
UPDATE stellar_spu SET main_image = 'https://picsum.photos/seed/laptop-airbook14/400/400'  WHERE id = 4;
UPDATE stellar_spu SET main_image = 'https://picsum.photos/seed/tv-65q80/400/400'          WHERE id = 5;
UPDATE stellar_spu SET main_image = 'https://picsum.photos/seed/soundbar-vivid/400/400'    WHERE id = 6;

-- 2. 新增分类
INSERT IGNORE INTO stellar_category (id, name, parent_id, level, type, sort, status, create_time, create_user, update_time, update_user) VALUES
(8, '智能穿戴',   0, 1, 1, 8, 1, NOW(), 1, NOW(), 1),
(9, '生活家电',   0, 1, 1, 9, 1, NOW(), 1, NOW(), 1);

-- 3. 新增 SPU（id 从 7 开始，避开已有 1-6）
INSERT INTO stellar_spu (id, name, sub_title, category_id, category2_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

-- ===== 智能手机 =====
(7, '星耀 X100 青春版', '骁龙 7+ Gen3 · 5000mAh · 67W 快充',
    1, NULL,
    '# 星耀 X100 青春版\n\n- **芯片**：骁龙 7+ Gen3 4nm 工艺\n- **屏幕**：6.67 寸 AMOLED 120Hz\n- **内存**：8GB / 12GB LPDDR5\n- **存储**：128GB / 256GB UFS 3.1\n- **电池**：5000mAh，67W 快充 38 分钟充满\n- **相机**：50MP 主摄 OIS + 8MP 超广角',
    'https://picsum.photos/seed/phone-x100-lite/400/400', NULL,
    95, 1, NOW(), 1999.00, 2499.00, 300, 2, 1, 0,
    NOW(), 1, NOW(), 1),

(8, '星耀 X100 Pro Max', '骁龙 8 Gen4 · 2K LTPO · 卫星通信',
    1, NULL,
    '# 星耀 X100 Pro Max\n\n- **芯片**：骁龙 8 Gen4 3nm 领先版\n- **屏幕**：6.82 寸 2K+ LTPO 1-120Hz 自适应\n- **内存**：16GB LPDDR5T 9600Mbps\n- **存储**：512GB / 1TB UFS 4.1\n- **电池**：6000mAh 硅碳负极 · 120W 有线 + 80W 无线\n- **影像**：1 英寸主摄 + 200MP 潜望长焦 + 卫星通信',
    'https://picsum.photos/seed/phone-x100-promax/400/400', NULL,
    99, 1, NOW(), 5999.00, 7999.00, 80, 2, 1, 1,
    NOW(), 1, NOW(), 1),

-- ===== 冰箱 =====
(9, '极净 610L 对开门冰箱', '风冷无霜 · 双变频 · 智能互联',
    2, NULL,
    '# 极净 610L 对开门冰箱\n\n- **容量**：610L（冷藏 388L / 冷冻 222L）\n- **能效**：新国标一级，日耗电 0.95 度\n- **制冷**：风冷无霜 · 双循环不串味\n- **特色**：-24℃ 深冷速冻 · 假日模式 · WiFi 远程控温\n- **尺寸**：910×730×1780mm',
    'https://picsum.photos/seed/fridge-610L/400/400', NULL,
    85, 1, NOW(), 3999.00, 4599.00, 60, 2, 0, 0,
    NOW(), 1, NOW(), 1),

(10, '极净 328L 三门冰箱', '变频静音 · 中门变温 · 小户型优选',
    2, NULL,
    '# 极净 328L 三门冰箱\n\n- **容量**：328L（冷藏 186L / 冷冻 92L / 变温 50L）\n- **变温**：中门 -18℃~5℃ 宽幅变温\n- **能效**：新国标一级，日耗电 0.62 度\n- **静音**：38dB 超静音\n- **尺寸**：640×680×1805mm，适合小户型',
    'https://picsum.photos/seed/fridge-328L/400/400', NULL,
    82, 1, NOW(), 2499.00, 2999.00, 100, 2, 0, 0,
    NOW(), 1, NOW(), 1),

-- ===== 空调 =====
(11, '御风 1.5 匹一级变频挂机', '新一级能效 · 自清洁 · 智能控温',
    3, NULL,
    '# 御风 1.5 匹挂机\n\n- **制冷量**：3500W，适用 15-22㎡\n- **能效**：APF 5.27，超一级能效\n- **特色**：0.5℃ 精准控温 · 57℃ 高温自清洁\n- **静音**：18dB 超静音睡眠模式\n- **智能**：WiFi 远程控制 · 语音操控',
    'https://picsum.photos/seed/ac-1point5/400/400', NULL,
    90, 1, NOW(), 2699.00, 3299.00, 120, 2, 0, 1,
    NOW(), 1, NOW(), 1),

(12, '御风 2 匹一级变频挂机', '大客厅专用 · 急速冷暖 · 广角送风',
    3, NULL,
    '# 御风 2 匹挂机\n\n- **制冷量**：5100W，适用 25-35㎡\n- **能效**：APF 4.80，新国标一级\n- **送风**：110° 广角送风 · 1300m³/h 大风量\n- **特色**：UV 杀菌 · 智能除湿 · 防直吹\n- **适用**：大客厅 / 商铺 / 办公室',
    'https://picsum.photos/seed/ac-2hp/400/400', NULL,
    85, 1, NOW(), 4299.00, 4799.00, 50, 1, 0, 0,
    NOW(), 1, NOW(), 1),

-- ===== 笔记本 =====
(13, '清逸 ProBook 16 高性能本', 'RTX 4070 · 240Hz · 标压 i9',
    4, NULL,
    '# 清逸 ProBook 16\n\n- **屏幕**：16 寸 3.2K 240Hz MiniLED 100% DCI-P3\n- **CPU**：Intel Core i9-14900HX 24核32线程\n- **GPU**：NVIDIA RTX 4070 8GB 140W 满血\n- **内存**：32GB DDR5 5600MHz\n- **存储**：2TB PCIe 4.0\n- **散热**：双风扇 5 热管 · 整机 200W 性能释放',
    'https://picsum.photos/seed/laptop-probook16/400/400', NULL,
    90, 1, NOW(), 10999.00, 12999.00, 30, 1, 1, 1,
    NOW(), 1, NOW(), 1),

(14, '清逸 AirBook 13 极致轻薄', '980g · 2.5K OLED · 16h 续航',
    4, NULL,
    '# 清逸 AirBook 13\n\n- **屏幕**：13.3 寸 2.5K OLED 100% DCI-P3\n- **CPU**：Intel Core Ultra 5 228H\n- **内存**：16GB LPDDR5X\n- **存储**：1TB PCIe 4.0\n- **重量**：980g · 厚度 11.9mm\n- **续航**：70Wh 电池，16 小时本地视频\n- **配色**：星光银 / 深空灰',
    'https://picsum.photos/seed/laptop-airbook13/400/400', NULL,
    93, 1, NOW(), 5499.00, 6499.00, 80, 2, 0, 0,
    NOW(), 1, NOW(), 1),

-- ===== 电视 =====
(15, '逸彩 75 寸 QD-MiniLED 电视', '量子点 · 3000nits · 全阵列 3000 分区',
    5, NULL,
    '# 逸彩 75Q90 MiniLED\n\n- **屏幕**：75 寸 QD-MiniLED，量子点广色域 99% DCI-P3\n- **亮度**：峰值 3000nits，HDR10+ / Dolby Vision IQ\n- **分区**：全阵列 3000 分区背光\n- **音频**：10 单元 80W · Dolby Atmos 全景声\n- **游戏**：HDMI 2.1 × 4 · 4K 144Hz · VRR · ALLM\n- **系统**：星耀 SmartHub 3.0',
    'https://picsum.photos/seed/tv-75q90/400/400', NULL,
    88, 1, NOW(), 6999.00, 7999.00, 40, 1, 1, 1,
    NOW(), 1, NOW(), 1),

(16, '逸彩 55 寸 OLED 电视', '自发光 · 无限对比度 · 超薄 4mm',
    5, NULL,
    '# 逸彩 55Q60 OLED\n\n- **面板**：55 寸 OLED 自发光，像素级控光\n- **色域**：100% DCI-P3 · 10bit 色深\n- **HDR**：Dolby Vision IQ · HDR10+ Adaptive\n- **音频**：6 单元 40W · Dolby Atmos\n- **游戏**：4K 120Hz · G-Sync · FreeSync\n- **设计**：4mm 极致超薄 · 无边框设计',
    'https://picsum.photos/seed/tv-55oled/400/400', NULL,
    85, 1, NOW(), 4999.00, 5999.00, 50, 1, 0, 0,
    NOW(), 1, NOW(), 1),

-- ===== 影音 =====
(17, '逸彩 VividPods Pro 真无线耳机', '主动降噪 · 空间音频 · 30h 续航',
    6, NULL,
    '# 逸彩 VividPods Pro\n\n- **降噪**：自适应 ANC 主动降噪，深度 48dB\n- **音质**：12mm 动圈 · LDAC 高清解码\n- **空间音频**：动态头部追踪\n- **续航**：单次 7h + 充电盒 30h\n- **连接**：蓝牙 5.4 · 双设备无缝切换\n- **防水**：IPX5 运动防汗',
    'https://picsum.photos/seed/earbuds-pro/400/400', NULL,
    80, 1, NOW(), 699.00, 699.00, 500, 1, 1, 1,
    NOW(), 1, NOW(), 1),

(18, '逸彩 VividSound 智能音箱', 'Hi-Res 认证 · 360° 环绕 · 语音助手',
    6, NULL,
    '# 逸彩 VividSound 智能音箱\n\n- **音频**：2.1 声道 · 60W 总功率 · Hi-Res 认证\n- **声场**：360° 全向环绕声\n- **连接**：WiFi 6 · 蓝牙 5.3 · AirPlay 2\n- **智能**：内置星耀语音助手\n- **特色**：多房间串联 · 闹钟 · 场景联动',
    'https://picsum.photos/seed/speaker-smart/400/400', NULL,
    78, 1, NOW(), 899.00, 899.00, 300, 1, 0, 0,
    NOW(), 1, NOW(), 1),

-- ===== 智能穿戴 =====
(19, '星耀 Watch 3 Pro 智能手表', '钛合金 · 1.5 寸 LTPO · 14 天续航',
    8, NULL,
    '# 星耀 Watch 3 Pro\n\n- **材质**：钛合金表壳 + 蓝宝石玻璃\n- **屏幕**：1.5 寸 LTPO OLED 常亮显示\n- **续航**：14 天典型 / 5 天重度\n- **健康**：心率 · 血氧 · 体温 · ECG 心电图\n- **运动**：150+ 运动模式 · 双频 GPS\n- **防水**：5ATM + IP68',
    'https://picsum.photos/seed/watch-3pro/400/400', NULL,
    85, 1, NOW(), 1999.00, 2499.00, 150, 2, 1, 1,
    NOW(), 1, NOW(), 1),

(20, '星耀 Band 8 智能手环', 'AMOLED · 血氧监测 · 14 天续航',
    8, NULL,
    '# 星耀 Band 8 智能手环\n\n- **屏幕**：1.62 寸 AMOLED 彩色屏\n- **续航**：14 天典型使用\n- **健康**：心率 · 血氧 · 睡眠 · 压力\n- **运动**：100+ 运动模式\n- **特色**：支付宝离线支付 · 消息通知\n- **防水**：5ATM 游泳级',
    'https://picsum.photos/seed/band-8/400/400', NULL,
    75, 1, NOW(), 249.00, 299.00, 500, 2, 0, 0,
    NOW(), 1, NOW(), 1),

-- ===== 生活家电 =====
(21, '极净 AutoClean X1 扫地机器人', 'LDS 激光导航 · 5000Pa · 自动集尘',
    9, NULL,
    '# 极净 AutoClean X1\n\n- **导航**：LDS 激光 + 3D 结构光避障\n- **吸力**：5000Pa 超大吸力\n- **集尘**：自动集尘底座，60 天免倒垃圾\n- **拖地**：旋转加压拖地 · 自动回洗拖布\n- **续航**：5200mAh，单次清扫 200㎡\n- **智能**：AI 识别地毯增压 · 禁区设置',
    'https://picsum.photos/seed/robot-vacuum/400/400', NULL,
    82, 1, NOW(), 2999.00, 3499.00, 80, 1, 1, 0,
    NOW(), 1, NOW(), 1),

(22, '御风 PureAir Pro 空气净化器', 'CADR 800 · 除甲醛 · UV 杀菌',
    9, NULL,
    '# 御风 PureAir Pro\n\n- **净化**：颗粒物 CADR 800m³/h · 甲醛 CADR 400m³/h\n- **滤芯**：H13 HEPA + 改性活性炭 3kg\n- **杀菌**：UV-C 紫外线 + 等离子\n- **适用**：56-96㎡ 大空间\n- **传感**：PM2.5 · PM10 · 甲醛 · TVOC 四合一\n- **静音**：最低 28dB',
    'https://picsum.photos/seed/air-purifier/400/400', NULL,
    78, 1, NOW(), 1999.00, 2499.00, 60, 1, 0, 0,
    NOW(), 1, NOW(), 1);

-- 4. 新增 SKU（id 从 11 开始，避开已有 1-10）
INSERT INTO stellar_sku (id, spu_id, name, specs, price, original_price, stock, version, sort, status,
                         create_time, create_user, update_time, update_user) VALUES
-- SPU 7 星耀 X100 青春版
(11, 7, '星耀 X100 青春版 · 8+128GB', '内存:8GB;存储:128GB', 1999.00, 2199.00, 200, 0, 1, 1, NOW(), 1, NOW(), 1),
(12, 7, '星耀 X100 青春版 · 12+256GB', '内存:12GB;存储:256GB', 2499.00, 2699.00, 100, 0, 2, 1, NOW(), 1, NOW(), 1),
-- SPU 8 星耀 X100 Pro Max
(13, 8, '星耀 X100 Pro Max · 16+512GB', '内存:16GB;存储:512GB', 5999.00, 6499.00, 50, 0, 1, 1, NOW(), 1, NOW(), 1),
(14, 8, '星耀 X100 Pro Max · 16+1TB',   '内存:16GB;存储:1TB',   7999.00, 8599.00, 30, 0, 2, 1, NOW(), 1, NOW(), 1),
-- SPU 9 极净 610L 对开门
(15, 9, '极净 610L 对开门 · 钛灰',   '颜色:钛灰',   3999.00, 4299.00, 35, 0, 1, 1, NOW(), 1, NOW(), 1),
(16, 9, '极净 610L 对开门 · 星耀银', '颜色:星耀银', 4599.00, 4899.00, 25, 0, 2, 1, NOW(), 1, NOW(), 1),
-- SPU 10 极净 328L 三门
(17, 10, '极净 328L 三门 · 雅致白', '颜色:雅致白', 2499.00, 2699.00, 60, 0, 1, 1, NOW(), 1, NOW(), 1),
(18, 10, '极净 328L 三门 · 静谧金', '颜色:静谧金', 2999.00, 3199.00, 40, 0, 2, 1, NOW(), 1, NOW(), 1),
-- SPU 11 御风 1.5 匹挂机
(19, 11, '御风 1.5 匹挂机 · 皓月白', '颜色:皓月白', 2699.00, 2999.00, 80, 0, 1, 1, NOW(), 1, NOW(), 1),
(20, 11, '御风 1.5 匹挂机 · 流光金', '颜色:流光金', 3299.00, 3599.00, 40, 0, 2, 1, NOW(), 1, NOW(), 1),
-- SPU 12 御风 2 匹挂机
(21, 12, '御风 2 匹挂机 · 星空灰', '颜色:星空灰', 4299.00, 4799.00, 50, 0, 1, 1, NOW(), 1, NOW(), 1),
-- SPU 13 清逸 ProBook 16
(22, 13, '清逸 ProBook 16 · i9 32G 2T RTX4070', 'CPU:i9-14900HX;内存:32G;存储:2T;显卡:RTX4070', 10999.00, 12999.00, 30, 0, 1, 1, NOW(), 1, NOW(), 1),
-- SPU 14 清逸 AirBook 13
(23, 14, '清逸 AirBook 13 · Ultra5 16G 1T 星光银', 'CPU:Ultra5 228H;内存:16G;存储:1T;颜色:星光银', 5499.00, 5999.00, 50, 0, 1, 1, NOW(), 1, NOW(), 1),
(24, 14, '清逸 AirBook 13 · Ultra5 16G 1T 深空灰', 'CPU:Ultra5 228H;内存:16G;存储:1T;颜色:深空灰', 6499.00, 6999.00, 30, 0, 2, 1, NOW(), 1, NOW(), 1),
-- SPU 15 逸彩 75Q90
(25, 15, '逸彩 75Q90 MiniLED', '型号:标准版', 6999.00, 7999.00, 40, 0, 1, 1, NOW(), 1, NOW(), 1),
-- SPU 16 逸彩 55Q60 OLED
(26, 16, '逸彩 55Q60 OLED', '型号:标准版', 4999.00, 5999.00, 50, 0, 1, 1, NOW(), 1, NOW(), 1),
-- SPU 17 逸彩 VividPods Pro
(27, 17, '逸彩 VividPods Pro · 月岩白', '颜色:月岩白', 699.00, 799.00, 300, 0, 1, 1, NOW(), 1, NOW(), 1),
-- SPU 18 逸彩 VividSound
(28, 18, '逸彩 VividSound 智能音箱', '颜色:经典黑', 899.00, 999.00, 300, 0, 1, 1, NOW(), 1, NOW(), 1),
-- SPU 19 星耀 Watch 3 Pro
(29, 19, '星耀 Watch 3 Pro · 钛金属 运动表带', '材质:钛金属;表带:运动', 1999.00, 2199.00, 100, 0, 1, 1, NOW(), 1, NOW(), 1),
(30, 19, '星耀 Watch 3 Pro · 钛金属 真皮表带', '材质:钛金属;表带:真皮', 2499.00, 2699.00, 50, 0, 2, 1, NOW(), 1, NOW(), 1),
-- SPU 20 星耀 Band 8
(31, 20, '星耀 Band 8 · 午夜黑', '颜色:午夜黑', 249.00, 269.00, 300, 0, 1, 1, NOW(), 1, NOW(), 1),
(32, 20, '星耀 Band 8 · 珊瑚粉', '颜色:珊瑚粉', 299.00, 319.00, 200, 0, 2, 1, NOW(), 1, NOW(), 1),
-- SPU 21 极净 AutoClean X1
(33, 21, '极净 AutoClean X1 · 标准版', '型号:标准版', 2999.00, 3499.00, 80, 0, 1, 1, NOW(), 1, NOW(), 1),
-- SPU 22 御风 PureAir Pro
(34, 22, '御风 PureAir Pro · 标准版', '型号:标准版', 1999.00, 2499.00, 60, 0, 1, 1, NOW(), 1, NOW(), 1);

-- 更新 SPU 的 total_stock 和 sku_count（已有 SPU 1-6 保持不变，新增的用上面数据）
-- 这里不需要额外操作，上面 INSERT 已经包含了正确值