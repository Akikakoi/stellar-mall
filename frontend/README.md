# 星耀商城前端 (Stellar Mall Frontend)

基于 Vue3 + Vite + Pinia + Vue Router 的电商前端，包含 C 端商城主站和管理后台两套系统，全量 TypeScript（strict + vue-tsc 类型检查）。

## 技术栈

* Vue 3.4+

* Vite 5.0+

* TypeScript（strict 模式）

* Pinia 2.1+（状态管理）

* Vue Router 4.2+

* Axios 1.6+（请求封装）

* Element Plus 2.4+（按需引入）

* ECharts（数据可视化）

## 快速开始

```bash
# 安装依赖（建议使用 npm@10 生成/对齐 package-lock.json）
npm install

# 启动开发服务器 (默认端口 5173)
npm run dev

# 生产构建（含大依赖代码分割）
npm run build

# 预览生产构建
npm run preview

# 类型检查（vue-tsc）
npm run type-check

# 单元测试（Vitest）
npm run test

# 端到端测试（Playwright）
npm run e2e
```

## 代理配置 (devServer proxy)

开发环境下 Vite dev server 自动代理请求到后端服务：

| 前端路径           | 代理目标                            | 说明        |
| -------------- | ------------------------------- | --------- |
| `/api/*`       | `http://localhost:8082/user/*`  | C 端用户 API |
| `/admin-api/*` | `http://localhost:8082/admin/*` | 管理后台 API  |
| `/rag-api/*`   | `http://localhost:8000/*`       | RAG 聊天服务  |

## JWT 鉴权说明

### Token Header 命名对齐 Java 后端

| 场景          | Header 名         | Claim 键          | 来源                                                          |
| ----------- | ---------------- | ---------------- | ----------------------------------------------------------- |
| 管理后台请求      | `token`          | `EMP_ID`         | `JwtProperties.adminTokenName` + `JwtClaimsConstant.EMP_ID` |
| 管理后台请求(兼容)  | `Authorization`  | `Bearer <token>` | RAG 通用格式                                                    |
| C 端用户请求     | `authentication` | `USER_ID`        | `JwtProperties.userTokenName` + `JwtClaimsConstant.USER_ID` |
| C 端用户请求(兼容) | `Authorization`  | `Bearer <token>` | RAG 通用格式                                                    |
| RAG 面板请求    | `Authorization`  | `Bearer <token>` | Python RAG 对齐                                               |

同时额外补充自定义 Header 用于跨端兼容：

* 管理后台: `stellaremployeeid` (值为 EMP\_ID) + `stellar-token` (值为 JWT)

* C 端用户: `stellaruserid` (值为 USER\_ID) + `stellar-token` (值为 JWT)

### Token 存储位置

* localStorage key: `stellar_user_token` / `stellar_user_id` / `stellar_user_nickname`

* localStorage key: `stellar_admin_token` / `stellar_admin_empid` / `stellar_admin_username`

## 目录结构

```
frontend/
├── src/
│   ├── api/                    # API 封装层 (TypeScript)
│   │   ├── request.ts          # Axios 实例 + 拦截器
│   │   ├── mall.ts             # C 端商城接口
│   │   ├── admin.ts            # 管理后台接口
│   │   └── rag.ts              # RAG 聊天接口
│   ├── router/
│   │   └── index.ts            # 路由配置 (C端 + 管理后台)
│   ├── stores/                 # Pinia 状态
│   │   ├── user.ts             # C 端用户状态
│   │   ├── admin.ts            # 管理员状态
│   │   ├── cart.ts             # 购物车状态
│   │   └── chat.ts             # RAG 聊天状态
│   ├── components/             # 公共组件
│   │   ├── NavHeader.vue       # 导航栏
│   │   ├── FloatingSidebar.vue # 浮动侧边栏
│   │   ├── SkuSpecSelector.vue # SKU 规格选择器
│   │   ├── ChatMessage.vue     # 聊天消息
│   │   ├── SourceCard.vue      # RAG 引用来源卡片
│   │   └── ThemeToggle.vue     # 主题切换
│   ├── composables/            # 组合式函数 (useTheme/useSiteTitle/useSearchHistory...)
│   ├── constants/              # 常量 (如订单状态)
│   ├── types/
│   │   └── models.ts           # 全局类型定义
│   ├── utils/                  # 工具函数
│   ├── views/
│   │   ├── Login.vue / Register.vue
│   │   ├── user/               # C 端页面（Home、Shop、SpuDetail、Cart、订单、
│   │   │                       #  收藏、钱包、优惠券、积分商城、售后、RagChat 等）
│   │   └── admin/              # 管理后台（Dashboard、SPU/SKU、分类、订单、
│   │                           #  商品评价、售后、优惠券、库存、积分、Banner、
│   │                           #  首页模块、知识库、ChatBI、员工、回收站、系统设置等）
│   ├── App.vue
│   └── main.ts
├── __tests__/                  # Vitest 单元测试
├── e2e/                        # Playwright 端到端测试
├── index.html
├── vite.config.js
├── vitest.config.js
├── playwright.config.js
├── tsconfig.json
├── package.json
└── README.md
```

## 测试体系

* **单元测试**（Vitest）：API 层、请求拦截器、Pinia stores、工具函数、组件

* **端到端测试**（Playwright）：核心下单流程（smoke + order-flow）

* 支持 `vue-tsc` 严格类型检查，CI 中自动执行

## 测试与 CI

> 说明：以仓库根目录为准，参见根 [`README.md`](../README.md) 的总体架构、启动方式与部署说明。

