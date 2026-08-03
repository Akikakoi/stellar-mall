# Stellar Mall LLM 缓存设计文档

> 版本：v1.0  
> 日期：2026-08-01  
> 作者：Stellar Mall 团队  
> 状态：设计阶段

---

## 一、文档概述

### 1.1 背景

Stellar Mall 的 RAG 智能问答子系统（rag-backend）通过 LangGraph Agent + ChromaDB 知识库为用户提供商品咨询、订单查询等 AI 能力。每条请求至少经过 **2 次 LLM 调用**（意图识别 + 答案生成），每次调用耗时 1-5 秒、成本约 0.001-0.003 元。目前缓存仅覆盖 RAG 直连路径且命中率极低，Agent 路径完全无缓存。

电商场景中，商品问答具有显著的 **长尾重复性**——热门商品（iPhone、充电器）承载 80% 以上咨询量，且用户问题高度集中（"多少钱""有什么功能""和 XX 比哪个好"）。引入 LLM 缓存可获得显著的延迟和成本收益。

### 1.2 目标

| 指标 | 当前值 | 目标值 |
|------|--------|--------|
| 平均端到端延迟（Agent 模式） | 3-7 秒 | <1 秒（缓存命中时） |
| 综合缓存命中率 | ~0%（Agent 路径） | 30-40% |
| 意图识别命中率 | 0% | 50-70% |
| 单次请求 LLM 成本 | ￥0.0027 | ￥0.0018（综合） |
| 缓存引入的额外延迟 | — | <50ms |

### 1.3 非目标

- 不涉及 Prompt 缓存（上下文前缀的 KV-cache），那是推理引擎层面的事
- 不涉及前端 HTTP 缓存（ETag / CDN）
- 不替换现有 `_QueryCache`，而是在其之上增强

---

## 二、现状分析

### 2.1 现有缓存

当前 `rag/retriever.py:130-194` 有一个 `_QueryCache` 类：

| 属性 | 当前值 |
|------|--------|
| 存储 | Python dict（内存） |
| 容量 | 128 条 |
| TTL | 300 秒 |
| 相似度阈值 | 0.97 |
| 匹配方式 | Embedding 余弦相似度 |
| 覆盖路径 | 仅 `chains.py` RAG 直连路径 |
| 持久化 | 无（重启丢失） |
| 多进程共享 | 不支持 |

### 2.2 当前 LLM 调用点

| 位置 | 文件 | 行号 | 调用方式 | 可否缓存 |
|------|------|------|----------|----------|
| 意图识别 | `agent/nodes.py` | 200 | `llm.invoke()` 同步 | **是（高优）** |
| 答案生成（Agent） | `agent/nodes.py` | 786 | `llm.stream()` 流式 | **是（高优）** |
| 答案生成（RAG） | `rag/chains.py` | 171 | `llm.astream()` 异步流式 | **是（高优）** |
| 查询改写 | `rag/chains.py` | 99 | `llm.ainvoke()` 异步 | 是（中优） |
| 缺参追问 | `agent/nodes.py` | 444 | `llm.invoke()` 同步 | 否（命中率极低） |
| 闲聊兜底 | `agent/nodes.py` | 806 | `llm.invoke()` 同步 | 否（命中率极低） |

### 2.3 现有基础设施

项目已具备以下可复用的组件：

- **Redis 7.2**（Java 后端同一实例，`docker-compose.yml`，127.0.0.1:6379，db=11，maxmemory=256MB，淘汰策略 allkeys-lru）
- **ChromaDB**（`ecommerce_knowledge_base` collection，持久化在 `./data/chroma`）
- **Embedding 服务**（DashScope `text-embedding-v2` 优先，本地 `BAAI/bge-large-zh-v1.5` 兜底，1024 维）
- **BGE Reranker**（本地 FlagEmbedding，`rerank` 函数已存在）
- **商品同步管道**（`api/internal.py` SPU/DOC 同步，含 `spu_id` / `min_price` 等 metadata）

**不需要引入任何新组件**。

---

## 三、架构设计

### 3.1 三层缓存模型

