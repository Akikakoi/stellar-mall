-- ============================================================
-- 平板电脑分类产品数据
-- 4 款 SPU + 8 条 SKU + AI 助手可用产品文档
-- ============================================================

-- ---- SPU 1: 星耀 Tab Pro 12.4 ----
INSERT INTO stellar_spu (name, sub_title, category_id, category2_id, description, description_md, main_image, sub_images, slider_images, sale_count, comment_count, total_stock, sku_count, min_price, max_price, is_new, is_hot, sort, status, create_time, create_user, update_time, update_user) VALUES
('星耀 Tab Pro 12.4', '12.4" OLED · 骁龙8Gen3 · 10000mAh · 星耀笔', 280, NULL,
'<div class="product-detail"><h2>星耀 Tab Pro 12.4</h2><p>星耀 Tab Pro 12.4 是星耀品牌旗舰级平板电脑，搭载12.4英寸2.8K OLED屏幕，支持120Hz自适应刷新率和HDR10+显示，峰值亮度达1600nit。内置高通骁龙8 Gen 3处理器，配备12GB LPDDR5X内存，提供256GB/512GB UFS 4.0存储选择。内置10000mAh大容量电池，支持67W有线快充和20W无线充电。支持星耀笔（4096级压感、2ms延迟）、磁吸键盘和星耀互联跨设备协同。前置1200万超广角摄像头，后置5000万主摄+1300万超广角双摄系统。四扬声器杜比全景声，支持WiFi 7和蓝牙5.4，可选5G蜂窝网络版本。金属一体化机身，厚度仅6.5mm，重量约580g。</p><h3>核心卖点</h3><ul><li>12.4" 2.8K OLED 120Hz 屏幕 — 1600nit 峰值亮度</li><li>骁龙8 Gen 3 + 12GB LPDDR5X — 旗舰性能</li><li>10000mAh + 67W快充 + 20W无线充</li><li>星耀笔 4096级压感 2ms延迟</li><li>四扬声器杜比全景声</li></ul></div>',
'## 星耀 Tab Pro 12.4\n\n星耀 Tab Pro 12.4 是星耀品牌旗舰级平板电脑，主打生产力与影音娱乐的完美平衡。\n\n### 核心参数\n\n| 项目 | 参数 |\n|------|------|\n| 屏幕 | 12.4" 2.8K OLED (2800×2000)，120Hz LTPO，HDR10+，1600nit峰值 |\n| 处理器 | 高通骁龙8 Gen 3 (4nm) |\n| 内存 | 12GB LPDDR5X |\n| 存储 | 256GB / 512GB UFS 4.0 |\n| 电池 | 10000mAh，67W有线快充，20W无线充电 |\n| 摄像头 | 前置12MP超广角，后置50MP主摄(OIS)+13MP超广角 |\n| 音频 | 四扬声器，杜比全景声，4麦克风阵列 |\n| 连接 | WiFi 7，蓝牙5.4，可选5G |\n| 系统 | StellarOS 5.0 (基于Android 15) |\n| 尺寸重量 | 285×195×6.5mm，约580g (WiFi版) |\n| 颜色 | 星云灰、晨曦金、极光蓝 |\n\n### 特色功能\n\n- **星耀笔 Gen 3**：4096级压感，2ms超低延迟，磁吸充电，支持悬空预览\n- **星耀互联 3.0**：跨设备剪贴板、文件拖拽、屏幕镜像、通话接力\n- **磁吸键盘 Pro**：全尺寸背光键盘，1.3mm键程，多点触控板\n- **PC模式**：一键切换桌面级多窗口操作界面\n- **人脸+指纹双解锁**\n\n### 适用场景\n\n- 商务办公：搭配键盘秒变轻薄笔记本\n- 创意设计：配合星耀笔进行绘画、笔记、标注\n- 影音娱乐：OLED影院级观影体验\n- 在线学习：大屏护眼，分屏高效',
'https://picsum.photos/seed/tab-pro-12/400/400', 'https://picsum.photos/seed/tab-pro-12-1/800/800,https://picsum.photos/seed/tab-pro-12-2/800/800,https://picsum.photos/seed/tab-pro-12-3/800/800', 'https://picsum.photos/seed/tab-pro-12-slider/1200/500', 0, 0, 160, 2, 4299.00, 5299.00, 1, 1, 100, 1, NOW(), 1, NOW(), 1);

