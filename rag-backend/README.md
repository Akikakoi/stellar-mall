# 星耀商城 RAG 智能问答服务 (Stellar Mall RAG Backend)

基于 FastAPI + LangChain + LangGraph 的电商智能客服后端，提供 RAG 知识问答、Agent 工具执行、知识库管理、ChatBI 查数与 AI 经营日报能力，并作为 MCP Server 暴露商城 Agent 工具。

## 技术栈

| 组件        | 说明                                                                  |
| --------- | ------------------------------------------------------------------- |
| 框架        | FastAPI 0.115 + Uvicorn                                             |
| AI 框架     | LangChain 1.x + LangChain Core + LangGraph（Agent 状态机 + ReAct）       |
| 向量库       | ChromaDB（持久化）                                                       |
| 混合检索      | Chroma 向量检索 + BM25 关键词 + BGE Reranker 精排                            |
| Embedding | 本地 BGE（`BAAI/bge-large-zh-v1.5`），失败降级 DashScope `text-embedding-v2` |
| LLM       | 通义千问（DashScope，`qwen-plus` / `qwen-turbo` / `qwen-max`）             |
| 缓存        | 三层 LLM 缓存：L1 Redis 精确 → L2 Chroma 语义（本地余弦精排）→ L3 模型调用               |
| 数据库       | SQLAlchemy（默认 SQLite，可切 MySQL）                                      |
| 交互协议      | 同时提供 REST API 与 MCP Server（stdio）                                   |

## 核心能力

### LangGraph Agent 状态机

5 节点确定性流程：`意图识别 → 参数检查/追问 → 工具执行 → 回答生成`，支持 16 类意图分类：

```text
[intent_classification] → 路由
    ├─→ 业务类 → [check_params] → 缺参 → [ask_params] → END
    │                             └→ 齐全 → [execute_tool] → [generate_answer] → END
    └─→ small_talk / other → [generate_answer] → END
```

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
| L1 | Redis  | 精确命中，TTL 分级（答案/意图/改写）              |
| L2 | Chroma | 语义命中，本地余弦精排替换云端 rerank，消除约 1.3s 往返 |
| L3 | 模型调用   | 兜底，结果异步落库                          |

配套优化：**布隆过滤**快速判负、**Single-flight** 防缓存击穿、缓存写入走**后台线程池异步落库**不阻塞主链路、意图识别感知多轮上下文（短回答带语境时不走缓存）。详细设计见 [`docs/llm-cache-design.md`](docs/llm-cache-design.md)。

### 其他

* 知识库管理（多格式文档：PDF / Word / TXT / MD / CSV + 图片解析）

* ChatBI：自然语言查数，SQL 生成前做列名/敏感字段黑名单校验

* AI 经营日报：自动汇总关键经营指标

* Mall 内部同步桥：Mall Java 增量同步商品文档到知识库

* 联合健康检查：RAG 主库 + Mall MySQL / Redis / API 探活

* MCP Server：通过 `python -m app.mcp_server` 将商城 Agent 工具暴露为 MCP 工具

## 项目结构

```
rag-backend/
├── app/
│   ├── agent/             # LangGraph 状态机 + ReAct Agent
│   │   ├── graph.py       # 状态图构建
│   │   ├── nodes.py       # 节点逻辑（意图/检查/执行/生成）
│   │   ├── react_agent.py # 手动 ReAct 循环（流式 + 同步）
│   │   ├── react_safety.py# 写操作白名单 + 幂等守卫
│   │   ├── prompts.py     # 意图分类 / 系统提示词
│   │   ├── state.py       # AgentState 类型
│   │   └── tools/         # Agent 工具（订单/购物车/售后/收藏/钱包/搜索/SKU/评价/KB）
│   ├── api/               # REST 路由（auth/conversation/chat/knowledge_base/admin/
│   │                      #   internal 同步桥/daily_report/chat_bi/embed）
│   ├── core/              # 数据库、异常、日志、限流、安全
│   ├── models/            # SQLAlchemy 模型
│   ├── rag/               # 检索/向量/embedding/LLM/llm_cache/chains
│   ├── services/          # 业务层（agent/auth/conversation/kb/rag_chat/admin...）
│   ├── config.py          # pydantic-settings 配置
│   ├── dependencies.py    # 认证依赖
│   ├── main.py            # FastAPI 入口
│   └── mcp_server.py      # MCP Server
├── docs/
│   └── llm-cache-design.md
├── tests/                 # pytest 测试
├── requirements.txt
├── .env.example
└── README.md
```

## 快速开始

### 前置依赖

* Python 3.14+（requirements.txt 已针对 1.x 系 langchain 对齐）

* Redis（LLM 缓存 L1，可选，缺失时降级为 Chroma-only）

### 1. 安装依赖

```bash
cd rag-backend
pip install -r requirements.txt
```

### 2. 配置环境

复制 `.env.example` 为 `.env`，重点配置：

```bash
DASHSCOPE_API_KEY=sk-xxxx              # 通义千问 API Key
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