```
用户请求
  │
  ▼
┌────────────────────────────────────────────────┐
│  L1: Redis 精确缓存                              │
│  Key: SHA256(query + model + temp + sys_hash)   │
│  延迟: <5ms                                     │
│  命中率: 5-10%（精确匹配）                        │
└──────────┬─────────────────────────────────────┘
           │ miss
           ▼
┌────────────────────────────────────────────────┐
│  L2: Chroma 语义缓存                             │
│  Collection: llm_semantic_cache                 │
│  匹配: Embedding 相似度 + Reranker 二次确认       │
│  延迟: ~50ms                                    │
│  命中率: 15-25%（语义相似）                       │
└──────────┬─────────────────────────────────────┘
           │ miss
           ▼
┌────────────────────────────────────────────────┐
│  L3: 真实 LLM 调用                               │
│  延迟: 1-5 秒                                   │
│  成本: ￥0.002-0.003 / 次                        │
│  结果异步回写 L1 + L2                            │
└────────────────────────────────────────────────┘
```

### 3.2 模块结构

```
app/
  rag/
    llm_cache.py          ← 新增：LLM 缓存核心模块
    chains.py             ← 修改：接入 L1/L2 缓存
  agent/
    nodes.py              ← 修改：接入 L1/L2 缓存
  config.py               ← 修改：新增缓存配置项
```

### 3.3 模块接口设计

`app/rag/llm_cache.py` 对外暴露三个核心函数：

```python
async def get_cached_answer(
    query: str,
    model: str,
    temperature: float,
    system_prompt_hash: str,
    context_hash: Optional[str] = None,
) -> Optional[Dict[str, Any]]:
    """
    查询缓存。按 L1 → L2 顺序检查，命中则返回缓存结果。
    
    返回值:
        {
            "answer": "...",
            "sources": [...],
            "intent": "...",
            "tokens_used": 123
        }
    未命中返回 None。
    """

async def put_cached_answer(
    query: str,
    model: str,
    temperature: float,
    system_prompt_hash: str,
    context_hash: Optional[str],
    answer: str,
    sources: List[dict],
    intent: str,
    tokens_used: int,
    product_ids: Optional[List[int]] = None,
) -> None:
    """异步写入 L1 + L2 缓存（不阻塞主流程）。"""

async def invalidate_by_product_ids(product_ids: List[int]) -> int:
    """按商品 ID 精准失效缓存。返回清除条数。"""

async def invalidate_by_prompt_hash(system_prompt_hash: str) -> int:
    """Prompt 模板变更时全量失效。"""
```

---

## 四、各层详细设计

### 4.1 L1 — Redis 精确缓存

#### 4.1.1 Key 设计

```
格式: llm:exact:{sha256_hex}
内容: query + "\n" + model + "\n" + str(temperature) + "\n" + system_prompt_hash + "\n" + context_hash

SHA256 输入示例（实际使用时拼接为单个字符串）:
"iPhone 15 多少钱\nqwen-plus\n0.3\na1b2c3d4\ne5f6g7h8"
```

**context_hash 的作用**：区分相同用户问题但不同检索结果（例如知识库更新后同一 query 召回不同文档）。如果缓存时不区分 context，可能导致返回过期答案。

- **RAG 路径**：`context_hash = sha256(",".join(sorted(doc_ids_of_retrieved_chunks)))`
- **Agent 路径**：`context_hash = sha256(json.dumps(tool_results, sort_keys=True))`

#### 4.1.2 Value 设计

```json
{
  "v": 1,
  "answer": "iPhone 15 目前售价 5999 元起...",
  "sources": [
    {"doc_name": "iPhone 15 规格.md", "chunk_index": 3, "score": 0.85}
  ],
  "intent": "product_consult",
  "tokens_used": 456,
  "ts": 1754067600
}
```

- `v`：版本号，便于后续数据结构升级
- `ts`：时间戳，用于监控和统计

#### 4.1.3 TTL

| 缓存类型 | TTL | 理由 |
|----------|-----|------|
| 意图识别 | 3600s（1小时） | 意图几乎不变 |
| 答案生成 | 7200s（2小时） | 商品信息半衰期 |
| 查询改写 | 1800s（30分钟） | 多轮对话上下文变化快 |

