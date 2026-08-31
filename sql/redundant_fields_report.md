# stellar_mall 数据库冗余字段排查报告

排查时间：2026-08-29
排查对象：`stellar_mall` 库 30 张表 / 364 个字段
排查方式：information_schema 实际列信息 + 全量代码引用扫描（Java / Mapper XML / 前端 Vue+JS）+ 数据分布统计

---

## 一、确认冗余（代码零引用，建议删除）

这 2 个字段在后端 Java、Mapper XML、前端代码中**完全没有出现过任何一次**，属于历史遗留列。

| 表 | 字段 | 类型 | 数据情况 | 说明 |
|---|---|---|---|---|
| stellar_sku | market_price | decimal(10,2) | 204 行中 60 行有值 | 市场价/参考价，与 original_price（原价/划线价）语义重叠 |
| stellar_spu | slider_images | varchar(2000) | 80 行中 4 行有值 | 轮播图，实际已由 main_image + sub_images 承载 |

---

## 二、僵尸字段（仅 Mapper 搬运，无任何业务逻辑使用）

这些字段在 Mapper XML 的 resultMap/insert/update 里存在、实体类里有声明，但**业务代码和前端从未读写**——数据写进去了却从没用过。

| 表 | 字段 | 说明 |
|---|---|---|
| stellar_after_sale | exchange_sku_id | 换货目标 SKU（换货功能未实现） |
| stellar_after_sale | refund_no | 第三方退款流水号（未接入真实支付） |
| stellar_mall_order | delivery_time | 发货时间 |
| stellar_review | pics | 评价图片（前端未开放上传入口） |
| stellar_review | reply_time | 回复时间 |
| stellar_sku | cost_price | 成本价（仅持久化，无业务使用） |

---

## 三、数据层面从未写入（整列 100% NULL）

| 表 | 字段 | 表行数 | 判定 |
|---|---|---|---|
| stellar_after_sale | images / audit_remark / refund_no / exchange_sku_id | 8 | 售后高级功能预留 |
| stellar_employee | avatar | 1 | 头像功能未开放 |
| stellar_mall_order | tracking_no | 51 | 物流单号未接入 |
| stellar_mall_order_item | extra_amount | 56 | 保障服务费，代码已接入但数据未落库（功能未完成） |
| stellar_notification_log | phone | 26 | 短信通道预留 |
| stellar_points_product | coupon_id | 1 | 暂无 COUPON 类型积分商品 |
| stellar_review | pics | 9 | 同第二类 |
| stellar_spu | off_shelf_time | 80 | 上下架未走该字段 |

> 说明：这一类属于"功能预留"而非设计失误，是否清理取决于功能是否还会做。

---

## 四、空表（0 行）

- `stellar_points_redemption`（积分兑换记录）——积分商城尚未产生兑换
- `stellar_review_comment`（评价回复）——评价回复功能未开放入口

两张表结构完整且有 Mapper，属功能未上线，非冗余设计。

---

## 五、附带发现：列注释乱码（48 列）

部分表的列注释在写入时字符集错误（UTF-8 字节被按 GBK 解码），显示为 `鐢ㄦ埛ID`、`鍒涘缓鏃堕棿` 等乱码。已全部可精确还原。

受影响集中在 12 张表：

| 表 | 乱码列数 | 表 | 乱码列数 |
|---|---|---|---|
| stellar_points_product | 7 | stellar_user_points | 3 |
| stellar_points_redemption | 6 | stellar_home_module | 3 |
| stellar_points_rule | 5 | stellar_user_message | 4 |
| stellar_points_payment | 4 | stellar_wallet | 3 |
| stellar_points_record | 4 | stellar_wallet_transaction | 2 |
| stellar_checkin_record | 3 | stellar_mall_order | 1 |

示例还原：`鐢ㄦ埛ID` → `用户ID`，`鍒涘缓鏃堕棿` → `创建时间`，`绉垎` → `积分`

---

## 六、建议保留的反范式字段（非冗余）

以下字段虽然可由其他表推导，但属于有意为之的性能优化，**不建议删除**：

- `stellar_spu` 的 `sale_count` / `total_stock` / `sku_count` / `min_price` / `max_price` / `comment_count`
  —— 由 SKU 与订单聚合而来，用于列表页免联表查询
- `stellar_sku` 的 `specs`（文本）+ `specs_json`（JSON）
  —— 前端 SkuSpecSelector.vue 依赖 specs_json 做规格选择器（17 处引用），双写有实际用途
- `stellar_spu` 的 `description`（HTML）+ `description_md`（Markdown）
  —— 后者专供 RAG 知识库同步，注释中已明确用途

---

## 七、处理建议

1. **立即执行**：修复 48 列乱码注释（无风险，仅改元数据）
2. **确认后执行**：删除 `market_price`、`slider_images` 两个死字段（执行前先备份表）
3. **暂不处理**：第二、三、四类中的预留字段，待对应功能明确后再决定

配套脚本：`sql/cleanup_redundant_fields.sql`
- 第一部分：修复注释（48 条 ALTER，已通过影子表验证，50/50 语法通过）
- 第二部分：删除冗余字段（默认注释状态，需人工确认后启用）
- 第三部分：待观察字段清单

---

## 八、另附：疑似字段缺失（非冗余，供参考）

`stellar_mall_order` 只有 `address`（地址快照），**缺少收货人姓名与电话字段**。
V2 迁移脚本中相关的两句 ALTER 处于注释状态：

```sql
-- ALTER TABLE stellar_mall_order ADD COLUMN consignee VARCHAR(50) DEFAULT '' COMMENT '收货人' AFTER address;
-- ALTER TABLE stellar_mall_order ADD COLUMN phone VARCHAR(20) DEFAULT '' COMMENT '联系电话' AFTER consignee;
```

订单仅存地址而无收货人信息，在售后/物流场景下会有缺口，建议一并补上。
