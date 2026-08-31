-- ============================================================
-- 星耀商城 - 扩充商品数据 + 详细产品文档
-- 每个类别新增 4-5 个 SPU，均包含丰富的产品说明供 AI 检索
-- ============================================================
USE stellar_mall;

-- ============================================================
-- 1. 智能手机 (category_id=1)
-- ============================================================

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('星耀 X200 5G 影像旗舰', '徕卡光学镜头 · 1英寸大底 · 骁龙8 Gen4',
    1,
    '# 星耀 X200 5G 影像旗舰

## 产品概述
星耀 X200 是星耀品牌的影像旗舰手机，与徕卡联合研发的光学系统，搭载 1 英寸大底传感器，为专业摄影师和摄影爱好者打造。支持全焦段 4K 120fps 视频录制。

## 核心参数
| 项目 | 规格 |
|------|------|
| 处理器 | 骁龙 8 Gen4 3nm |
| 屏幕 | 6.73 寸 2K+ LTPO OLED，1-120Hz 自适应刷新率 |
| 后置相机 | 50MP 一英寸主摄(OIS) + 50MP 超广角 + 50MP 3.2x 长焦(OIS) + 50MP 5x 潜望长焦(OIS) |
| 前置相机 | 32MP |
| 电池 | 5400mAh 硅碳负极 |
| 充电 | 120W 有线 + 50W 无线 + 10W 反向 |
| 系统 | StellarOS 3.0（基于 Android 15） |
| 防护 | IP68 防水防尘 |
| 重量 | 219g |

## 影像系统详解
- **一英寸主摄**：索尼 LYT-900 传感器，f/1.63 光圈，支持双原生 ISO Fusion，暗光画质提升 40%
- **徕卡色彩科学**：提供"徕卡经典"和"徕卡鲜艳"两种色彩风格
- **大师人像**：支持 23mm/35mm/50mm/75mm/90mm 五个经典焦段
- **夜景视频**：AI 降噪算法，4K 夜景视频噪点降低 60%
- **8K 视频**：支持 8K@30fps 全像素读取

## 购买建议
- 摄影爱好者、内容创作者首选
- 需要顶级影像能力的用户
- 对屏幕素质有高要求的用户

## 常见问题
**Q: 是否支持卫星通信？**
A: 本机型支持北斗卫星消息（仅限中国境内使用，需开通服务）。

**Q: 是否带充电器和数据线？**
A: 包装内含 120W 氮化镓充电器、USB-C to USB-C 6A 数据线、透明保护壳。

**Q: 防水性能如何？**
A: IP68 等级，可在 1.5 米深清水中浸泡 30 分钟。但不建议在海水中使用。

**Q: 系统更新支持多久？**
A: 承诺 4 年 Android 大版本更新 + 5 年安全补丁。',
    'https://picsum.photos/seed/phone-x200/400/400',
    'https://picsum.photos/seed/phone-x200-back/400/400;https://picsum.photos/seed/phone-x200-side/400/400',
    99, 1, NOW(), 5999.00, 7499.00, 500, 3, 1, 1, NOW(), 1, NOW(), 1);