#### 4.1.4 连接复用

rag-backend 通过 `redis-py` 异步客户端连接 Redis（与 Java 后端共享同一 Redis 实例，使用 db=11）。使用连接池避免每次查询建连。

```python
import redis.asyncio as aioredis

# 初始化（在 FastAPI lifespan 中完成）
redis_client = aioredis.from_url(
    "redis://127.0.0.1:6379/11",
    max_connections=16,
    socket_timeout=2,
    socket_connect_timeout=1,
)
```

**容错**：Redis 不可用时所有读写操作静默降级（返回 None），不影响 LLM 主流程。

---

### 4.2 L2 — Chroma 语义缓存

#### 4.2.1 Collection 设计

**新建 collection**：`llm_semantic_cache`（与现有的 `ecommerce_knowledge_base` 独立）

```python
# 创建
chroma_client.create_collection(
    name="llm_semantic_cache",
    metadata={"hnsw:space": "cosine"}
)
```

#### 4.2.2 Document 结构

存入时的 metadata 字段：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `answer` | str | 是 | LLM 原始回答 |
| `query` | str | 是 | 用户原始问题（用于追溯和调试） |
| `model` | str | 是 | 模型名（qwen-plus / qwen-max） |
| `temperature` | float | 是 | 温度参数 |
| `system_prompt_hash` | str | 是 | Prompt 模板 SHA256 |
| `context_hash` | str | 是 | 检索结果/工具结果 SHA256 |
| `intent` | str | 是 | 意图分类结果 |
| `sources_json` | str | 否 | JSON 序列化的引用来源 |
| `tokens_used` | int | 否 | Token 用量 |
| `created_at` | float | 是 | Unix 时间戳（用于 TTL 清理） |
| `hit_count` | int | 否 | 命中计数（默认 0，用于热度排序） |
| `product_ids` | str | 否 | 逗号分隔的商品 ID 列表（用于精准失效） |

向量值：`query` 的 Embedding（复用现有 `text-embedding-v2` / `bge-large-zh-v1.5`）。

#### 4.2.3 检索与判重流程

```
query → Embedding
  → Chroma llm_semantic_cache 向量检索 (k=5)
  → 过滤：model、temperature、system_prompt_hash 完全匹配
  → BGE Reranker 精排（query vs 前5条候选的 query 字段）
  → 最高 rerank_score > 0.95 → 命中
  → 否则 → miss
```

**为什么用 Reranker 而非单纯的 Embedding 相似度**：

| 方法 | 阈值 | 典型准确率 | 说明 |
|------|------|-----------|------|
| Embedding 余弦相似度 | 0.95 | ~85% | 容易误判"苹果手机多少钱"和"苹果股票多少钱"相似 |
| Reranker 精排 | 0.95 | ~98% | Cross-encoder 逐对打分，区分度更高 |

你项目已有本地 BGE Reranker（`FlagEmbedding`），额外延迟约 10-20ms，换来准确率的大幅提升。

#### 4.2.4 容量与淘汰

- **最大容量**：5000 条（可配置）
- **淘汰策略**：
  - 惰性清理：检索时跳过 `created_at + TTL < now()` 的过期条目
  - 定期清理：每 30 分钟扫描一次，删除过期条目
  - 容量满时：淘汰 `hit_count` 最低的 20%

#### 4.2.5 TTL

| 缓存类型 | TTL |
|----------|-----|
| 意图识别 | 3600s |
| 答案生成 | 7200s |
| 查询改写 | 1800s |

与 L1 Redis TTL 保持一致。

---

### 4.3 L3 — 真实 LLM 调用（现状不变）

L1 + L2 全 miss 时走原有 LLM 调用流程。调用完成后 **异步** 回写 L1 和 L2：

```python
# 伪代码
answer = await llm_call(...)
asyncio.create_task(put_cached_answer(..., answer, ...))
return answer  # 不等待缓存写入完成
```

回写失败不影响用户应答（try-catch + 日志告警）。

---

## 五、接入点

### 5.1 意图识别缓存 — `agent/nodes.py:intent_classification_node`