SET @tab_pro_id = LAST_INSERT_ID();

-- ---- SKU 1-1: Tab Pro WiFi 12+256 ----
INSERT INTO stellar_sku (spu_id, name, specs, price, original_price, stock, warn_stock, sort, status, create_time, create_user, update_time, update_user) VALUES
(@tab_pro_id, '星耀 Tab Pro 12.4 · WiFi版 12G+256G', '网络:WiFi;内存:12G;存储:256G;颜色:星云灰', 4299.00, 4599.00, 80, 10, 1, 1, NOW(), 1, NOW(), 1);

-- ---- SKU 1-2: Tab Pro 5G 12+512 ----
INSERT INTO stellar_sku (spu_id, name, specs, price, original_price, stock, warn_stock, sort, status, create_time, create_user, update_time, update_user) VALUES
(@tab_pro_id, '星耀 Tab Pro 12.4 · 5G版 12G+512G', '网络:5G;内存:12G;存储:512G;颜色:晨曦金', 5299.00, 5799.00, 80, 10, 2, 1, NOW(), 1, NOW(), 1);


-- ---- SPU 2: 星耀 Tab Air 11 ----
INSERT INTO stellar_spu (name, sub_title, category_id, category2_id, description, description_md, main_image, sub_images, slider_images, sale_count, comment_count, total_stock, sku_count, min_price, max_price, is_new, is_hot, sort, status, create_time, create_user, update_time, update_user) VALUES
('星耀 Tab Air 11', '11" 2.5K LCD · 490g · 骁龙7+Gen3 · 8000mAh', 280, NULL,
'<div class="product-detail"><h2>星耀 Tab Air 11</h2><p>星耀 Tab Air 11 定位中端轻薄平板，整机仅490g，厚度6.2mm，极致便携。配备11英寸2.5K分辨率LCD屏幕，支持144Hz高刷和DC调光，通过莱茵TUV低蓝光认证。搭载高通骁龙7+ Gen3处理器和8GB/12GB LPDDR5内存，日常使用流畅无压力。内置8000mAh电池，支持45W快充，续航长达14小时。支持星耀笔（4096级压感）和磁吸键盘。四扬声器杜比音效，前置800万摄像头支持人脸解锁，后置1300万像素主摄。提供WiFi版，银翼白、深空黑双色可选。</p><h3>核心卖点</h3><ul><li>490g超轻机身 — 单手握持无负担</li><li>11" 2.5K 144Hz LCD — DC调光护眼</li><li>骁龙7+Gen3 — 性能功耗完美平衡</li><li>8000mAh + 45W快充 — 14小时续航</li><li>全金属机身仅6.2mm</li></ul></div>',
'## 星耀 Tab Air 11\n\n星耀 Tab Air 11 是一款主打轻薄便携的中端平板，适合学生和日常用户。\n\n### 核心参数\n\n| 项目 | 参数 |\n|------|------|\n| 屏幕 | 11" 2.5K LCD (2560×1600)，144Hz，DC调光，莱茵低蓝光认证 |\n| 处理器 | 高通骁龙7+Gen3 (4nm) |\n| 内存 | 8GB / 12GB LPDDR5 |\n| 存储 | 128GB / 256GB UFS 3.1 |\n| 电池 | 8000mAh，45W快充 |\n| 摄像头 | 前置8MP，后置13MP |\n| 音频 | 四扬声器，杜比音效 |\n| 连接 | WiFi 6E，蓝牙5.3 |\n| 系统 | StellarOS 5.0 |\n| 尺寸重量 | 252×165×6.2mm，约490g |\n| 颜色 | 银翼白、深空黑 |\n\n### 特色功能\n\n- **超轻机身**：490g，单手可持，通勤无负担\n- **护眼屏**：DC调光 + 莱茵TUV低蓝光认证，长时间学习不疲劳\n- **星耀笔支持**：记笔记、标注PDF得心应手\n- **学习助手**：分屏双开、AI实时翻译、错题本\n\n### 适用场景\n\n- 学生党：看网课、记笔记、刷题\n- 追剧神器：2.5K大屏 + 四扬声器\n- 轻办公：邮件、文档、视频会议',
'https://picsum.photos/seed/tab-air-11/400/400', 'https://picsum.photos/seed/tab-air-11-1/800/800,https://picsum.photos/seed/tab-air-11-2/800/800', 'https://picsum.photos/seed/tab-air-11-slider/1200/500', 0, 0, 250, 2, 2199.00, 2799.00, 1, 0, 90, 1, NOW(), 1, NOW(), 1);

