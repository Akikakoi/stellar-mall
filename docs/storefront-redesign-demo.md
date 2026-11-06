# 商城前端重设计 Demo

访问路径：`/demo/storefront`。原首页、搜索页和商品详情页均未替换，可安全对比。

## 这版 Demo 验证什么

- 首页视觉：暖白杂志感、电光蓝主色、非对称首屏，移动端同步适配。
- 核心浏览链路：商品搜索、分类筛选、快速详情、SKU 切换和数量选择。
- 接口对接：直接复用 Spring Boot 的公开只读接口，不建立另一套前端商品模型。
- 异常状态：加载骨架、空结果、接口失败回退、图片加载失败的本地矢量图。
- 数据边界：Demo 不写购物车、不创建订单；点击购买进入现有完整商品详情页。

## 前后端契约

| 页面能力 | 后端接口 | 对齐字段 |
| --- | --- | --- |
| 商品列表 / 搜索 / 分类 | `GET /user/spu/page` | 请求 `page / pageSize / name / categoryId / status`；响应 `records / total` |
| 分类 | `GET /user/category/list` | `Category[]`，保留真实 `id / name / children` |
| 快速详情 / SKU | `GET /user/spu/{id}` | `Spu`、`skuList / specs / price / stock` |

统一响应 `{ code, msg, data }` 继续由 `src/api/request.ts` 解包。Demo 的参数适配集中在 `src/api/storefrontDemo.ts`，视图不拼 URL，也不猜测返回字段。

## 两种数据状态

- 顶栏显示 `LIVE · 后端已连接`：当前商品数据来自 `8082` 后端，经 Vite 代理访问。
- 顶栏显示 `DEMO · 本地数据`：后端未启动或请求失败，使用字段结构相同的只读演示数据。点击该标记可重新连接。

这样可以把“页面效果”和“接口是否在线”明确区分，避免把本地演示数据误认为真实联调成功。

## 本地运行

```powershell
cd frontend
npm run dev -- --host 0.0.0.0 --port 5173
```

打开 `http://localhost:5173/demo/storefront`。需要真实数据时，再启动现有 `8082` Spring Boot 服务并点击顶栏的数据状态标记。

## 如果继续重做完整前端

1. 先冻结接口契约，以 Java DTO / VO 为准补充 OpenAPI 类型生成，替换重复的手写类型。
2. 保持旧路由可用，按「首页 → 搜索 → 详情 → 购物车 → 下单」逐页平行迁移，每页都做真实接口验收。
3. 把本页的颜色、字体、间距和按钮沉淀为设计 Token；商品卡、SKU 选择器等业务组件复用，不复制页面代码。
4. 每个页面至少覆盖加载、空数据、接口失败、无权限、移动端五种状态。
5. 联调时验证请求参数、统一响应解包、鉴权、写操作幂等和错误提示，再做构建与 E2E 回归。

## 主要文件

- `frontend/src/views/demo/StorefrontDemo.vue`：页面与交互。
- `frontend/src/components/demo/DemoProductVisual.vue`：真实商品图与本地矢量占位图。
- `frontend/src/api/storefrontDemo.ts`：只读 API 适配层。
- `frontend/src/data/storefrontDemo.ts`：同构演示数据。
