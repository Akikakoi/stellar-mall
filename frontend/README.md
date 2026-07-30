# 星耀商城前端 (Stellar Mall Frontend)

基于 Vue3 + Vite + Pinia + Vue Router 的电商前端脚手架，包含 C 端用户商城和管理后台两套系统。

## 技术栈

- Vue 3.4+
- Vite 5.0+
- Pinia 2.1+
- Vue Router 4.2+
- Axios 1.6+
- Element Plus 2.4+

## 快速开始

```bash
# 安装依赖
npm install

# 启动开发服务器 (默认端口 5173)
npm run dev

# 生产构建
npm run build

# 预览生产构建
npm run preview
```

## 代理配置 (devServer proxy)

开发环境下 Vite dev server 会自动代理请求到后端服务：

| 前端路径 | 代理目标 | 说明 |
|---------|---------|------|
| `/api/*` | `http://localhost:8082/user/*` | C 端用户 API |
| `/admin-api/*` | `http://localhost:8082/admin/*` | 管理后台 API |
| `/rag-api/*` | `http://localhost:8000/*` | RAG 聊天服务 |

## JWT 鉴权说明

### Token Header 命名对齐 Java 后端

| 场景 | Header 名 | Claim 键 | 来源 |
|-----|----------|---------|------|
| 管理后台请求 | `token` | `EMP_ID` | `JwtProperties.adminTokenName` + `JwtClaimsConstant.EMP_ID` |
| 管理后台请求(兼容) | `Authorization` | `Bearer <token>` | RAG 通用格式 |
| C 端用户请求 | `authentication` | `USER_ID` | `JwtProperties.userTokenName` + `JwtClaimsConstant.USER_ID` |
| C 端用户请求(兼容) | `Authorization` | `Bearer <token>` | RAG 通用格式 |
| RAG 面板请求 | `Authorization` | `Bearer <token>` | Python RAG 对齐 |

同时额外补充自定义 Header 用于跨端兼容：
- 管理后台: `stellaremployeeid` (值为 EMP_ID) + `stellar-token` (值为 JWT)
- C 端用户: `stellaruserid` (值为 USER_ID) + `stellar-token` (值为 JWT)

### Token 存储位置

- localStorage key: `stellar_user_token` / `stellar_user_id` / `stellar_user_nickname`
- localStorage key: `stellar_admin_token` / `stellar_admin_empid` / `stellar_admin_username`

## 目录结构

```
frontend/
├── src/
│   ├── api/                    # API 封装层
│   │   ├── request.js          # Axios 实例 + 拦截器
│   │   ├── mall.js             # C 端商城接口
│   │   ├── admin.js            # 管理后台接口
│   │   └── rag.js              # RAG 聊天接口
│   ├── router/
│   │   └── index.js            # 路由配置 (C端 + 管理后台)
│   ├── stores/                 # Pinia 状态管理
│   │   ├── user.js             # C 端用户状态
│   │   ├── admin.js            # 管理员状态
│   │   └── cart.js             # 购物车状态
│   ├── views/
│   │   ├── Login.vue           # C 端登录
│   │   ├── Register.vue        # C 端注册
│   │   ├── user/               # C 端页面
│   │   │   ├── Home.vue
│   │   │   ├── SpuDetail.vue
│   │   │   ├── Cart.vue
│   │   │   ├── OrderList.vue
│   │   │   ├── OrderSubmit.vue
│   │   │   └── Profile.vue
│   │   └── admin/              # 管理后台页面
│   │       ├── _Layout.vue     # 后台布局 (侧边栏 + 内容区)
│   │       ├── Dashboard.vue
│   │       ├── SpuMgmt.vue
│   │       ├── CategoryMgmt.vue
│   │       ├── RagSyncQueue.vue
│   │       └── EmployeeMgmt.vue
│   ├── App.vue
│   └── main.js
├── index.html
├── vite.config.js
├── package.json
└── README.md
```