**优先级**：P1（预期命中率 50-70%）  
**缓存层**：L1（Redis 精确）+ L2（Chroma 语义）  
**Key 特殊性**：意图识别不依赖 context（工具结果），key 不包含 context_hash

**接入伪代码**：

```python
def intent_classification_node(state):
    query = _get_last_user_query(state)
    
    # === 缓存查询 ===
    intent_result = await get_cached_answer(
        query=query,
        model=LLM_MODEL_NAME,
        temperature=LLM_TEMPERATURE,
        system_prompt_hash=INTENT_PROMPT_HASH,   # 固定常量
        context_hash=None,                        # 意图不依赖 context
    )
    if intent_result:
        return {
            "intent": intent_result["intent"],
            "intent_confidence": intent_result.get("confidence", 0.9),
        }
    
    # === 原逻辑：调用 LLM ===
    llm = get_langchain_chat()
    result = llm.invoke(...)
    intent = result.get("intent")
    confidence = result.get("confidence")
    
    # === 异步回写缓存 ===
    asyncio.create_task(put_cached_answer(
        query=query,
        model=LLM_MODEL_NAME,
        temperature=LLM_TEMPERATURE,
        system_prompt_hash=INTENT_PROMPT_HASH,
        context_hash=None,
        answer=json.dumps(result),
        sources=[],
        intent=intent,
        tokens_used=...,
    ))
    
    return {"intent": intent, "intent_confidence": confidence}
```

### 5.2 Agent 答案生成缓存 — `agent/nodes.py:generate_answer_node`

**优先级**：P0（预期命中率 25-35%）  
**缓存层**：L1 + L2  
**context_hash**：`sha256(json.dumps(tool_result, sort_keys=True))`  
**system_prompt_hash**：`sha256(AGENT_SYSTEM_PROMPT)`

**接入伪代码**：

```python
def generate_answer_node(state):
    query = _get_last_user_query(state)
    intent = state.get("intent")
    tool_result = state.get("tool_results", {})
    
    # 生成 context_hash
    context_hash = sha256(json.dumps(tool_result, sort_keys=True))
    
    # === 缓存查询 ===
    cached = await get_cached_answer(
        query=query,
        model=LLM_MODEL_NAME,
        temperature=LLM_TEMPERATURE,
        system_prompt_hash=AGENT_PROMPT_HASH,
        context_hash=context_hash,
    )
    if cached:
        return {
            "final_answer": cached["answer"],
            "stream_chunks": list(cached["answer"]),  # 伪流式
            "from_cache": True,
        }
    
    # === 原逻辑：流式调用 LLM ===
    answer_chunks = []
    for chunk in llm.stream([sys_msg, user_msg]):
        ...
    full_answer = "".join(answer_chunks)
    
    # === 异步回写 ===
    product_ids = _extract_product_ids(tool_result)
    asyncio.create_task(put_cached_answer(
        query=query,
        model=LLM_MODEL_NAME,
        temperature=LLM_TEMPERATURE,
        system_prompt_hash=AGENT_PROMPT_HASH,
        context_hash=context_hash,
        answer=full_answer,
        sources=[],
        intent=intent,
        tokens_used=...,
        product_ids=product_ids,
    ))
    
    return {"final_answer": full_answer, "stream_chunks": answer_chunks}
```

### 5.3 RAG 答案生成缓存 — `rag/chains.py:astream_answer_with_sources`

**优先级**：P0  
**缓存层**：L1 + L2  
**context_hash**：`sha256(",".join(sorted([s.doc_id for s in ranked_docs])))`

现有代码已有 `_QueryCache` 的接入点（`chains.py:122-130`）。改造方案：**将 `get_query_cache().get()` 替换为新的 `get_cached_answer()` 调用**。

```python
async def astream_answer_with_sources(query, ...):
    # 1) 先查缓存（替换原有 _QueryCache）
    cached = await get_cached_answer(
        query=query,
        model=LLM_MODEL_NAME,
        temperature=LLM_TEMPERATURE,
        system_prompt_hash=RAG_PROMPT_HASH,
        context_hash=None,  # 此时还没有检索结果，context_hash 在写缓存时补充
    )
    if cached:
        for ch in cached["answer"]:
            yield {"type": "token", "content": ch}
        yield {"type": "sources", "data": cached["sources"]}
        yield {"type": "done", "tokens": cached.get("tokens_used", 0)}
        return
    
    # 2-5) 原有流程...
    ...
    
    # 回写时带上 context_hash
    context_hash = sha256(",".join(sorted([s["doc_id"] for s in sources])))
    asyncio.create_task(put_cached_answer(
        query=query, ...,
        context_hash=context_hash,
        ...
    ))
```