SET @tab_air_id = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, price, original_price, stock, warn_stock, sort, status, create_time, create_user, update_time, update_user) VALUES
(@tab_air_id, '星耀 Tab Air 11 · WiFi版 8G+128G', '网络:WiFi;内存:8G;存储:128G;颜色:银翼白', 2199.00, 2399.00, 150, 10, 1, 1, NOW(), 1, NOW(), 1);

INSERT INTO stellar_sku (spu_id, name, specs, price, original_price, stock, warn_stock, sort, status, create_time, create_user, update_time, update_user) VALUES
(@tab_air_id, '星耀 Tab Air 11 · WiFi版 12G+256G', '网络:WiFi;内存:12G;存储:256G;颜色:深空黑', 2799.00, 2999.00, 100, 10, 2, 1, NOW(), 1, NOW(), 1);


-- ---- SPU 3: 星耀 Tab Max 14.6 ----
INSERT INTO stellar_spu (name, sub_title, category_id, category2_id, description, description_md, main_image, sub_images, slider_images, sale_count, comment_count, total_stock, sku_count, min_price, max_price, is_new, is_hot, sort, status, create_time, create_user, update_time, update_user) VALUES
('星耀 Tab Max 14.6', '14.6" 3K OLED · 天玑9300 · 星耀笔Pro · 12000mAh', 280, NULL,
'<div class="product-detail"><h2>星耀 Tab Max 14.6</h2><p>星耀 Tab Max 14.6 是专为创意工作者打造的超大屏旗舰平板。配备14.6英寸3K分辨率OLED屏幕（3000×2000），120Hz自适应刷新率，P3广色域Delta E小于1出厂校色，支持10bit色深。搭载联发科天玑9300旗舰处理器和16GB LPDDR5T内存。12000mAh超大电池支持65W快充和反向无线充电。标配星耀笔 Pro（8192级压感，1ms延迟，倾角感应，悬空笔触预览），可选磁吸全尺寸键盘。8扬声器空间音频系统，前置1200万+ToF深感摄像头，后置5000万主摄+1300万超广角+800万长焦三摄。雷电4接口×2，支持外接4K显示器。可选5G版，机身厚度6.8mm，重约720g。</p><h3>核心卖点</h3><ul><li>14.6" 3K OLED — P3色域 Delta E<1 专业校色</li><li>天玑9300 + 16GB LPDDR5T — 移动工作站</li><li>星耀笔 Pro — 8192级压感 1ms延迟</li><li>12000mAh超大电池 + 65W快充</li><li>8扬声器空间音频 + 雷电4双接口</li></ul></div>',
'## 星耀 Tab Max 14.6\n\n星耀 Tab Max 14.6 是为专业创作者打造的旗舰大屏平板，可替代笔记本+数位板。\n\n### 核心参数\n\n| 项目 | 参数 |\n|------|------|\n| 屏幕 | 14.6" 3K OLED (3000×2000)，120Hz LTPO，P3 100%，Delta E<1，10bit |\n| 处理器 | 联发科天玑9300 (4nm) |\n| 内存 | 16GB LPDDR5T |\n| 存储 | 512GB / 1TB UFS 4.0 |\n| 电池 | 12000mAh，65W快充，15W反向无线充电 |\n| 摄像头 | 前12MP+ToF，后50MP(OIS)+13MP超广角+8MP长焦(3x) |\n| 音频 | 8扬声器，空间音频，4麦克风 |\n| 接口 | 雷电4×2，支持4K@60Hz外接显示器 |\n| 连接 | WiFi 7，蓝牙5.4，可选5G |\n| 系统 | StellarOS 5.0 Pro |\n| 尺寸重量 | 330×240×6.8mm，约720g |\n| 颜色 | 深空灰、极光银 |\n\n### 特色功能\n\n- **星耀笔 Pro**：8192级压感，1ms延迟，倾角感应，悬空笔触预览，磁吸无线充电\n- **专业创作工具**：原生支持Procreate、Clip Studio Paint等，出厂内置星耀画板\n- **多屏协作**：雷电4同时外接两台4K显示器，配合键盘鼠标实现桌面级体验\n- **AI创作加速**：本地AI降噪、智能抠图、手写识别转文字\n\n### 适用场景\n\n- 数字绘画：专业级压感笔 + P3色域大屏\n- 视频剪辑：天玑9300强劲AI引擎 + 大屏精准调色\n- 3D建模：大屏大内存，流畅运行专业软件\n- 生产力办公：接键盘后完整体验PC级多任务',
'https://picsum.photos/seed/tab-max-14/400/400', 'https://picsum.photos/seed/tab-max-14-1/800/800,https://picsum.photos/seed/tab-max-14-2/800/800,https://picsum.photos/seed/tab-max-14-3/800/800', 'https://picsum.photos/seed/tab-max-14-slider/1200/500', 0, 0, 80, 2, 6299.00, 7499.00, 1, 1, 95, 1, NOW(), 1, NOW(), 1);