SET @spu_x200 = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_x200, '星耀 X200 · 钛灰 · 12+256GB', '颜色:钛灰;内存:12GB;存储:256GB', '{"颜色":"钛灰","内存":"12GB","存储":"256GB"}', 5999.00, 6499.00, 4800.00, 200, 10, 219, 'PH-X200-12-256-GY', 10, 1, NOW(), 1, NOW(), 1),
(@spu_x200, '星耀 X200 · 钛灰 · 16+512GB', '颜色:钛灰;内存:16GB;存储:512GB', '{"颜色":"钛灰","内存":"16GB","存储":"512GB"}', 6999.00, 7499.00, 5600.00, 150, 10, 219, 'PH-X200-16-512-GY', 20, 1, NOW(), 1, NOW(), 1),
(@spu_x200, '星耀 X200 · 陶瓷白 · 16+1TB', '颜色:陶瓷白;内存:16GB;存储:1TB', '{"颜色":"陶瓷白","内存":"16GB","存储":"1TB"}', 7499.00, 7999.00, 6000.00, 100, 10, 223, 'PH-X200-16-1TB-WH', 30, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 3, total_stock = 450 WHERE id = @spu_x200;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('星耀 Note 14 Pro', '2亿像素 · 120W快充 · 轻薄长续航',
    1,
    '# 星耀 Note 14 Pro

## 产品概述
星耀 Note 14 Pro 定位中高端全能机型，主打 2 亿像素主摄和超长续航。轻薄机身内塞入 5500mAh 大电池，是同价位综合体验最均衡的选择。

## 核心参数
| 项目 | 规格 |
|------|------|
| 处理器 | 骁龙 8s Gen4 |
| 屏幕 | 6.67 寸 1.5K AMOLED，120Hz |
| 后置相机 | 200MP 主摄(OIS) + 8MP 超广角 + 2MP 微距 |
| 前置相机 | 32MP |
| 电池 | 5500mAh |
| 充电 | 120W 有线 |
| 厚度 | 7.8mm |
| 重量 | 188g |

## 使用场景
- 日常拍照和社交媒体分享
- 重度使用者（5500mAh 可满足一天半使用）
- 预算 2500-3500 元的中端用户

## 常见问题
**Q: 2亿像素照片占用多少存储？**
A: 单张约 30-50MB，建议 256GB 起步。日常使用建议用 12.5MP 像素融合模式，效果更好。

**Q: 是否有耳机孔？**
A: 没有 3.5mm 耳机孔，需使用 Type-C 耳机或蓝牙耳机。

**Q: 快充对电池有损伤吗？**
A: 内置电池健康引擎，经过 1600 次充放电循环后仍保留 80% 以上容量。',
    'https://picsum.photos/seed/phone-note14/400/400',
    'https://picsum.photos/seed/phone-note14-blue/400/400;https://picsum.photos/seed/phone-note14-gold/400/400',
    85, 1, NOW(), 2799.00, 3499.00, 800, 3, 1, 0, NOW(), 1, NOW(), 1);

SET @spu_note14 = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_note14, '星耀 Note 14 Pro · 子夜黑 · 8+256GB', '颜色:子夜黑;内存:8GB;存储:256GB', '{"颜色":"子夜黑","内存":"8GB","存储":"256GB"}', 2799.00, 2999.00, 2200.00, 300, 15, 188, 'PH-N14-8-256-BK', 10, 1, NOW(), 1, NOW(), 1),
(@spu_note14, '星耀 Note 14 Pro · 冰川蓝 · 12+256GB', '颜色:冰川蓝;内存:12GB;存储:256GB', '{"颜色":"冰川蓝","内存":"12GB","存储":"256GB"}', 3099.00, 3299.00, 2450.00, 250, 15, 188, 'PH-N14-12-256-BL', 20, 1, NOW(), 1, NOW(), 1),
(@spu_note14, '星耀 Note 14 Pro · 香槟金 · 12+512GB', '颜色:香槟金;内存:12GB;存储:512GB', '{"颜色":"香槟金","内存":"12GB","存储":"512GB"}', 3499.00, 3699.00, 2800.00, 200, 15, 188, 'PH-N14-12-512-GD', 30, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 3, total_stock = 750 WHERE id = @spu_note14;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('星耀 A60 5G 学生机', '5000mAh · 护眼屏 · 学生模式',
    1,
    '# 星耀 A60 5G 学生机

## 产品概述
专为青少年和学生打造的入门 5G 手机。内置家长管控功能，支持护眼模式、应用时长管理、定位追踪。性价比极高，适合作为第一部 5G 手机。

## 核心参数
| 项目 | 规格 |
|------|------|
| 处理器 | 天玑 7300 |
| 屏幕 | 6.58 寸 FHD+ LCD，120Hz，全局 DC 调光 |
| 后置相机 | 64MP 主摄 + 2MP 景深 |
| 前置相机 | 16MP |
| 电池 | 5000mAh |
| 充电 | 33W 有线 |
| 系统 | StellarOS Lite（家长管控版） |

## 学生模式功能
- **应用白名单**：家长可设置允许使用的应用
- **屏幕时间管理**：设置每日使用时长上限
- **定位追踪**：实时查看孩子位置
- **学习模式**：上课时间自动静音，仅允许学习类应用
- **护眼提醒**：距离过近、光线过暗自动提醒

## 常见问题
**Q: 学生模式可以关闭吗？**
A: 需要家长密码才能关闭。如遗忘密码，可通过绑定手机号找回。

**Q: 打游戏流畅吗？**
A: 王者荣耀高帧率流畅运行，原神中低画质 40fps 左右。定位是学习和日常使用，非游戏手机。',
    'https://picsum.photos/seed/phone-a60/400/400',
    'https://picsum.photos/seed/phone-a60-green/400/400',
    70, 1, NOW(), 1299.00, 1699.00, 1200, 2, 1, 0, NOW(), 1, NOW(), 1);

SET @spu_a60 = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_a60, '星耀 A60 · 薄荷绿 · 6+128GB', '颜色:薄荷绿;内存:6GB;存储:128GB', '{"颜色":"薄荷绿","内存":"6GB","存储":"128GB"}', 1299.00, 1499.00, 1000.00, 500, 20, 195, 'PH-A60-6-128-GN', 10, 1, NOW(), 1, NOW(), 1),
(@spu_a60, '星耀 A60 · 星空黑 · 8+256GB', '颜色:星空黑;内存:8GB;存储:256GB', '{"颜色":"星空黑","内存":"8GB","存储":"256GB"}', 1699.00, 1899.00, 1300.00, 400, 20, 195, 'PH-A60-8-256-BK', 20, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 2, total_stock = 900 WHERE id = @spu_a60;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('星耀 Flip 折叠屏', '竖折设计 · 3.6寸外屏 · 轻薄时尚',
    1,
    '# 星耀 Flip 折叠屏

## 产品概述
星耀首款竖向折叠屏手机，翻盖式设计致敬经典。展开是 6.8 寸旗舰内屏，折叠后仅掌心大小。3.6 寸外屏支持全功能操作，不用翻开就能回复消息、拍照、导航。

## 核心参数
| 项目 | 规格 |
|------|------|
| 处理器 | 骁龙 8 Gen3 |
| 内屏 | 6.8 寸 FHD+ AMOLED，120Hz LTPO |
| 外屏 | 3.6 寸 AMOLED，60Hz |
| 后置相机 | 50MP 主摄(OIS) + 12MP 超广角 |
| 前置相机 | 32MP（内屏打孔） |
| 电池 | 4300mAh |
| 充电 | 67W 有线 + 15W 无线 |
| 折叠厚度 | 15.8mm |
| 展开厚度 | 7.3mm |
| 重量 | 188g |

## 折叠屏使用技巧
- **悬停拍照**：半折叠状态下放在桌面上，解放双手进行自拍或延时摄影
- **外屏快捷回复**：在外屏上使用预设回复或语音输入快速回消息
- **双屏预览**：拍照时内外屏同时显示画面，被拍者也能看到构图
- **分屏多任务**：展开状态下支持上下分屏，边看视频边聊天

## 常见问题
**Q: 折叠屏耐用吗？铰链寿命多长？**
A: 通过 50 万次折叠测试（莱茵认证），正常使用 5 年以上无问题。

**Q: 折痕明显吗？**
A: 采用超薄柔性玻璃(UTG)，折痕相比竞品更浅，正常使用几乎不可见。

**Q: 外屏能运行所有应用吗？**
A: 主流应用（微信、抖音、支付宝、地图等）均已适配外屏。未适配应用可在设置中开启强制显示。',
    'https://picsum.photos/seed/phone-flip/400/400',
    'https://picsum.photos/seed/phone-flip-folded/400/400;https://picsum.photos/seed/phone-flip-open/400/400',
    92, 1, NOW(), 4999.00, 5999.00, 300, 2, 1, 1, NOW(), 1, NOW(), 1);

SET @spu_flip = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_flip, '星耀 Flip · 香槟粉 · 12+256GB', '颜色:香槟粉;内存:12GB;存储:256GB', '{"颜色":"香槟粉","内存":"12GB","存储":"256GB"}', 4999.00, 5499.00, 4000.00, 150, 10, 188, 'PH-FLIP-12-256-PK', 10, 1, NOW(), 1, NOW(), 1),
(@spu_flip, '星耀 Flip · 曜石黑 · 12+512GB', '颜色:曜石黑;内存:12GB;存储:512GB', '{"颜色":"曜石黑","内存":"12GB","存储":"512GB"}', 5999.00, 6499.00, 4800.00, 100, 10, 188, 'PH-FLIP-12-512-BK', 20, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 2, total_stock = 250 WHERE id = @spu_flip;

-- ============================================================
-- 2. 笔记本电脑 (category_id=4)
-- ============================================================

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('星耀 ThinkBook 16 商务本', 'Ultra 9 · 32GB · 2.5K · 1.8kg',
    4,
    '# 星耀 ThinkBook 16 商务本

## 产品概述
面向商务人士的高性能轻薄本。16 寸 2.5K 高分屏配合 Ultra 9 处理器，兼顾性能与便携。全金属机身仅重 1.8kg，是移动办公的理想选择。

## 核心参数
| 项目 | 规格 |
|------|------|
| 处理器 | Intel Core Ultra 9 285H |
| 显卡 | Intel Arc 集成显卡 |
| 内存 | 32GB LPDDR5x 7467MHz |
| 存储 | 1TB PCIe 4.0 SSD |
| 屏幕 | 16 寸 2560×1600 IPS，120Hz，100% sRGB |
| 电池 | 84Wh |
| 接口 | 2×Thunderbolt 4, 2×USB-A 3.2, HDMI 2.1, SD 卡槽, 3.5mm 耳机孔 |
| 重量 | 1.8kg |
| 厚度 | 16.9mm |
| 系统 | Windows 11 Pro |

## 适用场景
- 日常办公（Office、邮件、视频会议）
- 轻度设计和视频剪辑（PS、剪映）
- 编程开发（VS Code、Docker、虚拟机）
- 出差移动办公

## 常见问题
**Q: 续航时间多长？**
A: PCMark 10 办公场景测试约 12 小时，实际混合使用约 8-10 小时。

**Q: 支持外接几个显示器？**
A: 通过 Thunderbolt 4 可外接 2 台 4K@60Hz 显示器。

**Q: 可以加装内存和硬盘吗？**
A: 内存为板载不可升级。硬盘有一个空闲 M.2 2280 插槽可加装。',
    'https://picsum.photos/seed/laptop-thinkbook16/400/400',
    'https://picsum.photos/seed/laptop-thinkbook16-open/400/400',
    95, 1, NOW(), 6999.00, 7999.00, 300, 2, 1, 0, NOW(), 1, NOW(), 1);

SET @spu_tb16 = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_tb16, '星耀 ThinkBook 16 · 银色 · 32GB+1TB', '颜色:银色;内存:32GB;存储:1TB', '{"颜色":"银色","内存":"32GB","存储":"1TB"}', 6999.00, 7499.00, 5600.00, 150, 10, 1800, 'NB-TB16-32-1T-SL', 10, 1, NOW(), 1, NOW(), 1),
(@spu_tb16, '星耀 ThinkBook 16 · 深空灰 · 32GB+2TB', '颜色:深空灰;内存:32GB;存储:2TB', '{"颜色":"深空灰","内存":"32GB","存储":"2TB"}', 7999.00, 8499.00, 6400.00, 100, 10, 1800, 'NB-TB16-32-2T-GY', 20, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 2, total_stock = 250 WHERE id = @spu_tb16;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('星耀 Creator 14 设计师本', 'RTX 4070 · 3.2K OLED · 100% DCI-P3',
    4,
    '# 星耀 Creator 14 设计师本

## 产品概述
专为创意工作者打造的高性能笔记本。3.2K OLED 屏幕覆盖 100% DCI-P3 色域，每台出厂校色 Delta E < 1。RTX 4070 独显可胜任 3D 渲染、视频剪辑、AI 绘画等工作。

## 核心参数
| 项目 | 规格 |
|------|------|
| 处理器 | Intel Core Ultra 9 285H |
| 显卡 | NVIDIA GeForce RTX 4070 8GB (100W) |
| 内存 | 64GB LPDDR5x |
| 存储 | 2TB PCIe 4.0 SSD |
| 屏幕 | 14.5 寸 3200×2000 OLED，120Hz，100% DCI-P3，HDR500 |
| 电池 | 76Wh |
| 重量 | 1.65kg |
| 接口 | 2×Thunderbolt 4, 1×USB-A, HDMI 2.1, SD Express 读卡器 |

## 创意工作流
- **视频剪辑**：Davinci Resolve 中 4K H.265 时间线流畅回放，导出速度提升 40%
- **3D 渲染**：Blender 中 BMW 场景渲染仅需 2 分 15 秒
- **AI 绘画**：Stable Diffusion 本地运行，512×512 出图约 3 秒/张
- **平面设计**：Photoshop 处理 1GB PSD 文件无卡顿

## 常见问题
**Q: OLED 屏幕会烧屏吗？**
A: 内置像素位移、屏幕保护等防烧屏技术。正常使用 3 年内出现烧屏可免费更换屏幕。

**Q: 风扇噪音大吗？**
A: 办公场景约 28dB（几乎无声），满载渲染时约 45dB（可接受）。可在控制中心切换安静模式。

**Q: 可以打游戏吗？**
A: RTX 4070 移动端可以畅玩 3A 大作。赛博朋克 2077 2.5K 中高画质约 70fps。',
    'https://picsum.photos/seed/laptop-creator14/400/400',
    'https://picsum.photos/seed/laptop-creator14-screen/400/400',
    98, 1, NOW(), 12999.00, 14999.00, 150, 2, 1, 1, NOW(), 1, NOW(), 1);

SET @spu_creator = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_creator, '星耀 Creator 14 · 深空灰 · 64GB+2TB', '颜色:深空灰;内存:64GB;存储:2TB', '{"颜色":"深空灰","内存":"64GB","存储":"2TB"}', 12999.00, 13999.00, 10400.00, 80, 5, 1650, 'NB-CR14-64-2T-GY', 10, 1, NOW(), 1, NOW(), 1),
(@spu_creator, '星耀 Creator 14 · 月岩白 · 64GB+4TB', '颜色:月岩白;内存:64GB;存储:4TB', '{"颜色":"月岩白","内存":"64GB","存储":"4TB"}', 14999.00, 15999.00, 12000.00, 50, 5, 1650, 'NB-CR14-64-4T-WH', 20, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 2, total_stock = 130 WHERE id = @spu_creator;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('星耀 Book 14 学生本', 'i5-13500H · 16GB · 2.2K · 高性价比',
    4,
    '# 星耀 Book 14 学生本

## 产品概述
面向大学生和年轻用户的入门全能本。2.2K 高分屏、标压处理器、全金属机身，在这个价位提供了越级体验。适合日常学习、轻度娱乐。

## 核心参数
| 项目 | 规格 |
|------|------|
| 处理器 | Intel Core i5-13500H |
| 显卡 | Intel Iris Xe |
| 内存 | 16GB LPDDR5 5200MHz |
| 存储 | 512GB PCIe 4.0 SSD |
| 屏幕 | 14 寸 2240×1400 IPS，60Hz，100% sRGB |
| 电池 | 56Wh |
| 重量 | 1.45kg |
| 接口 | 2×USB-C（全功能）, 1×USB-A, HDMI 1.4, 3.5mm |

## 适合人群
- 大学生（写论文、做 PPT、上网课）
- 轻度编程（Python、Java、前端开发）
- 日常影音娱乐
- 预算 4000-5000 元

## 常见问题
**Q: 能不能玩 LOL、原神？**
A: LOL 全高画质 100fps+，原神低画质 45fps 左右。轻度游戏没问题，3A 大作不推荐。

**Q: 扩展性如何？**
A: 内存板载不可升级，硬盘有一个空闲 M.2 插槽可加装。购买时建议一步到位。',
    'https://picsum.photos/seed/laptop-book14/400/400',
    'https://picsum.photos/seed/laptop-book14-side/400/400',
    75, 1, NOW(), 4299.00, 4999.00, 600, 2, 0, 0, NOW(), 1, NOW(), 1);

SET @spu_book14 = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_book14, '星耀 Book 14 · 星空银 · 16GB+512GB', '颜色:星空银;内存:16GB;存储:512GB', '{"颜色":"星空银","内存":"16GB","存储":"512GB"}', 4299.00, 4699.00, 3400.00, 300, 15, 1450, 'NB-BK14-16-512-SL', 10, 1, NOW(), 1, NOW(), 1),
(@spu_book14, '星耀 Book 14 · 雾霾蓝 · 16GB+1TB', '颜色:雾霾蓝;内存:16GB;存储:1TB', '{"颜色":"雾霾蓝","内存":"16GB","存储":"1TB"}', 4999.00, 5299.00, 4000.00, 200, 15, 1450, 'NB-BK14-16-1T-BL', 20, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 2, total_stock = 500 WHERE id = @spu_book14;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('星耀 GamePro 16 游戏本', 'RTX 4060 · i9-14900HX · 240Hz 电竞屏',
    4,
    '# 星耀 GamePro 16 游戏本

## 产品概述
硬核游戏本，搭载 i9-14900HX + RTX 4060 满血版（140W）。16 寸 2.5K 240Hz 电竞屏，G-SYNC 加持，为电竞玩家提供极致游戏体验。

## 核心参数
| 项目 | 规格 |
|------|------|
| 处理器 | Intel Core i9-14900HX（24核32线程） |
| 显卡 | NVIDIA RTX 4060 8GB 140W 满血 |
| 内存 | 32GB DDR5 5600MHz（双通道，可升级） |
| 存储 | 1TB PCIe 4.0 SSD（双 M.2 插槽） |
| 屏幕 | 16 寸 2560×1600 IPS，240Hz，100% sRGB，G-SYNC |
| 电池 | 90Wh |
| 重量 | 2.4kg |
| 散热 | 双风扇 + 5 热管 + 液金导热 |

## 游戏性能
| 游戏 | 画质 | 分辨率 | 帧率 |
|------|------|--------|------|
| 英雄联盟 | 最高 | 2.5K | 280fps+ |
| CS2 | 高 | 2.5K | 220fps+ |
| 赛博朋克 2077 | 光追中 | 2.5K DLSS | 90fps |
| 黑神话：悟空 | 高 | 2.5K DLSS | 85fps |
| 绝地求生 | 三极致 | 2.5K | 160fps+ |

## 常见问题
**Q: 温度高吗？**
A: 双烤（CPU+GPU 满载）CPU 85°C、GPU 78°C，属于正常游戏本温度。

**Q: 可以自己加内存和硬盘吗？**
A: 支持。有两个 DDR5 SODIMM 插槽（最大 64GB）和两个 M.2 2280 插槽。自行拆机不影响保修。

**Q: 续航怎么样？**
A: 办公场景约 5 小时，游戏场景需插电使用。游戏本续航普遍较短，建议随身携带适配器。',
    'https://picsum.photos/seed/laptop-gamepro16/400/400',
    'https://picsum.photos/seed/laptop-gamepro16-rgb/400/400',
    90, 1, NOW(), 9499.00, 10999.00, 200, 2, 1, 1, NOW(), 1, NOW(), 1);

SET @spu_gamepro = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_gamepro, '星耀 GamePro 16 · 暗夜黑 · 32GB+1TB', '颜色:暗夜黑;内存:32GB;存储:1TB', '{"颜色":"暗夜黑","内存":"32GB","存储":"1TB"}', 9499.00, 9999.00, 7600.00, 120, 10, 2400, 'NB-GP16-32-1T-BK', 10, 1, NOW(), 1, NOW(), 1),
(@spu_gamepro, '星耀 GamePro 16 · 星际灰 · 32GB+2TB', '颜色:星际灰;内存:32GB;存储:2TB', '{"颜色":"星际灰","内存":"32GB","存储":"2TB"}', 10999.00, 11499.00, 8800.00, 60, 10, 2400, 'NB-GP16-32-2T-GY', 20, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 2, total_stock = 180 WHERE id = @spu_gamepro;

-- ============================================================
-- 3. 智能影音 (category_id=6)
-- ============================================================

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('逸彩 VividBar Ultra 旗舰回音壁', '11.1.4声道 · Dolby Atmos · 无线后环绕',
    6,
    '# 逸彩 VividBar Ultra 旗舰回音壁

## 产品概述
逸彩旗舰级回音壁系统，包含条形音箱 + 无线低音炮 + 一对无线后环绕音箱。11.1.4 物理声道配置，支持 Dolby Atmos 和 DTS:X，实现真正的三维环绕声。

## 核心参数
| 项目 | 规格 |
|------|------|
| 声道 | 11.1.4（条形音箱 7.1.4 + 低音炮 + 后环绕×2） |
| 总功率 | 880W RMS |
| 低音炮 | 10 寸无线低音炮，200W |
| 连接 | HDMI eARC, 2×HDMI In, 光纤, Bluetooth 5.3, Wi-Fi |
| 音频格式 | Dolby Atmos, DTS:X, Dolby TrueHD, LPCM 7.1 |
| 串流 | AirPlay 2, Spotify Connect, Chromecast |

## 场景模式
- **电影模式**：增强中置声道人声清晰度，低音深沉有力
- **音乐模式**：立体声分离度提升，适合音乐欣赏
- **游戏模式**：低延迟传输，支持 VRR/ALLM 直通
- **夜间模式**：压缩动态范围，不影响家人休息

## 常见问题
**Q: 需要提前布线吗？**
A: 不需要。后环绕和低音炮都是无线连接，只需给条形音箱接一根 HDMI 线和电源即可。

**Q: 和电视自带的音响比提升多大？**
A: 天壤之别。电视音响通常 10-20W，这款 880W。你会听到之前从未注意到的音效细节。

**Q: 适合多大的房间？**
A: 20-40 平米客厅效果最佳。支持房间校准功能，可自动优化各声道延迟和音量。',
    'https://picsum.photos/seed/soundbar-ultra/400/400',
    'https://picsum.photos/seed/soundbar-ultra-setup/400/400;https://picsum.photos/seed/soundbar-ultra-rear/400/400',
    95, 1, NOW(), 6999.00, 6999.00, 100, 1, 1, 0, NOW(), 1, NOW(), 1);

SET @spu_soundbar = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_soundbar, '逸彩 VividBar Ultra · 标准套装', '规格:标准套装', '{"规格":"标准套装"}', 6999.00, 7999.00, 5500.00, 100, 5, 15000, 'SPK-VBU-001', 10, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 1, total_stock = 100 WHERE id = @spu_soundbar;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('逸彩 VividStudio Pro 监听音箱', '5寸低音 · 独立功放 · XLR平衡输入',
    6,
    '# 逸彩 VividStudio Pro 监听音箱

## 产品概述
为音乐制作人和 Hi-Fi 发烧友打造的有源近场监听音箱。5 寸凯夫拉低音单元 + 1 寸丝膜高音，D 类独立功放驱动。XLR/TRS 平衡输入，适合录音棚和桌面 Hi-Fi。

## 核心参数
| 项目 | 规格 |
|------|------|
| 类型 | 2 分频有源监听音箱 |
| 低音单元 | 5.25 寸凯夫拉复合振膜 |
| 高音单元 | 1 寸丝膜球顶 |
| 功放 | D 类，低音 50W + 高音 30W（每只） |
| 频率响应 | 45Hz - 40kHz（±3dB） |
| 输入 | XLR 平衡, TRS 平衡, RCA 非平衡 |
| 尺寸 | 180×280×240mm（每只） |
| 重量 | 6.5kg（每只） |

## 使用建议
- **摆位**：高音单元与耳朵齐平，两只音箱与听音位置成等边三角形
- **离墙距离**：后倒相孔设计，建议离墙 30cm 以上
- **脚架**：推荐搭配 ISO 避震脚架，减少桌面共振
- **煲机**：建议 50 小时以上，声音会逐渐宽松自然

## 常见问题
**Q: 需要配功放吗？**
A: 不需要。这是有源音箱，内置独立功放，直接接音源即可使用。

**Q: 和普通电脑音箱有什么区别？**
A: 监听音箱追求声音的准确还原，没有音染。你能听到音乐本来的样子，而不是被美化的版本。特别适合音乐制作和 Hi-Fi 欣赏。

**Q: 可以连接电视吗？**
A: 电视通常没有 XLR/TRS 输出。可购买 HDMI 音频分离器或使用 3.5mm 转 RCA 线（音质会打折扣）。',
    'https://picsum.photos/seed/speaker-studio/400/400',
    'https://picsum.photos/seed/speaker-studio-back/400/400',
    80, 1, NOW(), 2999.00, 2999.00, 200, 1, 0, 0, NOW(), 1, NOW(), 1);

SET @spu_studio = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_studio, '逸彩 VividStudio Pro · 对装', '规格:一对', '{"规格":"一对"}', 2999.00, 3499.00, 2300.00, 200, 10, 13000, 'SPK-VSP-001', 10, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 1, total_stock = 200 WHERE id = @spu_studio;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('逸彩 VividBeam 4K 投影仪', '4K HDR · 2800ANSI流明 · 自动对焦梯形校正',
    6,
    '# 逸彩 VividBeam 4K 投影仪

## 产品概述
家用 4K 智能投影仪，2800 ANSI 流明高亮度，白天也能看清画面。内置智能系统，支持自动对焦、自动梯形校正、自动避障和自动入幕，开机即用。

## 核心参数
| 项目 | 规格 |
|------|------|
| 分辨率 | 3840×2160（真4K，0.47寸 DMD XPR） |
| 亮度 | 2800 ANSI 流明 |
| 对比度 | 10000:1 |
| 投射比 | 1.2:1（2.66 米投 100 寸） |
| 处理器 | MT9679 |
| 内存/存储 | 4GB+64GB |
| 系统 | Android TV 12 |
| 音响 | 2×12W 哈曼卡顿调音 |
| 接口 | 2×HDMI 2.1, 2×USB, 光纤, 3.5mm, RJ45 |

## 投影距离参考
| 屏幕尺寸 | 投影距离 |
|----------|----------|
| 80 寸 | 约 2.1m |
| 100 寸 | 约 2.7m |
| 120 寸 | 约 3.2m |
| 150 寸 | 约 4.0m |

## 常见问题
**Q: 白天能看吗？**
A: 2800 ANSI 流明在拉窗帘环境下效果不错。如果经常白天使用，建议搭配抗光幕布。

**Q: 灯泡寿命多长？**
A: LED 光源，标称寿命 30000 小时。按每天 4 小时可用 20 年，无需更换灯泡。

**Q: 可以吊装吗？**
A: 支持正装、吊装、背投、吊装背投四种安装方式。底部有标准 1/4 寸螺孔。',
    'https://picsum.photos/seed/projector-beam/400/400',
    'https://picsum.photos/seed/projector-beam-room/400/400;https://picsum.photos/seed/projector-beam-remote/400/400',
    88, 1, NOW(), 5999.00, 5999.00, 150, 1, 1, 0, NOW(), 1, NOW(), 1);

SET @spu_projector = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_projector, '逸彩 VividBeam 4K · 标准版', '规格:标准版', '{"规格":"标准版"}', 5999.00, 6999.00, 4700.00, 150, 5, 5200, 'PJ-VBM-001', 10, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 1, total_stock = 150 WHERE id = @spu_projector;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('逸彩 VividMix 直播声卡', '专业DSP · 48V幻象电源 · 实时监聽',
    6,
    '# 逸彩 VividMix 直播声卡

## 产品概述
专业级外置直播声卡，内置 32 位 DSP 芯片。支持 48V 幻象电源驱动电容麦克风，独立控制监听、伴奏、直播音量。内置多种音效和一键变声，适合直播、录音、K歌。

## 核心参数
| 项目 | 规格 |
|------|------|
| 采样率 | 24bit/192kHz |
| 输入 | 2×XLR/TRS 复合接口, 3.5mm 伴奏输入 |
| 输出 | 6.35mm 监听, 3.5mm 耳机, RCA 线路输出 |
| 幻象电源 | 48V（独立开关） |
| 连接 | USB-C, Bluetooth 5.0 伴奏输入 |
| 电池 | 内置 5000mAh（约 8 小时续航） |
| 音效 | 混响、回声、变声、掌声、哄笑等 15 种 |

## 适用场景
- **直播带货**：边播边放背景音乐，一键音效活跃气氛
- **唱歌/K歌**：专业混响效果，声音更饱满
- **播客录制**：双人 XLR 输入，适合访谈节目
- **游戏直播**：低延迟监听，游戏音和人声分离控制

## 常见问题
**Q: 需要安装驱动吗？**
A: Windows 需安装 ASIO 驱动（官网下载），Mac 即插即用。

**Q: 手机的伴奏怎么输入？**
A: 可以蓝牙连接手机播放伴奏，也可以用 3.5mm 音频线连接。',
    'https://picsum.photos/seed/audio-mixer/400/400',
    'https://picsum.photos/seed/audio-mixer-knobs/400/400',
    75, 1, NOW(), 899.00, 899.00, 500, 1, 0, 0, NOW(), 1, NOW(), 1);

SET @spu_mixer = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_mixer, '逸彩 VividMix · 标准版', '规格:标准版', '{"规格":"标准版"}', 899.00, 1099.00, 650.00, 500, 20, 450, 'AUD-VMX-001', 10, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 1, total_stock = 500 WHERE id = @spu_mixer;

-- ============================================================
-- 4. 智能穿戴 (category_id=8)
-- ============================================================

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('星耀 Watch 4 Ultra 旗舰手表', '钛合金 · 蓝宝石玻璃 · 双频GPS · 14天续航',
    8,
    '# 星耀 Watch 4 Ultra 旗舰手表

## 产品概述
星耀旗舰智能手表，采用 TC4 钛合金表壳和蓝宝石玻璃表镜。支持 100+ 运动模式、双频五星定位、ECG 心电图、血氧监测。14 天超长续航，适合户外运动和商务佩戴。

## 核心参数
| 项目 | 规格 |
|------|------|
| 表壳 | TC4 钛合金，49mm |
| 屏幕 | 1.5 寸 LTPO AMOLED，2000nit 峰值亮度 |
| 表镜 | 蓝宝石玻璃 |
| 定位 | 双频五星（GPS+北斗+GLONASS+Galileo+QZSS） |
| 传感器 | 心率(8通道)、血氧、ECG、体温、气压、地磁 |
| 防水 | 10ATM（100米）+ IP68 |
| 电池 | 590mAh（日常 14 天，重度 8 天） |
| 系统 | Stellar Wear OS |

## 运动模式
- **跑步**：支持步频、步幅、触地时间、垂直振幅等高级跑步数据
- **游泳**：自动识别泳姿，记录 SWOLF 效率值
- **滑雪**：自动识别滑行/乘坐缆车，记录滑行轨迹和垂直落差
- **高尔夫**：内置全球 42000+ 球场地图，测距到果岭前后沿
- **登山**：气压计实时海拔，风暴预警

## 健康监测
- **ECG 心电图**：30 秒快速测量，可导出 PDF 报告
- **睡眠分析**：深睡、浅睡、REM、清醒时间，睡眠呼吸质量
- **压力监测**：全天候 HRV 监测，呼吸训练引导
- **血氧**：24 小时连续监测，低血氧提醒

## 常见问题
**Q: 能脱离手机独立使用吗？**
A: 支持 eSIM 独立通话和上网（一号双终端），不带手机也能接打电话、听歌、导航。

**Q: 兼容 iPhone 吗？**
A: 完全兼容。iPhone 上安装"星耀健康"App 即可使用全部功能。

**Q: 表带可以换吗？**
A: 采用 22mm 快拆表带，兼容所有标准 22mm 表带。',
    'https://picsum.photos/seed/watch-ultra/400/400',
    'https://picsum.photos/seed/watch-ultra-back/400/400;https://picsum.photos/seed/watch-ultra-wrist/400/400',
    95, 1, NOW(), 3299.00, 3299.00, 200, 1, 1, 1, NOW(), 1, NOW(), 1);

SET @spu_watch4u = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_watch4u, '星耀 Watch 4 Ultra · 钛金灰', '颜色:钛金灰', '{"颜色":"钛金灰"}', 3299.00, 3699.00, 2600.00, 200, 10, 61, 'WT-W4U-GY', 10, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 1, total_stock = 200 WHERE id = @spu_watch4u;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('星耀 Band 9 智能手环', '1.74寸AMOLED · 120种运动 · 磁吸快充',
    8,
    '# 星耀 Band 9 智能手环

## 产品概述
轻巧时尚的智能手环，9.8mm 超薄机身仅重 18g。1.74 寸方屏大屏，120 种运动模式，支持血氧、心率、睡眠全天候监测。磁吸快充 15 分钟可用一整天。

## 核心参数
| 项目 | 规格 |
|------|------|
| 屏幕 | 1.74 寸 AMOLED 方屏，336×480 |
| 亮度 | 最高 800nit |
| 传感器 | 心率、血氧、加速度计、陀螺仪 |
| 防水 | 5ATM（50米） |
| 电池 | 300mAh（日常 10 天，重度 6 天） |
| 充电 | 磁吸快充，15 分钟充 50% |
| 重量 | 18g（不含表带） |
| 系统 | Stellar Lite OS |

## 适用人群
- 运动爱好者（跑步、骑行、游泳数据记录）
- 睡眠质量关注者（详细睡眠分析）
- 预算有限的智能穿戴入门用户
- 学生党（消息提醒、课程提醒）

## 常见问题
**Q: 和星耀 Watch 系列有什么主要区别？**
A: Band 9 没有 GPS、ECG、eSIM 功能，屏幕更小。但续航更长、价格更低、佩戴更轻便。

**Q: 能不能接打电话？**
A: 支持蓝牙通话，但需要手机在附近。没有 eSIM 独立通话功能。',
    'https://picsum.photos/seed/band9/400/400',
    'https://picsum.photos/seed/band9-black/400/400;https://picsum.photos/seed/band9-colors/400/400',
    78, 1, NOW(), 329.00, 379.00, 1000, 3, 0, 0, NOW(), 1, NOW(), 1);

SET @spu_band9 = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_band9, '星耀 Band 9 · 午夜黑', '颜色:午夜黑', '{"颜色":"午夜黑"}', 329.00, 349.00, 220.00, 400, 20, 18, 'BD-B9-BK', 10, 1, NOW(), 1, NOW(), 1),
(@spu_band9, '星耀 Band 9 · 星云粉', '颜色:星云粉', '{"颜色":"星云粉"}', 349.00, 369.00, 230.00, 300, 20, 18, 'BD-B9-PK', 20, 1, NOW(), 1, NOW(), 1),
(@spu_band9, '星耀 Band 9 · 深海蓝', '颜色:深海蓝', '{"颜色":"深海蓝"}', 379.00, 399.00, 250.00, 250, 20, 18, 'BD-B9-BL', 30, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 3, total_stock = 950 WHERE id = @spu_band9;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('星耀 Buds Pro 3 降噪耳机', '自适应降噪 · 空间音频 · 36小时续航',
    8,
    '# 星耀 Buds Pro 3 降噪耳机

## 产品概述
旗舰真无线降噪耳机，自适应 ANC 降噪深度达 55dB。支持空间音频头部追踪，Hi-Res 认证 LDAC 高清传输。IP54 防水防汗，适合运动和日常通勤。

## 核心参数
| 项目 | 规格 |
|------|------|
| 降噪 | 自适应 ANC，最大 55dB |
| 驱动单元 | 11mm 镀铍振膜 + 6mm 高音单元（同轴双单元） |
| 蓝牙 | Bluetooth 5.4，支持 LE Audio |
| 音频编码 | LDAC, AAC, SBC, LC3 |
| 续航 | 耳机 9 小时 + 充电盒 27 小时（共 36 小时，ANC 开） |
| 充电 | USB-C + Qi 无线充电 |
| 防水 | IP54（耳机） |
| 重量 | 单耳 5.2g |

## 降噪模式
- **强降噪**：地铁、飞机等嘈杂环境
- **均衡降噪**：办公室、咖啡馆日常使用
- **通透模式**：不错过外界声音，与人交谈无需摘下耳机
- **自适应**：根据环境噪音自动切换降噪强度

## 常见问题
**Q: 连接 iPhone 能用 LDAC 吗？**
A: iPhone 不支持 LDAC，会自动降级为 AAC。Android 手机可开启 LDAC。

**Q: 能同时连接两台设备吗？**
A: 支持蓝牙双设备连接。在手机和电脑间自动切换。

**Q: 降噪对耳朵有影响吗？**
A: ANC 降噪通过产生反向声波抵消噪音，对耳朵没有伤害。长时间佩戴建议开启通透模式让耳朵休息。',
    'https://picsum.photos/seed/buds-pro3/400/400',
    'https://picsum.photos/seed/buds-pro3-case/400/400',
    85, 1, NOW(), 1299.00, 1499.00, 400, 2, 1, 0, NOW(), 1, NOW(), 1);

SET @spu_buds3 = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_buds3, '星耀 Buds Pro 3 · 陶瓷白', '颜色:陶瓷白', '{"颜色":"陶瓷白"}', 1299.00, 1399.00, 950.00, 200, 10, 52, 'BD-BP3-WH', 10, 1, NOW(), 1, NOW(), 1),
(@spu_buds3, '星耀 Buds Pro 3 · 石墨黑', '颜色:石墨黑', '{"颜色":"石墨黑"}', 1499.00, 1599.00, 1100.00, 150, 10, 52, 'BD-BP3-BK', 20, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 2, total_stock = 350 WHERE id = @spu_buds3;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('星耀 AR Glasses Air 智能眼镜', 'Micro-OLED · 201寸巨幕 · 49g超轻',
    8,
    '# 星耀 AR Glasses Air 智能眼镜

## 产品概述
新一代消费级 AR 智能眼镜。仅重 49g，佩戴如普通眼镜般舒适。Micro-OLED 屏幕投射等效 4 米 201 寸巨幕。支持连接手机、电脑、游戏机，随时随地享受大屏体验。

## 核心参数
| 项目 | 规格 |
|------|------|
| 显示 | 双 Micro-OLED，1920×1080 每眼 |
| FOV | 46° |
| 等效屏幕 | 4 米距离 201 寸 |
| 亮度 | 入眼 600nit |
| 刷新率 | 120Hz |
| 连接 | USB-C（DP Alt Mode） |
| 音频 | 双扬声器 + 双麦克风 |
| 重量 | 49g |
| 近视适配 | 支持 0-600° 屈光度调节 |

## 使用场景
- **移动办公**：连接笔记本，获得超大虚拟屏幕，在咖啡馆也能高效工作
- **飞机高铁**：在狭小空间享受影院级观影体验
- **游戏**：连接 Switch/Steam Deck/ROG Ally，201 寸巨幕沉浸游戏
- **航拍**：连接无人机，第一视角飞行

## 常见问题
**Q: 需要什么手机？**
A: 需要支持 DP 视频输出的 USB-C 接口。大部分旗舰 Android 手机、iPhone 15/16 系列、iPad Pro 均支持。

**Q: 戴近视眼镜能用吗？**
A: 支持 0-600° 屈光度独立调节（左右眼分开），无需戴近视眼镜。

**Q: 长时间佩戴会晕吗？**
A: 120Hz 高刷新率 + 低延迟显示，大多数用户不会感到不适。建议首次使用每 30 分钟休息一下。',
    'https://picsum.photos/seed/glass-ar/400/400',
    'https://picsum.photos/seed/glass-ar-wear/400/400',
    80, 1, NOW(), 2799.00, 2799.00, 100, 1, 1, 0, NOW(), 1, NOW(), 1);

SET @spu_glass = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_glass, '星耀 AR Glasses Air · 墨镜灰', '颜色:墨镜灰', '{"颜色":"墨镜灰"}', 2799.00, 2999.00, 2100.00, 100, 5, 49, 'GL-AR-GY', 10, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 1, total_stock = 100 WHERE id = @spu_glass;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('星耀 SmartRing Nova 智能戒指', '钛合金 · 健康监测 · 7天续航 · 3g超轻',
    8,
    '# 星耀 SmartRing Nova 智能戒指

## 产品概述
星耀首款智能戒指，采用医用级钛合金材质。内置心率、血氧、体温传感器，全天候无感佩戴。3g 超轻设计，适合睡眠监测和不习惯戴手表的用户。

## 核心参数
| 项目 | 规格 |
|------|------|
| 材质 | TC4 钛合金 + 陶瓷内圈 |
| 传感器 | PPG 心率、SpO2 血氧、皮肤温度、加速度计 |
| 防水 | IP68 |
| 电池 | 22mAh（7 天续航） |
| 充电 | 无线充电底座，60 分钟充满 |
| 重量 | 3-4g（取决于尺码） |
| 尺寸 | 7/8/9/10/11/12 号（美码） |

## 健康功能
- **睡眠监测**：戒指是睡眠监测的理想形态，比手表更无感
- **静息心率**：24 小时连续监测，异常心率提醒
- **血氧**：夜间自动监测，低血氧预警
- **体温趋势**：基础体温变化跟踪，女性生理周期预测
- **活动记录**：步数、卡路里、活动时长

## 常见问题
**Q: 尺码怎么选？**
A: 购买后会邮寄一套试戴样品（7-12 号塑料环），确定尺码后再发货。建议戴在食指或中指。

**Q: 戒指会被刮花吗？**
A: 钛合金硬度高，日常使用不易刮花。但避免与钻石、砂纸等硬物接触。

**Q: 能戴着游泳吗？**
A: IP68 防水，可以戴着洗手、游泳。但潜水和热水浴不建议。',
    'https://picsum.photos/seed/ring-nova/400/400',
    'https://picsum.photos/seed/ring-nova-box/400/400',
    72, 1, NOW(), 999.00, 999.00, 300, 1, 1, 0, NOW(), 1, NOW(), 1);

SET @spu_ring = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_ring, '星耀 SmartRing Nova · 钛金原色', '颜色:钛金原色', '{"颜色":"钛金原色"}', 999.00, 1199.00, 750.00, 300, 15, 3, 'RG-NOVA-TI', 10, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 1, total_stock = 300 WHERE id = @spu_ring;

-- ============================================================
-- 5. 家用电冰箱 (category_id=2)
-- ============================================================

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('极净 FreshBox 500 十字对开冰箱', '500L · 双变频 · 风冷无霜 · 净味杀菌',
    2,
    '# 极净 FreshBox 500 十字对开冰箱

## 产品概述
500 升大容量十字对开门冰箱。上层对开冷藏（310L），下层双抽屉冷冻（190L）。双变频压缩机+风机，一级能效，日耗电仅 0.85 度。内置铂金净味模块，有效去除异味。

## 核心参数
| 项目 | 规格 |
|------|------|
| 总容积 | 500L（冷藏 310L + 冷冻 190L） |
| 制冷方式 | 风冷无霜 |
| 能效等级 | 一级能效 |
| 日耗电 | 0.85kWh/24h |
| 噪音 | 36dB(A) |
| 压缩机 | 变频 |
| 尺寸 | 833×650×1810mm |
| 净重 | 88kg |
| 门体 | 十字对开（PCM 彩涂板） |

## 保鲜技术
- **铂金净味**：内置铂金离子模块，有效分解异味分子，保持冰箱内空气清新
- **360° 环绕风冷**：冷气均匀分布，食材不直吹、不风干
- **独立变温抽屉**：-7°C 软冷冻，肉类无需解冻即可切
- **果蔬保湿区**：90% 以上湿度，绿叶菜一周不蔫

## 使用建议
- **首次通电**：静置 4 小时后通电，空载运行 2-3 小时再放食物
- **温度设置**：冷藏 3-5°C、冷冻 -18°C 为推荐值
- **清洁保养**：每季度清理一次门封条，半年清理一次冷凝器（后盖）

## 常见问题
**Q: 需要手动除霜吗？**
A: 不需要。风冷无霜设计，自动除霜，永远不会结冰。

**Q: 声音大吗？**
A: 36dB 相当于图书馆环境，非常安静。偶尔有流水声（冷媒流动）属正常现象。

**Q: 停电了怎么办？**
A: 停电 4 小时内不要开门，食物可保持新鲜。超过 4 小时建议转移或使用冰袋。',
    'https://picsum.photos/seed/fridge-500L/400/400',
    'https://picsum.photos/seed/fridge-500L-open/400/400;https://picsum.photos/seed/fridge-500L-interior/400/400',
    90, 1, NOW(), 4599.00, 5599.00, 300, 2, 1, 1, NOW(), 1, NOW(), 1);

SET @spu_fridge500 = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_fridge500, '极净 FreshBox 500 · 星云灰', '颜色:星云灰', '{"颜色":"星云灰"}', 4599.00, 4999.00, 3600.00, 150, 10, 88000, 'RF-FB500-GY', 10, 1, NOW(), 1, NOW(), 1),
(@spu_fridge500, '极净 FreshBox 500 · 珍珠白', '颜色:珍珠白', '{"颜色":"珍珠白"}', 5599.00, 5999.00, 4400.00, 100, 10, 88000, 'RF-FB500-WH', 20, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 2, total_stock = 250 WHERE id = @spu_fridge500;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('极净 MiniBar 120 小冰箱', '120L · 静音 · 宿舍/办公室/卧室',
    2,
    '# 极净 MiniBar 120 小冰箱

## 产品概述
120 升小型双门冰箱，专为单身公寓、学生宿舍、办公室设计。28dB 超静音运行，不会打扰睡眠和工作。一级能效，年耗电不到 200 度。

## 核心参数
| 项目 | 规格 |
|------|------|
| 总容积 | 120L（冷藏 85L + 冷冻 35L） |
| 制冷方式 | 直冷 |
| 能效等级 | 一级能效 |
| 日耗电 | 0.48kWh/24h |
| 噪音 | 28dB(A) |
| 尺寸 | 480×500×1150mm |
| 净重 | 28kg |

## 适用场景
- **学生宿舍**：放饮料、水果、护肤品
- **单身公寓**：1-2 人日常食材存储
- **办公室**：员工午餐、饮料冷藏
- **卧室**：夜间饮品、面膜冷藏

## 常见问题
**Q: 需要手动除霜吗？**
A: 冷冻室需要每 3-6 个月手动除霜一次（结霜超过 5mm 时）。冷藏室自动除霜。

**Q: 能放下 2L 大瓶饮料吗？**
A: 门搁架可以放 2L 可乐瓶，冷藏室层板可以放下 12 罐易拉罐。',
    'https://picsum.photos/seed/fridge-mini120/400/400',
    'https://picsum.photos/seed/fridge-mini120-open/400/400',
    68, 1, NOW(), 999.00, 1299.00, 800, 2, 0, 0, NOW(), 1, NOW(), 1);

SET @spu_minibar = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_minibar, '极净 MiniBar 120 · 白色', '颜色:白色', '{"颜色":"白色"}', 999.00, 1099.00, 750.00, 400, 20, 28000, 'RF-MB120-WH', 10, 1, NOW(), 1, NOW(), 1),
(@spu_minibar, '极净 MiniBar 120 · 薄荷绿', '颜色:薄荷绿', '{"颜色":"薄荷绿"}', 1299.00, 1399.00, 980.00, 300, 20, 28000, 'RF-MB120-GN', 20, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 2, total_stock = 700 WHERE id = @spu_minibar;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('极净 CoolPro 650 法式四门冰箱', '650L · 双系统 · 独立制冰 · 全空间净化',
    2,
    '# 极净 CoolPro 650 法式四门冰箱

## 产品概述
旗舰级法式四门冰箱，650 升超大容量。双系统独立循环，冷藏冷冻不串味。内置自动制冰机和独立变温空间。全空间 UV 杀菌净化，食材持久保鲜。

## 核心参数
| 项目 | 规格 |
|------|------|
| 总容积 | 650L（冷藏 420L + 冷冻 230L） |
| 制冷方式 | 风冷无霜，双系统双循环 |
| 制冰 | 自动制冰机（内置水箱，无需外接水管） |
| 能效 | 一级能效 |
| 日耗电 | 1.05kWh/24h |
| 噪音 | 38dB(A) |
| 尺寸 | 910×710×1830mm |
| 净重 | 115kg |

## 特色功能
- **双系统循环**：冷藏室和冷冻室各自独立蒸发器和风机，食物不串味
- **自动制冰**：内置 2L 水箱，一次制冰约 40 块，旋转取出
- **全空间 UV 净化**：冷藏和冷冻均配备 UV-LED 杀菌模块
- **珍品专区**：适合存放高档茶叶、干贝、虫草等，湿度控制在 45% 左右

## 常见问题
**Q: 双系统和双变频有什么区别？**
A: 双系统指冷藏和冷冻各自独立的制冷回路（真正不串味）。双变频指压缩机和风机都是变频的（节能静音）。这歀冰箱两者都有。

**Q: 制冰机需要清洗吗？**
A: 建议每月清洗一次水箱和冰格。长时间不用时清空水箱，运行"制冰机清洁"模式。',
    'https://picsum.photos/seed/fridge-650L/400/400',
    'https://picsum.photos/seed/fridge-650L-open/400/400',
    98, 1, NOW(), 8999.00, 9999.00, 100, 1, 1, 1, NOW(), 1, NOW(), 1);

SET @spu_coolpro = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_coolpro, '极净 CoolPro 650 · 星岩灰', '颜色:星岩灰', '{"颜色":"星岩灰"}', 8999.00, 9999.00, 7200.00, 100, 5, 115000, 'RF-CP650-GY', 10, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 1, total_stock = 100 WHERE id = @spu_coolpro;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('极净 IceBar 200 冰吧/红酒柜', '200L · 双温区 · 恒温恒湿 · UV杀菌',
    2,
    '# 极净 IceBar 200 冰吧/红酒柜

## 产品概述
家用冰吧/红酒柜二合一。左侧冷藏区（2-8°C）适合存放饮料、水果、护肤品；右侧红酒区（10-18°C 可调）恒温恒湿，是红酒爱好者的理想选择。

## 核心参数
| 项目 | 规格 |
|------|------|
| 总容积 | 200L（冷藏 120L + 红酒 80L） |
| 温区 | 双温区独立控温 |
| 红酒区温度 | 10-18°C 可调 |
| 湿度 | 55%-75% 自动恒湿 |
| 层架 | 5 层榉木酒架 + 3 层钢化玻璃层板 |
| 储酒量 | 约 28 瓶（标准 750ml） |
| 尺寸 | 550×580×1460mm |

## 使用场景
- **客厅**：朋友聚会随时取用冰镇饮料和红酒
- **书房/影音室**：享受红酒+电影
- **办公室**：接待客户，展示品味

## 常见问题
**Q: 红酒必须平放吗？**
A: 软木塞封瓶的红酒建议平放，让酒液浸润木塞防止干裂。螺旋盖红酒可以竖放。本产品酒架为 15° 倾斜设计，兼顾展示和保存。

**Q: 不能放什么？**
A: 香蕉、芒果等热带水果不宜冷藏（会冻伤发黑）。巧克力建议密封存放避免串味。',
    'https://picsum.photos/seed/fridge-icebar/400/400',
    'https://picsum.photos/seed/fridge-icebar-open/400/400',
    75, 1, NOW(), 3299.00, 3299.00, 150, 1, 0, 0, NOW(), 1, NOW(), 1);

SET @spu_icebar = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_icebar, '极净 IceBar 200 · 典雅黑', '颜色:典雅黑', '{"颜色":"典雅黑"}', 3299.00, 3699.00, 2500.00, 150, 10, 52000, 'RF-IB200-BK', 10, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 1, total_stock = 150 WHERE id = @spu_icebar;

-- ============================================================
-- 6. 家用空调 (category_id=3)
-- ============================================================

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('御风 FreshAir Pro 新风空调', '1.5匹 · 新一级 · 60m³/h新风 · UV杀菌',
    3,
    '# 御风 FreshAir Pro 新风空调

## 产品概述
集空调和新风机于一体。在制冷制热的同时，向室内引入经过 H13 HEPA 滤网过滤的新鲜空气。60m³/h 大新风量，不开窗也能呼吸新鲜空气。特别适合有老人小孩的家庭和雾霾严重地区。

## 核心参数
| 项目 | 规格 |
|------|------|
| 匹数 | 1.5 匹（3500W 制冷 / 5000W 制热） |
| 能效等级 | 新一级能效（APF 5.30） |
| 新风量 | 60m³/h |
| 适用面积 | 15-22m² |
| 噪音 | 内机 18-40dB(A)，外机 50dB(A) |
| 滤网 | H13 HEPA + 活性炭 + UV 杀菌 |
| 智能 | Wi-Fi + 语音控制（接入主流智能家居平台） |

## 为什么要新风空调？
普通空调只是把室内空气循环制冷/制热，开久了 CO2 浓度升高，人会觉得闷、头晕。新风空调不断引入过滤后的室外新鲜空气，同时排出室内污浊空气。即使不开窗，也能保持室内空气清新。

## 安装须知
- 需在墙上打一个直径 63mm 的新风孔（比空调孔略大）
- 建议专业师傅安装，总费用约 200-300 元（含打孔、支架、高空费）
- 外机需安装在通风良好的地方，远离卧室窗户

## 常见问题
**Q: 新风功能耗电吗？**
A: 新风电机功率约 25W，开一整天不到 0.6 度电。

**Q: 滤网多久更换？**
A: HEPA 滤网建议 6-12 个月更换（取决于空气质量），官方滤网售价 199 元/个。

**Q: 冬天制热效果怎么样？**
A: 采用喷气增焓压缩机，-20°C 环境仍可正常制热，无需电辅热。',
    'https://picsum.photos/seed/ac-freshair/400/400',
    'https://picsum.photos/seed/ac-freshair-install/400/400',
    95, 1, NOW(), 3999.00, 4699.00, 500, 2, 1, 1, NOW(), 1, NOW(), 1);

SET @spu_freshair = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_freshair, '御风 FreshAir Pro · 1.5匹 · 皓月白', '匹数:1.5匹;颜色:皓月白', '{"匹数":"1.5匹","颜色":"皓月白"}', 3999.00, 4299.00, 3000.00, 250, 10, 32000, 'AC-FAP-15-WH', 10, 1, NOW(), 1, NOW(), 1),
(@spu_freshair, '御风 FreshAir Pro · 1.5匹 · 星空灰', '匹数:1.5匹;颜色:星空灰', '{"匹数":"1.5匹","颜色":"星空灰"}', 4699.00, 4999.00, 3500.00, 150, 10, 32000, 'AC-FAP-15-GY', 20, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 2, total_stock = 400 WHERE id = @spu_freshair;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('御风 CoolMate 移动空调', '免安装 · 1匹 · 冷暖两用 · 除湿',
    3,
    '# 御风 CoolMate 移动空调

## 产品概述
免安装可移动空调，插电即用。适合出租房、厨房、车库等不方便安装传统空调的场所。冷暖两用，夏天制冷冬天取暖，还带独立除湿功能。

## 核心参数
| 项目 | 规格 |
|------|------|
| 匹数 | 1 匹（2600W 制冷 / 2200W 制热） |
| 适用面积 | 10-15m² |
| 噪音 | ≤53dB(A) |
| 排水 | 自蒸发免排水（湿度高时需手动排水） |
| 排风管 | 直径 150mm，长度 1.5m（含窗户密封板） |
| 轮子 | 4 个万向轮，轻松移动 |
| 重量 | 26kg |

## 适用场景
- **出租房/公寓**：房东不让打孔装空调的解决办法
- **厨房**：夏天做饭不怕热
- **车库/工作室**：临时制冷
- **备用**：家里空调坏了时的应急方案

## 常见问题
**Q: 移动空调和普通空调哪个好？**
A: 各有优劣。移动空调优点是免安装、可移动、价格低；缺点是噪音大些、制冷效率低些。如果你能装普通空调，优先选普通空调。

**Q: 排风管怎么装？**
A: 套装内含窗户密封板和排风管。把密封板卡在窗户上，排风管一端接空调、一端接密封板即可。

**Q: 需要加氟吗？**
A: 不需要。移动空调整体密封，冷媒在出厂时已充好，终身无需加氟。',
    'https://picsum.photos/seed/ac-portable/400/400',
    'https://picsum.photos/seed/ac-portable-room/400/400',
    68, 1, NOW(), 1599.00, 1899.00, 600, 2, 0, 0, NOW(), 1, NOW(), 1);

SET @spu_coolmate = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_coolmate, '御风 CoolMate · 1匹 · 白色 · 单冷', '匹数:1匹;颜色:白色;类型:单冷', '{"匹数":"1匹","颜色":"白色","类型":"单冷"}', 1599.00, 1799.00, 1200.00, 300, 15, 26000, 'AC-CM-10-CL-WH', 10, 1, NOW(), 1, NOW(), 1),
(@spu_coolmate, '御风 CoolMate · 1匹 · 白色 · 冷暖', '匹数:1匹;颜色:白色;类型:冷暖', '{"匹数":"1匹","颜色":"白色","类型":"冷暖"}', 1899.00, 2099.00, 1450.00, 200, 15, 26000, 'AC-CM-10-HT-WH', 20, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 2, total_stock = 500 WHERE id = @spu_coolmate;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('御风 EcoSilence 3匹柜机', '3匹 · 新一级 · 22dB静音 · 1550m³/h大风量',
    3,
    '# 御风 EcoSilence 3匹柜机

## 产品概述
客厅专属 3 匹圆柱柜机。新一级能效 APF 4.80，22dB 超静音运行。1550m³/h 大风量，3 分钟冷暖全屋（30-45m²）。支持语音控制和远程操控。

## 核心参数
| 项目 | 规格 |
|------|------|
| 匹数 | 3 匹（7200W 制冷 / 9800W 制热） |
| 能效等级 | 新一级能效（APF 4.80） |
| 循环风量 | 1550m³/h |
| 适用面积 | 30-45m² |
| 噪音 | 内机 22-42dB(A) |
| 尺寸 | 内机 420×420×1800mm |
| 出风 | 上下/左右扫风，130° 广角送风 |

## 清洁保养
- **滤网**：每 2 周清洗一次（取出用水冲洗，晾干装回）
- **蒸发器**：每年夏季前用空调清洗剂喷洗一次
- **外机**：确保周围无杂物遮挡，散热翅片可用软刷清理灰尘

## 常见问题
**Q: 3匹和1.5匹怎么选？**
A: 简单按面积：≤22m² 选 1.5 匹，22-35m² 选 2 匹，≥35m² 选 3 匹。如果房间西晒、顶楼或有落地窗，适当加大匹数。

**Q: 柜机和挂机哪个好？**
A: 柜机风量大、送风范围广，适合客厅。挂机省空间、价格低，适合卧室。大客厅建议选柜机。',
    'https://picsum.photos/seed/ac-cabinet3/400/400',
    'https://picsum.photos/seed/ac-cabinet3-living/400/400',
    88, 1, NOW(), 6999.00, 7699.00, 200, 2, 0, 0, NOW(), 1, NOW(), 1);

SET @spu_eco3 = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_eco3, '御风 EcoSilence · 3匹 · 珍珠白', '匹数:3匹;颜色:珍珠白', '{"匹数":"3匹","颜色":"珍珠白"}', 6999.00, 7499.00, 5200.00, 100, 5, 52000, 'AC-ES3-WH', 10, 1, NOW(), 1, NOW(), 1),
(@spu_eco3, '御风 EcoSilence · 3匹 · 香槟金', '匹数:3匹;颜色:香槟金', '{"匹数":"3匹","颜色":"香槟金"}', 7699.00, 8199.00, 5800.00, 60, 5, 52000, 'AC-ES3-GD', 20, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 2, total_stock = 160 WHERE id = @spu_eco3;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('御风 SmartCool 2匹挂机', '2匹 · 新一级 · 自清洁 · 智能温控',
    3,
    '# 御风 SmartCool 2匹挂机

## 产品概述
大1.5-2匹挂机，适合较大卧室或小客厅。内置蒸发器自清洁技术，56°C 高温杀菌 30 分钟。智能温控算法能根据室内外温差自动调节运行频率，比定频空调节电 30%。

## 核心参数
| 项目 | 规格 |
|------|------|
| 匹数 | 2 匹（5100W 制冷 / 6800W 制热） |
| 能效等级 | 新一级能效（APF 4.75） |
| 适用面积 | 20-30m² |
| 噪音 | 内机 20-42dB(A) |
| 自清洁 | 56°C 高温杀菌，蒸发器自洁 |

## 自清洁功能说明
1. 蒸发器结霜：制冷模式下蒸发器表面结霜，剥离灰尘
2. 高温化霜：转制热模式，56°C 高温溶解霜层并杀菌
3. 烘干：送风烘干蒸发器，防止霉菌滋生
整个过程约 30 分钟，建议每月运行一次。

## 常见问题
**Q: 自清洁能替代人工清洗吗？**
A: 可以大大减少清洗频率，但不能完全替代。建议每年还是请专业师傅深度清洗一次（约 100-150 元）。

**Q: 2匹挂机能替代柜机吗？**
A: 如果客厅 25-30m²，2 匹挂机够用且更省空间。但柜机送风范围更广、降温更快。',
    'https://picsum.photos/seed/ac-smartcool2/400/400',
    'https://picsum.photos/seed/ac-smartcool2-bedroom/400/400',
    82, 1, NOW(), 4599.00, 4899.00, 300, 1, 0, 0, NOW(), 1, NOW(), 1);

SET @spu_smartcool = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_smartcool, '御风 SmartCool · 2匹 · 皓月白', '匹数:2匹;颜色:皓月白', '{"匹数":"2匹","颜色":"皓月白"}', 4599.00, 4899.00, 3500.00, 300, 15, 28000, 'AC-SC2-WH', 10, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 1, total_stock = 300 WHERE id = @spu_smartcool;

-- ============================================================
-- 7. 平板电视 (category_id=5)
-- ============================================================

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('逸彩 85 寸 QD-OLED 电视', '85寸 · QD-OLED · 4K 144Hz · 无限对比度',
    5,
    '# 逸彩 85 寸 QD-OLED 电视

## 产品概述
逸彩旗舰 QD-OLED 电视。QD-OLED 结合了量子点的色彩纯度和 OLED 的像素级控光，实现了真正的无限对比度。85 寸巨幕配合 4K 144Hz，无论是看电影还是玩游戏都是顶级体验。

## 核心参数
| 项目 | 规格 |
|------|------|
| 屏幕 | 85 寸 QD-OLED，3840×2160 |
| 刷新率 | 144Hz（支持 VRR 48-144Hz） |
| HDR | Dolby Vision IQ, HDR10+, HLG |
| 色域 | 99.5% DCI-P3 |
| 亮度 | 峰值 2000nit（HDR 高光） |
| 处理器 | 逸彩 AI Picture X2 处理器 |
| 音响 | 6.2.2 声道，80W |
| 系统 | Stellar TV OS 4.0 |
| 接口 | 4×HDMI 2.1, 3×USB, 光纤, RJ45 |

## 画质技术
- **像素级控光**：每个像素独立发光，黑色就是真正的"黑"，无光晕
- **量子点色彩**：蓝色 OLED 激发量子点材料，实现超广色域
- **AI 画质引擎**：实时分析画面内容，优化清晰度、降噪、色彩
- **电影制作人模式**：关闭所有后处理，还原导演意图

## 游戏体验
- HDMI 2.1 × 4，全部支持 4K 144Hz
- VRR（可变刷新率）+ ALLM（自动低延迟模式）
- 输入延迟仅 5.8ms（4K@60Hz）
- 支持 NVIDIA G-SYNC 和 AMD FreeSync Premium Pro

## 常见问题
**Q: OLED 会烧屏吗？**
A: QD-OLED 相比传统 OLED 烧屏风险更低。电视内置像素刷新、屏幕位移等保护机制。正常家庭使用无需担心。

**Q: 需要配音响吗？**
A: 内置 6.2.2 声道 80W 音响效果已经很好。但如果追求影院级体验，建议配一套回音壁或家庭影院。

**Q: 挂墙还是放电视柜？**
A: 85 寸重量约 42kg。建议挂墙（需承重墙+专业支架），更安全也更美观。',
    'https://picsum.photos/seed/tv-85oled/400/400',
    'https://picsum.photos/seed/tv-85oled-living/400/400',
    99, 1, NOW(), 24999.00, 27999.00, 50, 2, 1, 1, NOW(), 1, NOW(), 1);

SET @spu_tv85 = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_tv85, '逸彩 85 寸 QD-OLED · 标准版', '版本:标准版', '{"版本":"标准版"}', 24999.00, 26999.00, 20000.00, 30, 3, 42000, 'TV-85OLED-STD', 10, 1, NOW(), 1, NOW(), 1),
(@spu_tv85, '逸彩 85 寸 QD-OLED · 壁挂套装(含安装)', '版本:壁挂套装(含安装)', '{"版本":"壁挂套装(含安装)"}', 27999.00, 29999.00, 22400.00, 20, 3, 42000, 'TV-85OLED-WALL', 20, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 2, total_stock = 50 WHERE id = @spu_tv85;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('逸彩 50 寸 QLED 游戏电视', '50寸 · QLED · 4K 144Hz · HDMI2.1 · 游戏优化',
    5,
    '# 逸彩 50 寸 QLED 游戏电视

## 产品概述
专为游戏玩家设计的中小尺寸电视。50 寸适合卧室和电竞房。QLED 量子点广色域 + 4K 144Hz + HDMI 2.1，完美适配 PS5、Xbox Series X 和 PC 游戏。

## 核心参数
| 项目 | 规格 |
|------|------|
| 屏幕 | 50 寸 QLED VA，3840×2160 |
| 刷新率 | 144Hz（VRR 48-144Hz） |
| HDR | Dolby Vision, HDR10+ |
| 色域 | 95% DCI-P3 |
| 亮度 | 峰值 1000nit |
| 处理器 | 逸彩 Game Engine 处理器 |
| 音响 | 2.1 声道，40W（含独立低音单元） |
| 接口 | 2×HDMI 2.1, 2×HDMI 2.0, 2×USB |

## 游戏功能
- **Game Bar**：一键呼出游戏工具栏，实时显示帧率、HDR、VRR 状态
- **暗部增强**：提升暗场景可见度，不被"老六"阴
- **动态准星**：屏幕中央显示自定义准星，FPS 游戏物理外挂
- **低延迟**：输入延迟仅 4.8ms（4K@120Hz）

## 常见问题
**Q: 50寸适合多大距离观看？**
A: 推荐视距 1.5-2.5 米。放在卧室、书房或小型客厅非常合适。

**Q: VA 面板有什么优缺点？**
A: VA 对比度高（黑色更深沉），但可视角度比 IPS 窄。如果你正对电视观看，VA 是更好的选择。侧着看会有轻微色偏。',
    'https://picsum.photos/seed/tv-50qled/400/400',
    'https://picsum.photos/seed/tv-50qled-game/400/400',
    82, 1, NOW(), 3999.00, 4999.00, 400, 2, 1, 1, NOW(), 1, NOW(), 1);

SET @spu_tv50 = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_tv50, '逸彩 50 寸 QLED 游戏电视 · 标准版', '版本:标准版', '{"版本":"标准版"}', 3999.00, 4499.00, 3100.00, 200, 10, 14000, 'TV-50QLED-STD', 10, 1, NOW(), 1, NOW(), 1),
(@spu_tv50, '逸彩 50 寸 QLED 游戏电视 · 游戏套装(含支架)', '版本:游戏套装(含支架)', '{"版本":"游戏套装(含支架)"}', 4999.00, 5499.00, 3900.00, 100, 10, 14000, 'TV-50QLED-GK', 20, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 2, total_stock = 300 WHERE id = @spu_tv50;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('逸彩 43 寸 FHD 智能电视', '43寸 · 1080P · 智能系统 · 卧室/厨房',
    5,
    '# 逸彩 43 寸 FHD 智能电视

## 产品概述
经济实用型 43 寸智能电视，适合卧室、厨房、小型公寓。内置智能系统，支持主流流媒体和投屏。价格实惠，功能齐全。

## 核心参数
| 项目 | 规格 |
|------|------|
| 屏幕 | 43 寸 VA，1920×1080 |
| 刷新率 | 60Hz |
| 处理器 | 四核 A55 |
| 内存/存储 | 2GB+16GB |
| 音响 | 2×10W |
| 系统 | Android TV 11 |
| 接口 | 3×HDMI 1.4, 2×USB, 光纤, RJ45 |

## 适合人群
- 卧室观影（43 寸在 2 米距离观感舒适）
- 租房党（性价比高，搬家不心疼）
- 老人使用（界面简单，操作方便）
- 厨房/餐厅背景电视

## 常见问题
**Q: 为什么是 1080P 不是 4K？**
A: 43 寸这个尺寸，在正常观看距离下 1080P 和 4K 差异不易察觉。降低分辨率意味着更低的价格，更适合预算敏感的用户。

**Q: 能安装 App 吗？**
A: 可以。内置应用商店，支持安装爱奇艺、腾讯视频、B站、Kodi 等主流应用。',
    'https://picsum.photos/seed/tv-43fhd/400/400',
    NULL,
    60, 1, NOW(), 1299.00, 1299.00, 600, 1, 0, 0, NOW(), 1, NOW(), 1);

SET @spu_tv43 = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_tv43, '逸彩 43 寸 FHD · 黑色', '颜色:黑色', '{"颜色":"黑色"}', 1299.00, 1499.00, 950.00, 600, 30, 7000, 'TV-43FHD-BK', 10, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 1, total_stock = 600 WHERE id = @spu_tv43;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('逸彩 The Wall 120 寸巨幕电视', '120寸 · MicroLED · 模块化拼接 · 商用/豪宅',
    5,
    '# 逸彩 The Wall 120 寸巨幕电视

## 产品概述
面向高端商业和豪宅用户的 120 寸 MicroLED 电视。采用模块化拼接技术，由多个 MicroLED 模块无缝拼接成 120 寸巨幕。亮度高达 3000nit，色域覆盖 100% DCI-P3，是顶级别墅、高端会所、董事会议室的终极显示方案。

## 核心参数
| 项目 | 规格 |
|------|------|
| 屏幕 | 120 寸 MicroLED |
| 分辨率 | 3840×2160 |
| 亮度 | 峰值 3000nit |
| 色域 | 100% DCI-P3 |
| 对比度 | 无限（像素级控光） |
| 寿命 | 100000 小时 |
| 音响 | 需配独立音响系统 |
| 安装 | 专业团队上门拼接安装 |

## 为什么选 MicroLED？
- **无烧屏**：无机材料，不会有机材料那样老化烧屏
- **超高亮度**：3000nit 峰值亮度，白天不拉窗帘也能看清
- **模块化**：可拼接任意尺寸，维修只需更换故障模块
- **超长寿命**：10 万小时，按每天 8 小时可用 34 年

## 常见问题
**Q: 安装需要什么条件？**
A: 需要承重墙、380V 电源、足够的空间。我们的专业团队会提前上门勘测。

**Q: 价格包含安装吗？**
A: 包含专业团队上门安装和调试。如需特殊支架或布线，费用另计。

**Q: 有一个模块坏了怎么办？**
A: 单个模块可独立更换，无需更换整屏。模块享有 5 年质保。',
    'https://picsum.photos/seed/tv-thewall/400/400',
    'https://picsum.photos/seed/tv-thewall-room/400/400',
    100, 1, NOW(), 499999.00, 499999.00, 5, 1, 1, 0, NOW(), 1, NOW(), 1);

SET @spu_thewall = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_thewall, '逸彩 The Wall 120寸 · 标准套装（含安装）', '规格:120寸标准套装（含安装）', '{"规格":"120寸标准套装（含安装）"}', 499999.00, 599999.00, 350000.00, 5, 1, 250000, 'TV-TW120-STD', 10, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 1, total_stock = 5 WHERE id = @spu_thewall;

-- ============================================================
-- 8. 生活家电直挂 → 家用电冰箱 (category_id=2)
-- ============================================================

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('极净 SteamPro 手持挂烫机', '1200W · 25g/min蒸汽 · 杀菌除螨 · 小巧便携',
    2,
    '# 极净 SteamPro 手持挂烫机

## 产品概述
小巧便携的手持挂烫机，25g/min 强劲蒸汽输出。30 秒快速出汽，一熨即平。120°C 高温蒸汽杀菌除螨，不仅熨衣还消毒。适合出差旅行和日常快节奏生活。

## 核心参数
| 项目 | 规格 |
|------|------|
| 功率 | 1200W |
| 蒸汽量 | 25g/min |
| 水箱 | 120ml 可拆卸 |
| 预热时间 | 30 秒 |
| 续航 | 约 8 分钟（满水箱） |
| 重量 | 0.85kg |
| 面板 | 陶瓷釉面板（不伤衣物） |

## 使用技巧
- **衬衫**：先熨领口和袖口，再熨大身，挂烫一遍即可
- **西装**：保持 5-10cm 距离，蒸汽穿透面料，不直接接触避免发亮
- **窗帘**：挂在杆上直接蒸汽除皱，不用拆下来
- **布艺沙发**：蒸汽杀菌除螨，每周一次保持卫生

## 常见问题
**Q: 能替代传统熨斗吗？**
A: 可以满足日常 90% 的除皱需求。但对要求极高的正装衬衫（硬挺领口袖口），传统熨斗效果更好。

**Q: 能用自来水吗？**
A: 建议用纯净水或蒸馏水。自来水水垢会堵塞蒸汽孔，影响寿命。',
    'https://picsum.photos/seed/steamer/400/400',
    'https://picsum.photos/seed/steamer-use/400/400',
    65, 1, NOW(), 259.00, 259.00, 800, 1, 1, 0, NOW(), 1, NOW(), 1);

SET @spu_steamer = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_steamer, '极净 SteamPro · 抹茶绿', '颜色:抹茶绿', '{"颜色":"抹茶绿"}', 259.00, 299.00, 180.00, 800, 30, 850, 'HA-STM-GN', 10, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 1, total_stock = 800 WHERE id = @spu_steamer;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('极净 HeatChef 空气炸锅', '5.5L · 360°热风 · 智能菜单 · 免翻面',
    2,
    '# 极净 HeatChef 空气炸锅

## 产品概述
5.5 升大容量空气炸锅，满足 3-5 人家庭使用。360° 高速热风循环，无需翻面也能均匀加热。内置 8 大智能菜单，一键烹饪薯条、鸡翅、蛋糕、牛排等。

## 核心参数
| 项目 | 规格 |
|------|------|
| 容量 | 5.5L |
| 功率 | 1500W |
| 温度范围 | 80-200°C |
| 定时 | 1-60 分钟 |
| 内胆 | 不粘涂层（可拆卸清洗） |
| 预设菜单 | 薯条、鸡翅、蛋糕、牛排、鱼、虾、蔬菜、解冻 |

## 空气炸锅 vs 传统油炸
- **油脂减少 80%**：利用食材自身油脂，无需额外加油
- **无油烟**：厨房不再油腻腻
- **更安全**：没有高温油锅溅油风险
- **更健康**：减少油炸产生的反式脂肪酸

## 推荐食谱
1. **脆皮鸡翅**：200°C、15 分钟，中途无需翻面
2. **蒜香排骨**：180°C、20 分钟，外酥里嫩
3. **芝士焗红薯**：180°C、12 分钟，拉丝诱人

## 常见问题
**Q: 炸篮能放洗碗机吗？**
A: 可以。炸篮和烤盘均可放入洗碗机清洗。

**Q: 需要预热吗？**
A: 不需要。开机即用，比烤箱快 3-5 倍。',
    'https://picsum.photos/seed/airfryer/400/400',
    'https://picsum.photos/seed/airfryer-food/400/400;https://picsum.photos/seed/airfryer-open/400/400',
    78, 1, NOW(), 399.00, 499.00, 1000, 2, 1, 1, NOW(), 1, NOW(), 1);

SET @spu_airfry = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_airfry, '极净 HeatChef · 象牙白', '颜色:象牙白', '{"颜色":"象牙白"}', 399.00, 449.00, 280.00, 500, 25, 4800, 'HA-AF5-WH', 10, 1, NOW(), 1, NOW(), 1),
(@spu_airfry, '极净 HeatChef · 墨岩黑', '颜色:墨岩黑', '{"颜色":"墨岩黑"}', 499.00, 549.00, 350.00, 300, 25, 4800, 'HA-AF5-BK', 20, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 2, total_stock = 800 WHERE id = @spu_airfry;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('极净 AquaBloom 智能加湿器', '5L · UV杀菌 · 恒湿50% · 静音加湿',
    2,
    '# 极净 AquaBloom 智能加湿器

## 产品概述
5 升大容量超声波加湿器，一次加水持续加湿 24 小时。内置 UV-C 杀菌灯先杀菌再加湿，确保出雾干净卫生。智能恒湿模式自动将湿度维持在 50%-60% 舒适区间。

## 核心参数
| 项目 | 规格 |
|------|------|
| 类型 | 超声波 |
| 水箱 | 5L（上加水，免开盖） |
| 加湿量 | 300ml/h（最大档） |
| 杀菌 | UV-C 紫外线 + 银离子抑菌 |
| 噪音 | ≤28dB(A)（睡眠档） |
| 适用面积 | 20-40m² |
| 智能 | Wi-Fi 远程控制 + 语音控制 |

## 最佳湿度指南
- **人体舒适**：40%-60%
- **婴儿房**：50%-60%
- **木地板/家具保护**：40%-55%
- **乐器（钢琴/吉他）**：45%-55%

## 常见问题
**Q: 能用自来水吗？**
A: 建议用纯净水或过滤水。自来水中的矿物质会被超声波打成白粉（PM2.5），虽然无害但会落在家具上。

**Q: 需要每天清洗吗？**
A: 不需要。UV 杀菌和银离子可以抑制细菌。建议每周清洗一次水箱，每月深度清洁一次。

**Q: 加湿器会加重呼吸道问题吗？**
A: 只要定期清洁、使用纯净水，加湿器是安全的。问题通常出在不清洁导致细菌滋生。',
    'https://picsum.photos/seed/humidifier/400/400',
    NULL,
    70, 1, NOW(), 299.00, 359.00, 600, 2, 0, 0, NOW(), 1, NOW(), 1);

SET @spu_humid = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_humid, '极净 AquaBloom · 暖白色', '颜色:暖白色', '{"颜色":"暖白色"}', 299.00, 329.00, 200.00, 400, 20, 1800, 'HA-AQM-WH', 10, 1, NOW(), 1, NOW(), 1),
(@spu_humid, '极净 AquaBloom · 雾蓝色', '颜色:雾蓝色', '{"颜色":"雾蓝色"}', 359.00, 389.00, 240.00, 200, 20, 1800, 'HA-AQM-BL', 20, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 2, total_stock = 600 WHERE id = @spu_humid;

INSERT INTO stellar_spu (name, sub_title, category_id, description_md,
    main_image, sub_images, sort, status, on_shelf_time,
    min_price, max_price, total_stock, sku_count, is_new, is_hot,
    create_time, create_user, update_time, update_user) VALUES

('极净 CleanBot S1 扫拖一体机器人', '激光导航 · 5000Pa吸力 · 自动洗拖布 · AI避障',
    2,
    '# 极净 CleanBot S1 扫拖一体机器人

## 产品概述
全功能扫拖一体机器人。激光雷达 + AI 视觉导航，精准建图避障。5000Pa 大吸力配合旋转加压拖布，扫地拖地一次搞定。支持自动回洗拖布和自动集尘。

## 核心参数
| 项目 | 规格 |
|------|------|
| 导航 | LDS 激光雷达 + AI 视觉 |
| 吸力 | 5000Pa（4 档可调） |
| 拖地 | 双旋转拖布，10N 加压 |
| 尘盒 | 400ml（自动集尘底座 3L） |
| 水箱 | 200ml 电控（基站自动补水） |
| 电池 | 5200mAh（续航约 180 分钟） |
| 越障 | 20mm |
| 噪音 | ≤65dB(A) |

## 智能功能
- **AI 避障**：识别鞋子、数据线、宠物粪便等常见障碍物
- **划区清扫**：在地图上画框，指定区域重点清扫
- **虚拟墙**：设置禁区，避免进入阳台、卫生间等区域
- **多楼层**：支持保存 3 张地图，复式/别墅自动切换

## 常见问题
**Q: 能吸干净地毯吗？**
A: 短毛地毯没问题。检测到地毯会自动增压至 5000Pa。长毛地毯建议设虚拟墙避开。

**Q: 会自动倒垃圾吗？**
A: 配合自动集尘底座（需另购 999 元），每次回充自动将尘盒垃圾吸入 3L 尘袋。约 2 个月更换一次尘袋。

**Q: 宠物会怕吗？**
A: 机器人噪音较低，大多数猫狗会好奇但不会害怕。建议前几次使用时有主人在场观察。',
    'https://picsum.photos/seed/cleanbot-s1/400/400',
    'https://picsum.photos/seed/cleanbot-s1-dock/400/400',
    88, 1, NOW(), 3299.00, 4299.00, 500, 2, 1, 1, NOW(), 1, NOW(), 1);

SET @spu_cleanbot = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, specs_json, price, original_price, cost_price, stock, warn_stock, weight_g, barcode, sort, status, create_time, create_user, update_time, update_user) VALUES
(@spu_cleanbot, '极净 CleanBot S1 · 标准版', '版本:标准版', '{"版本":"标准版"}', 3299.00, 3699.00, 2500.00, 300, 15, 4200, 'HA-CBS1-STD', 10, 1, NOW(), 1, NOW(), 1),
(@spu_cleanbot, '极净 CleanBot S1 · 全能版（含自动集尘+上下水）', '版本:全能版（含自动集尘+上下水）', '{"版本":"全能版（含自动集尘+上下水）"}', 4299.00, 4799.00, 3300.00, 150, 15, 8200, 'HA-CBS1-ULT', 20, 1, NOW(), 1, NOW(), 1);

UPDATE stellar_spu SET sku_count = 2, total_stock = 450 WHERE id = @spu_cleanbot;

-- ============================================================
-- 完成汇总
-- ============================================================
SELECT '商品数据扩充完成!' AS result;
SELECT c.name AS 类别, COUNT(s.id) AS 商品数
FROM stellar_category c
LEFT JOIN stellar_spu s ON s.category_id = c.id
WHERE c.type = 1 AND c.status = 1
GROUP BY c.id, c.name
ORDER BY COUNT(s.id) DESC;
