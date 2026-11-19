# 星耀商城 RAG 智能问答服务 (Stellar Mall RAG Backend)

基于 FastAPI + LangChain + LangGraph 的电商智能客服后端，提供 RAG 知识问答、Agent 工具执行、知识库管理、ChatBI 查数与 AI 经营日报能力，并作为 MCP Server 暴露商城 Agent 工具。

## 技术栈

| 组件        | 说明                                                                  |
| --------- | ------------------------------------------------------------------- |
| 框架        | FastAPI 0.115 + Uvicorn                                             |
| AI 框架     | LangChain 1.3.13 + LangGraph 1.2.9（Agent 状态机 + ReAct 子图）             |
| 检索引擎      | Elasticsearch 8.19（向量 + 倒排索引，RRF 融合）                                |
| 混合检索      | ES 向量检索 + BM25 关键词 + RRF(k=60) 融合 + BGE Reranker 精排                 |
| Embedding | DashScope `text-embedding-v2`（1536 维）；本地 BGE 降级不可用                    |
| LLM       | 默认通义千问 `qwen-plus`（可配 DeepSeek 等 OpenAI-compatible 端点）            |
| 缓存        | 三层 LLM 缓存：L1 Redis 精确 → L2 ES 语义 → L3 模型调用；另有 FAQ 进程内旁路缓存      |
| 数据库       | SQLAlchemy + PyMySQL（RAG 自有表在商城 MySQL，`stellar_rag_` 前缀）              |
| 交互协议      | REST API + SSE 流式 + MCP Server（stdio，只读工具）                          |

## 核心能力

### LangGraph Agent 状态机

5 节点确定性流程：`意图识别 → 参数检查/追问 → 工具执行 → 回答生成`，支持 16 类意图分类：

```text
[intent_classification] → 路由
    ├─→ 业务类 → [check_params] → 缺参 → [ask_params] → END
    │                             └→ 齐全 → [execute_tool] → [generate_answer] → END
    ├─→ small_talk → [generate_answer] → END
    └─→ other → [generate_answer] → [react_subgraph] → END
```
`other`（复合/多步问题）在主图内条件路由到 `react_subgraph`，事件统一经 `_emit_agent_event` 下发，服务层不再二次调用 ReAct。

### ReAct Agent（复合问题）

单意图走确定性管道；**复合/多意图问题**（如"看收藏夹→算总价→查钱包→加购物车"）自动降级到手动 ReAct 循环（Thought → Action → Observation），暴露 10 个安全子集工具：

* 只读：`kb_search`、`query_order`、`query_cart`、`query_favorite`、`query_wallet`、`query_reviews`、`product_search`、`resolve_sku`

* 可逆写：`update_cart_item`、`add_to_cart`

写操作强制 **参数来源白名单**（id 必须来自查询工具返回）+ **幂等守卫**（窗口内防重复提交），高危删除/清空/售后操作不注入工具集。

### 规格对比 / 筛选

* 规格比较类问题自动覆盖路由到知识库，叠加品类 `tags` 过滤 + 关键词改写，拉取多款商品的 spec 片段

* 商品搜索时智能补充知识库规格参数（充电功率、电池、屏幕等），供跨商品比较

* 防幻觉强约束：答案数字/商品名严格对齐工具返回值，缺失数据如实告知

### 三层 LLM 缓存

| 层级 | 存储     | 说明                                 |
| -- | ------ | ---------------------------------- |
| L1 | Redis  | 精确命中，TTL 分级（答案/意图/改写）+ 布隆快速判负   |
| L2 | ES     | 语义命中，向量检索 + 相似度阈值，写入走后台线程池       |
| L3 | 模型调用   | 兜底，结果异步落库                          |
另有 FAQ 进程内旁路缓存（仅 `product_consult` + `kb_search` 静态知识，KB 变更时清空）。所有缓存命中均在 Agent 指标中按 `cache_source` 记账。

配套优化：**布隆过滤**快速判负、**Single-flight** 防缓存击穿、缓存写入走**后台线程池异步落库**不阻塞主链路、意图识别感知多轮上下文（短回答带语境时不走缓存）。详细设计见 [`docs/llm-cache-design.md`](docs/llm-cache-design.md)。

### 其他

* 知识库管理（多格式文档：PDF / Word / TXT / MD / CSV + 图片解析）

* ChatBI：自然语言查数，SQL 生成前做列名/敏感字段黑名单校验

* AI 经营日报：自动汇总关键经营指标

* Mall 内部同步桥：Mall Java 增量同步商品文档到知识库

* 联合健康检查：RAG 主库 + Mall MySQL / Redis / API 探活