SET @tab_max_id = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, price, original_price, stock, warn_stock, sort, status, create_time, create_user, update_time, update_user) VALUES
(@tab_max_id, '星耀 Tab Max 14.6 · WiFi版 16G+512G', '网络:WiFi;内存:16G;存储:512G;颜色:深空灰', 6299.00, 6799.00, 50, 5, 1, 1, NOW(), 1, NOW(), 1);

INSERT INTO stellar_sku (spu_id, name, specs, price, original_price, stock, warn_stock, sort, status, create_time, create_user, update_time, update_user) VALUES
(@tab_max_id, '星耀 Tab Max 14.6 · 5G版 16G+1T', '网络:5G;内存:16G;存储:1T;颜色:极光银', 7499.00, 7999.00, 30, 5, 2, 1, NOW(), 1, NOW(), 1);


-- ---- SPU 4: 星耀 Tab Lite 10.4 ----
INSERT INTO stellar_spu (name, sub_title, category_id, category2_id, description, description_md, main_image, sub_images, slider_images, sale_count, comment_count, total_stock, sku_count, min_price, max_price, is_new, is_hot, sort, status, create_time, create_user, update_time, update_user) VALUES
('星耀 Tab Lite 10.4', '10.4" 2K屏 · 骁龙6Gen1 · 7500mAh · 学生优选', 280, NULL,
'<div class="product-detail"><h2>星耀 Tab Lite 10.4</h2><p>星耀 Tab Lite 10.4 是为学生和家庭用户打造的入门级平板。配备10.4英寸2K分辨率LCD屏幕（2000×1200），通过莱茵TUV低蓝光认证和类纸显示模式。搭载高通骁龙6 Gen 1处理器和6GB/8GB LPDDR4X内存，日常网课、视频、阅读流畅运行。7500mAh大电池支持25W快充，续航长达13小时。支持星耀笔 Lite（2048级压感）和磁吸键盘。双扬声器，前置500万+后置800万摄像头，支持MicroSD扩展（最高1TB）。磨砂机身，厚度7.5mm，重约460g。WiFi版，晴空蓝、星夜灰双色可选。</p><h3>核心卖点</h3><ul><li>10.4" 2K护眼大屏 — 类纸模式阅读舒适</li><li>骁龙6Gen1 — 够用不卡顿</li><li>7500mAh — 13小时超长续航</li><li>学生模式 — 家长管控+专注学习</li><li>MicroSD扩展 1TB — 海量存储</li></ul></div>',
'## 星耀 Tab Lite 10.4\n\n星耀 Tab Lite 10.4 定位学生入门平板，兼顾学习与娱乐，超高性价比。\n\n### 核心参数\n\n| 项目 | 参数 |\n|------|------|\n| 屏幕 | 10.4" 2K LCD (2000×1200)，60Hz，莱茵低蓝光，类纸模式 |\n| 处理器 | 高通骁龙6 Gen 1 (4nm) |\n| 内存 | 6GB / 8GB LPDDR4X |\n| 存储 | 128GB / 256GB eMMC 5.1 + MicroSD(最高1TB) |\n| 电池 | 7500mAh，25W快充 |\n| 摄像头 | 前置5MP，后置8MP |\n| 音频 | 双扬声器，3.5mm耳机孔 |\n| 连接 | WiFi 6，蓝牙5.2 |\n| 系统 | StellarOS Lite (基于Android 15) |\n| 尺寸重量 | 245×155×7.5mm，约460g |\n| 颜色 | 晴空蓝、星夜灰 |\n\n### 特色功能\n\n- **学生模式**：家长管控使用时长、应用白名单、护眼提醒\n- **类纸模式**：模拟纸质阅读体验，长时间看书不伤眼\n- **学习工具箱**：AI拍照搜题、单词卡片、错题整理\n- **MicroSD扩展**：最高支持1TB存储卡，课件视频随便存\n- **3.5mm耳机孔**：传统耳机即插即用\n\n### 适用场景\n\n- 中小学网课：大屏清晰，护眼认证\n- 课外阅读：类纸模式 + 轻量机身\n- 家庭娱乐：追剧刷视频老少皆宜\n- 启蒙学习：儿童模式 + 丰富的教育应用',
'https://picsum.photos/seed/tab-lite-10/400/400', 'https://picsum.photos/seed/tab-lite-10-1/800/800,https://picsum.photos/seed/tab-lite-10-2/800/800', 'https://picsum.photos/seed/tab-lite-10-slider/1200/500', 0, 0, 400, 2, 1299.00, 1599.00, 0, 0, 80, 1, NOW(), 1, NOW(), 1);

SET @tab_lite_id = LAST_INSERT_ID();

INSERT INTO stellar_sku (spu_id, name, specs, price, original_price, stock, warn_stock, sort, status, create_time, create_user, update_time, update_user) VALUES
(@tab_lite_id, '星耀 Tab Lite 10.4 · WiFi版 6G+128G', '网络:WiFi;内存:6G;存储:128G;颜色:晴空蓝', 1299.00, 1499.00, 250, 20, 1, 1, NOW(), 1, NOW(), 1);

INSERT INTO stellar_sku (spu_id, name, specs, price, original_price, stock, warn_stock, sort, status, create_time, create_user, update_time, update_user) VALUES
(@tab_lite_id, '星耀 Tab Lite 10.4 · WiFi版 8G+256G', '网络:WiFi;内存:8G;存储:256G;颜色:星夜灰', 1599.00, 1799.00, 150, 20, 2, 1, NOW(), 1, NOW(), 1);


-- ---- 验证 ----
SELECT s.id, s.name, s.min_price, s.max_price, s.sku_count, s.total_stock, c.name AS category
FROM stellar_spu s
JOIN stellar_category c ON c.id = s.category_id
WHERE s.category_id = 280
ORDER BY s.sort DESC, s.id;