**注意**：RAG 路径的缓存查询在两阶段进行：
- **检索前**：不带 `context_hash`，仅语义匹配问题本身
- **写入时**：带上实际的 `context_hash`，后续检索结果相同时精确命中

### 5.4 查询改写缓存 — `rag/chains.py:_rewrite_query_if_needed`

**优先级**：P1  
**缓存层**：仅 L1（改写结果短，语义维度低）  
**Key**：`sha256(query + "\n".join(history))`

### 5.5 不需要缓存的部分

- **缺参追问**（`agent/nodes.py:444`）：高随机性，命中率 <5%
- **闲聊兜底**（`agent/nodes.py:806`）：低频，不值得
- **工具调用本身**：走 Mall Java 后端 API，自身已有 Spring Cache

---

## 六、失效策略

### 6.1 自动过期（TTL）

| 缓存 | TTL | 机制 |
|------|-----|------|
| L1 Redis 答案 | 7200s | Redis `EXPIRE`，到期自动删除 |
| L1 Redis 意图 | 3600s | Redis `EXPIRE` |
| L1 Redis 改写 | 1800s | Redis `EXPIRE` |
| L2 Chroma 答案 | 7200s | 惰性扫描 + 定时清理 |
| L2 Chroma 意图 | 3600s | 同 |

### 6.2 精准失效（按商品 ID）

**触发条件**：Mall 后端 SPU 信息变更（价格、库存、规格等）。

**流程**：
```
Mall Java 后端 SPU 更新
  → 发布事件 / 调用 rag-backend 内部接口
  → rag-backend: invalidate_by_product_ids([123, 456])
    → L2 Chroma：metadata.product_ids 包含任一 ID → 标记失效
    → L1 Redis：精确 key 已自动过期（2h TTL），无需额外操作
```

**实现方式**：利用你现有的 `api/internal.py` 商品同步管道。在 SPU 同步成功后追加一行清除逻辑：

```python
# internal.py sync_spu 函数末尾
await invalidate_by_product_ids([spu_id])
```

### 6.3 批量失效

| 场景 | 操作 | 影响范围 |
|------|------|----------|
| Prompt 模板变更 | `invalidate_by_prompt_hash(old_hash)` | 仅旧模板的缓存 |
| 知识库全量重建 | `invalidate_by_collection("*")` | L2 Chroma 全部清除，L1 自然过期 |
| 模型升级（qwen-plus → qwen-max） | 全量清除 | L1 + L2 全部 |

### 6.4 缓存穿透防护

对于恶意高频查询相同 miss 的问题，加 **布隆过滤器** 或记录 miss 计数：

```
intent_cache_miss:qwen-plus:0.3:INTENT_HASH:total   → 记录 miss 总数
intent_cache_miss:qwen-plus:0.3:INTENT_HASH:{query_hash}  → 单 query miss 计数（TTL=60s）
```

连续 miss 同一 query 超过 3 次 → 暂时不写缓存（可能是异常流量）。

---

## 七、配置项

在 `app/config.py` 新增以下配置：

```python
# ============ LLM 缓存 ============

# L1 Redis 缓存
LLM_CACHE_REDIS_ENABLED: bool = True
LLM_CACHE_REDIS_TTL_ANSWER: int = 7200       # 答案缓存 TTL（秒）
LLM_CACHE_REDIS_TTL_INTENT: int = 3600       # 意图缓存 TTL（秒）
LLM_CACHE_REDIS_TTL_REWRITE: int = 1800      # 改写缓存 TTL（秒）

# L2 Chroma 语义缓存
LLM_CACHE_SEMANTIC_ENABLED: bool = True
LLM_CACHE_SEMANTIC_MAXSIZE: int = 5000        # 最大条目数
LLM_CACHE_SEMANTIC_TTL_ANSWER: int = 7200     # 答案缓存 TTL（秒）
LLM_CACHE_SEMANTIC_TTL_INTENT: int = 3600     # 意图缓存 TTL（秒）
LLM_CACHE_SEMANTIC_RANK_THRESHOLD: float = 0.95  # Reranker 命中阈值
LLM_CACHE_SEMANTIC_K: int = 5                 # 召回候选数

# 全局开关
LLM_CACHE_ENABLED: bool = True                # 总开关

# 监控
LLM_CACHE_METRICS_ENABLED: bool = True        # 是否记录命中率指标
```

