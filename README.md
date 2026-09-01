# Stellar Mall（星耀商城）

全栈电商平台，集成 AI 智能问答与 Agent 能力。

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.3.6 + MyBatis + Maven 多模块 |
| 前端 | Vue 3 + Vite + Element Plus + Pinia + ECharts |
| 数据库 | MySQL 8.0 |
| 缓存 | Redis 7.2 |
| 搜索引擎 | Elasticsearch 7.17（IK 分词器，MySQL 降级兜底） |
| 对象存储 | Aliyun OSS |
| AI/RAG | FastAPI + LangChain + LangGraph（5 节点状态机 + ReAct Agent）+ ChromaDB + BM25 + BGE Reranker |
| AI 缓存 | Redis 三层 LLM 缓存（L1 精确 + L2 语义 + L3 模型），布隆过滤 + Single-flight + 异步写 + 本地余弦精排 |
| BI 智能分析 | ChatBI 自然语言查数 + AI 经营日报 |
| 支付 | 微信支付 V3 |
| 鉴权 | JWT（用户端 + 管理端双密钥） |
| 基础设施 | Docker Compose（ES + MySQL + Redis） |

## 项目结构

```
stellar-mall/
├── stellar-common/          # 公共模块（工具类、常量、异常定义）
├── stellar-pojo/            # 数据对象（Entity、DTO、VO、Mapper）
├── stellar-server/          # 主服务（Controller、Service、配置）
├── frontend/                # Vue 3 前端（商城 + 管理后台）→ [README](frontend/README.md)
├── rag-backend/             # RAG 智能问答服务（FastAPI）→ [README](rag-backend/README.md)
├── docker/                  # Docker 构建文件
├── docker-compose.yml       # 基础设施编排
└── start-mall.ps1           # Windows 一键启动脚本
```

## 核心功能

### 电商基础
- 商品管理：SPU/SKU 模型，多级分类，多图上传，商品搜索（ES + MySQL 降级）
- 购物车：多规格选择，数量调整，批量操作
- 订单系统：下单、支付（微信支付 V3）、取消、退款、积分抵扣
- 地址簿：多地址管理，默认地址，收货地址联动
- 优惠券：领取、使用、过期管理
- 售后保障：延保 / 保险服务费全链路

### 积分系统
- 积分获取：签到、下单、评价等多场景自动发放
- 积分消费：订单抵扣（100 积分 = 1 元）、商城兑换
- 积分管理：365 天过期策略，乐观锁并发控制，完整流水追溯

### AI 智能问答（RAG + Agent）
- 基于 LangGraph 的 5 节点确定性状态机（意图识别 → 参数检查/追问 → 工具执行 → 回答生成），16 类意图分类
- 复合/多意图问题自动降级到 ReAct Agent（Thought → Action → Observation），暴露 10 个安全子集工具（知识库检索、订单/购物车/收藏夹/钱包/评价查询、商品搜索、SKU 解析、改数量、加购物车），写操作带参数来源白名单 + 幂等守卫
- 规格对比/筛选类问题自动路由到知识库，叠加品类 tags 过滤 + 关键词改写，多商品补全规格参数
- ChromaDB 向量库 + BM25 关键词混合检索 + BGE Reranker 精排
- 三层 LLM 缓存：L1 Redis 精确匹配 → L2 Chroma 语义匹配（本地余弦精排）→ L3 模型调用；布隆过滤快速布隆判负、Single-flight 防缓存击穿、缓存写入走后台线程池异步落库
- 知识库管理（管理端 CRUD + JWT 鉴权 + 图片/多媒体解析）
- 防幻觉强约束：答案数字/商品名必须严格对齐工具返回值，缺失数据如实告知

### BI 智能分析
- ChatBI：管理端自然语言查数，SQL 生成前做列名/敏感字段黑名单校验
- AI 经营日报：自动汇总关键经营指标生成运营日报

### 管理后台
- 商品管理：SPU/SKU 增删改查，Excel 导入导出，多图上传
- 订单管理、用户管理、积分规则配置
- 首页模块化渲染（轮播图、推荐位可配置）
- Knife4j API 文档（开发环境）

### 前端特性
- 暗色主题切换，响应式布局
- 商品详情页极简轮播图（仿小米/红米风格）
- 收藏页可配置每行 2/3/4/5 件
- 首页模块化组件，管理端可配置

## 快速开始

### 前置条件

- JDK 17+
- Node.js 18+
- Python 3.10+
- Docker Desktop

### 1. 启动基础设施

```bash
docker compose up -d
```

启动 MySQL（3307）、Redis（6379）、Elasticsearch（9200）。

### 2. 初始化数据库

在 MySQL 中创建 `stellar_mall` 数据库，首次建库先执行基础表结构与初始化数据（各一次）：
`stellar-server/src/main/resources/sql/stellar_mall_ddl.sql`、`stellar_mall_init_data.sql`。
后续增量迁移由 Flyway 在应用启动时自动执行（`db/migration/` 下 V2 起的脚本），无需手工操作。

### 3. 配置密钥

后端 `stellar-server/src/main/resources/application-dev.yml`（参考 `.example` 文件）：
- 数据库连接信息
- Aliyun OSS AccessKey
- JWT 签名密钥
- 微信支付配置

RAG 后端 `rag-backend/.env`（参考 `.env.example`）：
- DashScope API Key
- JWT 密钥（需与后端一致）
- 内部同步密钥

### 4. 一键启动（Windows）

```powershell
.\start-mall.ps1
```

自动完成依赖安装、配置校验、端口冲突检测，依次启动后端（8082）、RAG 服务（8000）、前端（5173）。

### 5. 手动启动

**后端：**
```bash
cd stellar-server
../mvnw spring-boot:run
```

**RAG 服务：**
```bash
cd rag-backend
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

**前端：**
```bash
cd frontend
npm install
npm run dev
```

### 6. 访问

| 地址 | 说明 |
|------|------|
| http://localhost:5173 | 商城首页 |
| http://localhost:5173/admin/login | 管理后台 |
| http://localhost:8082/doc.html | API 文档（Knife4j） |
| http://localhost:8000/docs | RAG 服务文档（Swagger） |

## License

MIT