* MCP Server：通过 `python -m app.mcp_server` 将商城**只读**查询能力（商品/订单/购物车/收藏/钱包/评价/知识库/SKU）暴露为 MCP 工具。**刻意不开放任何写操作**——取消订单/确认收货/清空购物车/删除购物车项/申请售后等高危写操作只能走 C 端 Agent 的 HTTP 接口，由服务端确认机制把关；IDE 侧模型自己填 `confirm=true` 不构成真实人类确认，故 MCP 侧不提供写工具。

## 项目结构

```
rag-backend/
├── app/
│   ├── agent/             # LangGraph 状态机 + ReAct 子图
│   │   ├── graph.py          # 主状态图构建（other → react_subgraph）
│   │   ├── react_subgraph.py # ReAct 子图（统一执行入口与事件）
│   │   ├── react_agent.py    # 手写 ReAct 循环（流式 + 同步）
│   │   ├── react_safety.py   # 写操作白名单 + Redis 幂等 + 确认判定
│   │   ├── nodes.py          # 节点逻辑（意图/检查/执行/生成）
│   │   ├── tool_registry.py  # 工具元数据/风险分级/意图映射 单一来源
│   │   ├── metrics.py        # 意图/执行/缓存命中指标
│   │   ├── prompts.py        # 意图分类 / 系统提示词
│   │   ├── state.py          # AgentState 类型
│   │   └── tools/            # Agent 工具（订单/购物车/售后/收藏/钱包/搜索/SKU/评价/KB）
│   ├── api/               # REST 路由（auth/conversation/chat/knowledge_base/admin/
│   │                      #   internal 同步桥/daily_report/chat_bi/embed）
│   ├── core/              # 数据库、异常、日志、限流、安全
│   ├── models/            # SQLAlchemy 模型
│   ├── rag/               # ES 检索/embedding/LLM/llm_cache(三层缓存)/chains
│   ├── services/          # 业务层（agent/auth/conversation/kb/chat_bi/admin...）
│   ├── config.py          # pydantic-settings 配置
│   ├── dependencies.py    # 认证依赖
│   ├── main.py            # FastAPI 入口
│   └── mcp_server.py      # MCP Server
├── docs/
│   └── llm-cache-design.md   # 缓存设计（历史设计文档，已落地，部分细节见 README）
├── scripts/
│   └── agent_metrics.py      # 导出 Agent 意图/执行/缓存命中指标
├── tests/                 # pytest 测试（54 passed）
├── requirements.txt
├── .env.example
└── README.md
```

## 快速开始

### 前置依赖

* Python 3.14+（依赖已全量锁定到 `==` 版本，见 requirements.txt）

* Redis（LLM 缓存 L1 + Agent 幂等/指标，可选，缺失时降级为仅 L2 + 进程内幂等）

* Elasticsearch 8.19（检索与 L2 语义缓存；ES 离线时检索侧有 MySQL fallback）

### 1. 安装依赖

```bash
cd rag-backend
pip install -r requirements.txt
```

### 2. 配置环境

复制 `.env.example` 为 `.env`，重点配置：

```bash
LLM_API_KEY=sk-xxxx                    # 对话模型 Key（如 DeepSeek）
LLM_BASE_URL=https://api.deepseek.com/v1
LLM_MODEL_NAME=deepseek-chat
DASHSCOPE_API_KEY=sk-xxxx              # 百炼 Key，仅用于 Embedding（DeepSeek 不提供）
# 与 Mall Java 后端必须完全一致的密钥：
STELLAR_ADMIN_SECRET_KEY=...           # 对应 Mall stellar.jwt.admin-secret-key
STELLAR_USER_SECRET_KEY=...            # 对应 Mall stellar.jwt.user-secret-key
STELLAR_RAG_INTERNAL_SYNC_SECRET=...   # 对应 Mall stellar.rag.internal-sync-secret
MALL_API_BASE_URL=http://127.0.0.1:8082
```

### 3. 启动服务

```bash
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

首次启动会自动：初始化数据库、创建 `admin`/`demo` 账号、预热 RAG 组件与样例数据。也可配合根目录 `start-mall.ps1` 一键启动全部服务。

### 4. 访问文档

| 地址                             | 说明             |
| ------------------------------ | -------------- |
| <http://localhost:8000/docs>   | Swagger API 文档 |
| <http://localhost:8000/redoc>  | ReDoc API 文档   |
| <http://localhost:8000/health> | 联合健康检查         |

### 5. 以 MCP Server 运行

```bash
# stdio 模式（TRAE IDE / Claude Desktop）
python -m app.mcp_server
# 或使用 mcp CLI
mcp run app/mcp_server.py
```

## 测试

```bash
cd rag-backend
python -m pytest tests -v
```

查看 Agent 意图/执行/缓存命中指标（需 Redis）：

```bash
python scripts/agent_metrics.py --days 7
```