**与现有配置的共存**：现有 `QUERY_CACHE_*` 配置保留，与新的 `LLM_CACHE_*` 独立运作。新的 L1/L2 缓存上线后，原有的 `_QueryCache` 可以逐步弃用。

---

## 八、监控与运维

### 8.1 统计指标

通过日志埋点 + Prometheus 指标暴露：

| 指标 | 说明 | 采集方式 |
|------|------|----------|
| `llm_cache_hit_total{layer=l1|l2}` | L1/L2 命中次数 | Counter |
| `llm_cache_miss_total` | 未命中次数 | Counter |
| `llm_cache_hit_ratio` | 综合命中率 | `hit / (hit + miss)` |
| `llm_cache_latency_ms{layer=l1|l2}` | 各层查询延迟 | Histogram |
| `llm_cache_write_failures_total` | 写入失败次数 | Counter |
| `llm_cache_size{layer=l1|l2}` | 缓存条目数 | Gauge |
| `llm_cache_revenue_saved` | 节省的 LLM 费用 | 估算（命中数 × 单次成本） |

### 8.2 日志规范

```
[LLMCache] L1_HIT    query="iPhone 15 多少钱" latency_ms=2
[LLMCache] L2_HIT    query="苹果15 价格"      latency_ms=48  rerank_score=0.97
[LLMCache] MISS      query="宇宙飞船怎么买"    latency_ms=55
[LLMCache] WRITE_OK  query="iPhone 15 多少钱" layers=L1+L2
[LLMCache] WRITE_ERR query="..."             error="Redis timeout"
```

### 8.3 管理端点

在 `api/admin.py` 新增缓存管理接口（需 ADMIN 角色）：

```
GET  /api/admin/cache/stats          → 缓存统计（命中率、大小、节省成本）
POST /api/admin/cache/clear          → 清空指定层缓存
POST /api/admin/cache/invalidate     → 按 product_ids 精准失效
```

### 8.4 Redis key 分析

定期执行 Redis `INFO KEYSPACE` 和 `--bigkeys` 分析：

```bash
redis-cli -n 11 INFO keyspace
redis-cli -n 11 --bigkeys
```

监控 db=11 的内存占用，确保不会挤占 Java 后端的 Redis 缓冲区（当前 maxmemory=256MB，预估 LLM 缓存占用 <20MB）。

---

## 九、实施计划

### Phase 1：基础设施（1 天）

- [ ] 新建 `app/rag/llm_cache.py` 模块，实现 `get_cached_answer()` / `put_cached_answer()` / `invalidate()` 三个接口
- [ ] 在 FastAPI `lifespan` 中初始化 Redis 异步客户端
- [ ] 在 Chroma 中创建 `llm_semantic_cache` collection
- [ ] 新增 `config.py` 配置项
- [ ] 编写单元测试

### Phase 2：接入 Agent 路径（1 天）

- [ ] 意图识别节点接入缓存
- [ ] 答案生成节点接入缓存
- [ ] 保持流式 SSE 兼容（缓存命中时模拟伪流式输出）
- [ ] 端到端测试（命中 / 未命中 / Redis 降级）

### Phase 3：接入 RAG 路径（0.5 天）

- [ ] `chains.py` 替换 `_QueryCache` 为新的 `get_cached_answer()`
- [ ] 查询改写接入 L1 缓存

### Phase 4：失效与监控（1 天）

- [ ] `api/internal.py` 同步流程接入 `invalidate_by_product_ids()`
- [ ] 日志埋点
- [ ] 管理端点
- [ ] Grafana 面板配置

### Phase 5：灰度与迭代（持续）

- [ ] 先在开发环境验证 1 周，观察命中率
- [ ] 根据实际命中率调参（阈值、TTL、容量）
- [ ] 全量上线后监控 2 周，确认收益
- [ ] 根据数据决定是否引入布隆过滤器、预生成缓存等高级特性

---

## 十、风险与预案

| 风险 | 概率 | 影响 | 预案 |
|------|------|------|------|
| Redis 连接超时 | 低 | 缓存全 miss，走原始 LLM | 超时设为 1s，降级不影响主流程 |
| Chroma 检索变慢 | 中 | L2 查询耗时增加 | 超时 100ms，超时只返回 L1 结果 |
| 缓存返回过期答案 | 中 | 用户看到错误信息 | 2h TTL + 商品变更精准失效 |
| Reranker 性能瓶颈 | 低 | 语义缓存延迟 > 100ms | 限制并发、考虑异步预热 |
| 缓存内存占用过大 | 低 | Redis OOM | maxmemory-policy=allkeys-lru 自动淘汰 |

---

## 十一、效果预估（基于 1000 次/天问答量）

### 11.1 成本

| 项目 | 无缓存 | 有缓存（35%命中率） | 节省 |
|------|--------|---------------------|------|
| 每日 LLM 调用次数 | 2000 次 | 1300 次 | -35% |
| 每日 LLM 费用 | ¥2.70 | ¥1.86 | -¥0.84/天 |
| 每月 LLM 费用 | ¥81 | ¥56 | -¥25/月 |
| Embedding 增量成本 | ¥0 | ~¥0.05/天 | — |

### 11.2 延迟

| 阶段 | 无缓存 | 缓存命中 | 提升 |
|------|--------|----------|------|
| 意图识别 | 800-1500ms | <10ms | 99% |
| 答案生成 | 2000-5000ms | <10ms | 99% |
| 缓存查询 | — | 5-50ms | — |
| **端到端** | **3-7s** | **0.3-0.6s** | **85-90%** |

### 11.3 首字延迟（TTFB）用户体验对比

```
无缓存: 用户输入 → ...等待... → 2-3秒后首字出现 → 逐字输出
有缓存: 用户输入 → 100ms 内 → 完整答案直接呈现（伪流式）
```

---

## 附录 A：Prompt Hash 常量定义

```python
# 这些 hash 在系统启动时计算一次，缓存在内存中
import hashlib

# agent/prompts.py 中的 prompt 模板
INTENT_PROMPT_HASH = hashlib.sha256(INTENT_CLASSIFICATION_PROMPT.encode()).hexdigest()
AGENT_PROMPT_HASH = hashlib.sha256(AGENT_SYSTEM_PROMPT.encode()).hexdigest()

# rag/chains.py 中的 prompt 模板
RAG_PROMPT_HASH = hashlib.sha256(SYSTEM_PROMPT.encode()).hexdigest()
REWRITE_PROMPT_HASH = hashlib.sha256(REWRITE_PROMPT.encode()).hexdigest()
```

## 附录 B：Redis 连接配置

```python
# rag-backend 连接 Java 后端共享的 Redis（db=11）
REDIS_URL = "redis://127.0.0.1:6379/11"
REDIS_MAX_CONNECTIONS = 16
REDIS_SOCKET_TIMEOUT = 2       # 不宜太长，缓存 miss 走 LLM
REDIS_SOCKET_CONNECT_TIMEOUT = 1
REDIS_RETRY_ON_TIMEOUT = False  # 不重试，缓存 miss 可接受
```

## 附录 C：Chroma metadata 查询示例

```python
# 按 product_ids 精准失效
collection.get(
    where={
        "$or": [
            {"product_ids": {"$contains": "123"}},
            {"product_ids": {"$contains": "456"}},
        ]
    }
)
# 注意：Chroma 的 $contains 是子串匹配，"123" 会匹配 "123,456" 和 "1123"
# 存储时 product_ids 前后加逗号防止误匹配：",123,456,"
```

---

> **文档维护**：本文档随 LLM 缓存功能的一同迭代。重大架构变更请更新本文档并通知团队。
