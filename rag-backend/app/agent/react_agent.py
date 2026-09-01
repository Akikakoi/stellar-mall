"""P1 ReAct Agent 原型(手动实现,不依赖 langgraph.prebuilt)。

背景:当前环境 langchain 1.x + langgraph 1.2.9,其 prebuilt 模块与版本不匹配,
`create_react_agent` 不可用。故本原型用手动 ReAct 循环:
    Thought(模型推理) -> Action(调用工具) -> Observation(观察结果) -> ...
仅依赖 langchain-core 稳定的接口: bind_tools / AIMessage.tool_calls / ToolMessage。

安全:所有工具调用都经 react_safety.safe_invoke 包裹——
    - 只读工具直接放行;
    - 写工具强制"参数来源白名单"(sourced_ids) + 幂等检查;
    - 高危写工具(cancel/clear/after_sales 等)不注入本工具集,统一留给确定性管道。

P1 暴露工具集(10 个): 8 只读(kb_search/query_order/query_cart/query_favorite/
query_wallet/query_reviews/product_search/resolve_sku) + 2 可逆写(update_cart_item/add_to_cart)。
"""
from __future__ import annotations

import asyncio
import json
from typing import List, Optional

from langchain_core.messages import SystemMessage, HumanMessage, AIMessage, ToolMessage
from langchain_core.tools import tool

from app.core.logger import logger
from app.rag.llm import get_langchain_chat
from app.agent.react_safety import safe_invoke, IdempotencyGuard
from app.agent.tools import (
    kb_search_tool,
    query_order_tool,
    query_cart_tool,
    query_favorite_tool,
    query_wallet_tool,
    query_reviews_tool,
    product_search_tool,
    update_cart_item_tool,
    add_to_cart_tool,
)
from app.agent.tools.resolve_sku_tool import resolve_sku_tool

# 幂等窗口:同一写操作在窗口期内只允许执行一次(防 ReAct 循环重复提交)
_IDEMPOTENCY_TTL = 300

SYSTEM_PROMPT = """你是一位电商平台智能客服，精通商品咨询、订单查询、购物车管理等。

你必须按"思考-行动-观察"(ReAct)的方式一步步解决问题：
1. 先思考用户问题的意图，决定需要哪个工具；
2. 调用一个工具获取信息；
3. 观察工具返回结果；
4. 如信息不足，继续调用下一个工具；信息足够后，直接给出最终回答。

规则：
- 只调用“购物车/订单中用户本人已查询到”的编号；查询类工具返回结果里出现的商品/订单/购物车编号才可使用。
- 用户让你修改购物车(改数量)时，先调用 query_cart 拿到购物车记录 id，再用该 id 更新。
- 用户让你"把某商品加入购物车"时，先调用 resolve_sku（商品名+规格）拿到可信 sku_id，再用该 sku_id 调用 add_to_cart。
- 不要编造任何编号；不要执行删除、清空、取消订单、售后等高风险操作(这些不在你的工具范围内，请引导用户走人工流程)。
- 能直接回答时不要再调用工具。
"""

# 供外部查看 P1 暴露了哪些工具
REACT_SAFE_TOOLS: List[str] = [
    "kb_search",
    "query_order",
    "query_cart",
    "query_favorite",
    "query_wallet",
    "query_reviews",
    "product_search",
    "resolve_sku",
    "update_cart_item",
    "add_to_cart",
]

# 从工具返回结果中提取的"可信标识"字段，用于写操作的参数来源白名单
_ID_KEYS = ("id", "spu_id", "sku_id", "cart_item_id", "order_id", "order_no")


class ReactContext:
    """单次 ReAct 运行的上下文：注入运行时用的凭据 + 累积可信 id 白名单。"""

    def __init__(self, mall_token: Optional[str] = None, user_id: Optional[int] = None):
        self.mall_token = mall_token
        self.user_id = user_id
        self.sourced_ids: set = set()  # 可信标识白名单，来自查询工具的返回结果
        # 每次 run 独立幂等守卫，避免不同用户之间的写操作互相误判重复
        self.guard = IdempotencyGuard(max_entries=32, ttl_seconds=_IDEMPOTENCY_TTL)


def _collect_ids(ctx: ReactContext, result: dict) -> None:
    """递归收集工具返回里的 id 类字段，加入 ctx.sourced_ids。"""
    if not isinstance(result, dict):
        return

    def _walk(x):
        if isinstance(x, dict):
            for k, v in x.items():
                if k in _ID_KEYS and v not in (None, ""):
                    ctx.sourced_ids.add(str(v))
                _walk(v)
        elif isinstance(x, list):
            for i in x:
                _walk(i)

    _walk(result)


def _fmt_result(result: dict) -> str:
    """把工具返回 dict 序列化成观察文本。"""
    try:
        return json.dumps(result, ensure_ascii=False, default=str)
    except Exception:  # noqa: BLE001
        return str(result)


# ---------- 工具包装(每工具一个闭包,捕获 ctx,内部注入凭据 + 走 safe_invoke) ----------

def _wrapped_kb(ctx: ReactContext):
    @tool
    def _kb_search_tool(query: str, top_k: int = 5) -> dict:
        """知识库检索：查询商品参数、售后政策、使用说明等静态知识。"""
        return safe_invoke("kb_search",
                           {"query": query, "top_k": top_k},
                           kb_search_tool)
    return _kb_search_tool


def _wrapped_query_order(ctx: ReactContext):
    @tool
    def _query_order_tool(order_id: Optional[str] = None, status: Optional[int] = None) -> dict:
        """订单查询：查看指定订单详情或全部订单列表。order_id/status 可不填。"""
        return safe_invoke("query_order",
                           {"order_id": order_id, "status": status,
                            "mall_token": ctx.mall_token},
                           query_order_tool)
    return _query_order_tool


def _wrapped_query_cart(ctx: ReactContext):
    @tool
    def _query_cart_tool() -> dict:
        """购物车查询：查看当前用户购物车商品列表。"""
        r = safe_invoke("query_cart", {"mall_token": ctx.mall_token}, query_cart_tool)
        _collect_ids(ctx, r)
        return r
    return _query_cart_tool


def _wrapped_query_favorite(ctx: ReactContext):
    @tool
    def _query_favorite_tool() -> dict:
        """收藏夹查询：查看当前用户收藏的商品。"""
        r = safe_invoke("query_favorite", {"mall_token": ctx.mall_token}, query_favorite_tool)
        _collect_ids(ctx, r)
        return r
    return _query_favorite_tool


def _wrapped_query_wallet(ctx: ReactContext):
    @tool
    def _query_wallet_tool() -> dict:
        """钱包查询：查看当前用户钱包余额与消费统计。"""
        r = safe_invoke("query_wallet", {"mall_token": ctx.mall_token}, query_wallet_tool)
        _collect_ids(ctx, r)
        return r
    return _query_wallet_tool


def _wrapped_query_reviews(ctx: ReactContext):
    @tool
    def _query_reviews_tool(spu_id: str, page: int = 1, page_size: int = 10) -> dict:
        """商品评价查询：查询指定商品(spu_id)的用户评价与评分。"""
        r = safe_invoke("query_reviews", {"spu_id": spu_id, "page": page, "page_size": page_size,
                                          "mall_token": ctx.mall_token},
                        query_reviews_tool)
        _collect_ids(ctx, r)
        return r
    return _query_reviews_tool


def _wrapped_product_search(ctx: ReactContext):
    @tool
    def _product_search_tool(query: str, page: int = 1, page_size: int = 10,
                             category: Optional[str] = None) -> dict:
        """商品搜索：按名称/分类搜索商城在售商品，返回商品列表与价格。"""
        r = safe_invoke("product_search",
                        {"query": query, "page": page, "page_size": page_size,
                         "category": category, "mall_token": ctx.mall_token},
                        product_search_tool)
        _collect_ids(ctx, r)
        return r
    return _product_search_tool


def _wrapped_resolve_sku(ctx: ReactContext):
    @tool
    def _resolve_sku_tool(product_name: str, spec_description: Optional[str] = None) -> dict:
        """SKU 解析：根据商品名称和规格描述，查找匹配的 sku_id。加购前先调此工具拿到可信 sku_id。"""
        r = safe_invoke("resolve_sku",
                        {"product_name": product_name, "spec_description": spec_description,
                         "mall_token": ctx.mall_token},
                        resolve_sku_tool)
        _collect_ids(ctx, r)
        return r
    return _resolve_sku_tool


def _wrapped_update_cart_item(ctx: ReactContext):
    @tool
    def _update_cart_item_tool(cart_item_id: str, qty: Optional[int] = None,
                               checked: Optional[int] = None) -> dict:
        """修改购物车商品：按 cart_item_id 修改数量(qty)或勾选状态(checked=1/0)。"""
        return safe_invoke("update_cart_item",
                           {"cart_item_id": cart_item_id, "qty": qty, "checked": checked,
                            "mall_token": ctx.mall_token},
                           update_cart_item_tool,
                           sourced_ids=ctx.sourced_ids,
                           idempotency_guard=ctx.guard)
    return _update_cart_item_tool


def _wrapped_add_to_cart(ctx: ReactContext):
    """加购工具包装：可逆写操作，强制 sku_id 来源白名单 + 幂等检查。"""
    @tool
    def _add_to_cart_tool(sku_id: str, qty: int = 1) -> dict:
        """加入购物车：将指定 sku_id 的商品加入购物车，qty 为该商品购买数量。"""
        return safe_invoke("add_to_cart",
                           {"sku_id": sku_id, "qty": qty,
                            "mall_token": ctx.mall_token},
                           add_to_cart_tool,
                           sourced_ids=ctx.sourced_ids,
                           idempotency_guard=ctx.guard)
    return _add_to_cart_tool


def _build_tools(ctx: ReactContext) -> list:
    return [
        _wrapped_kb(ctx),
        _wrapped_query_order(ctx),
        _wrapped_query_cart(ctx),
        _wrapped_query_favorite(ctx),
        _wrapped_query_wallet(ctx),
        _wrapped_query_reviews(ctx),
        _wrapped_product_search(ctx),
        _wrapped_resolve_sku(ctx),
        _wrapped_update_cart_item(ctx),
        _wrapped_add_to_cart(ctx),
    ]


# 工具调用在 async 上下文里同步执行会阻塞事件循环，故用 to_thread 包一层
def _invoke_tool_sync(fn, args):
    return fn.invoke(args)


def _build_messages(conversation_history: Optional[list], query: str) -> list:
    messages: list = [SystemMessage(content=SYSTEM_PROMPT)]
    for role, content in (conversation_history or []):
        if role == "user":
            messages.append(HumanMessage(content=content))
        elif role == "assistant":
            messages.append(AIMessage(content=content))
    messages.append(HumanMessage(content=query))
    return messages


def _pseudo_stream_chunks(text: str, chunk_size: int = 24):
    """伪流式分块：按标点/空白优先、固定长度兜底切成小块逐块 yield，降低纯表现层延迟。"""
    punct = "。！？，、；,.!?;:：\n\t "
    buf = ""
    for ch in text:
        buf += ch
        if ch in punct or len(buf) >= chunk_size:
            yield buf
            buf = ""
    if buf:
        yield buf


def run_react_agent(
    query: str,
    user_id: Optional[int] = None,
    mall_token: Optional[str] = None,
    conversation_history: Optional[list] = None,
    max_steps: int = 6,
    llm=None,
) -> dict:
    """运行 ReAct agent，返回结果。

    Args:
        query: 用户问题
        mall_token: 商城用户 token
        user_id: 用户 ID
        conversation_history: 历史对话 [(role, content), ...]，可选
        max_steps: ReAct 最大迭代步数上限
        llm: 可注入的 LLM(测试用)，默认 get_langchain_chat()

    Returns:
        {
            "answer": str,
            "steps": int,
            "tool_trace": [{"reasoning", "tool", "args"}, ...],
            "sources": list,
        }
    """
    ctx = ReactContext(mall_token=mall_token, user_id=user_id)
    tools = _build_tools(ctx)
    tool_by_name = {t.name: t for t in tools}

    if llm is None:
        llm = get_langchain_chat()
    bound = llm.bind_tools(tools)

    messages = _build_messages(conversation_history, query)

    tool_trace: list = []
    answer = ""
    sources: list = []

    for step in range(max_steps):
        ai = bound.invoke(messages)
        messages.append(ai)

        reasoning = (ai.content or "").strip() if isinstance(ai.content, str) else ""
        tool_calls = getattr(ai, "tool_calls", None) or []

        if not tool_calls:
            answer = reasoning or str(ai.content)
            break

        for tc in tool_calls:
            name = tc.get("name", "")
            args = tc.get("args", {})
            _id = tc.get("id", "")
            logger.info(f"[React] step={step} tool={name} args={args}")
            tool_trace.append({"reasoning": reasoning, "tool": name, "args": args})

            fn = tool_by_name.get(name)
            if fn is None:
                messages.append(ToolMessage(content=f"未知工具: {name}", tool_call_id=_id))
                continue

            try:
                result = fn.invoke(args)
            except Exception as e:  # noqa: BLE001
                result = {"success": False, "message": f"工具执行异常: {e}"}
            _collect_ids(ctx, result)

            # 聚合引用来源(只读检索类工具会返回 sources)
            if isinstance(result, dict):
                src = result.get("sources")
                if src:
                    sources.extend(src if isinstance(src, list) else [src])

            messages.append(ToolMessage(content=_fmt_result(result), tool_call_id=_id))
    else:
        # 达到 max_steps 仍未给出最终回答
        logger.warning(f"[React] 达到最大步数 {max_steps}，未收敛")
        last = messages[-1]
        answer = last.content if isinstance(last.content, str) else str(last.content)

    return {
        "answer": answer,
        "steps": max(1, min(len(tool_trace) + 1, max_steps)),
        "tool_trace": tool_trace,
        "sources": sources,
    }


async def astream_react(
    query: str,
    user_id: Optional[int] = None,
    mall_token: Optional[str] = None,
    conversation_history: Optional[list] = None,
    max_steps: int = 6,
    llm=None,
):
    """ReAct 的流式版本：把 ReAct 过程与最终回答以事件实时 yield。

    Yields(与 agent_service 现有 SSE 协议一致):
        {"type": "agent_thought", "content"}    思考过程
        {"type": "tool_call", "tool", "params"} 工具调用
        {"type": "tool_result", "tool", "success", "message"} 工具结果
        {"type": "token", "content"}            最终回答 token(伪流式，逐字)
        {"type": "sources", "data"}             引用来源
        {"type": "done", "tokens", "tool_trace"} 完成

    说明：工具决策轮用同步 invoke 快速拿到 tool_calls，将思考以 agent_thought
    实时透出；最终答案轮整段按字符拆分 yield，给前端逐字显现的流式体验。
    """
    ctx = ReactContext(mall_token=mall_token, user_id=user_id)
    tools = _build_tools(ctx)
    tool_by_name = {t.name: t for t in tools}

    if llm is None:
        llm = get_langchain_chat()
    bound = llm.bind_tools(tools)

    messages = _build_messages(conversation_history, query)
    tool_trace: list = []
    sources: list = []

    for step in range(max_steps):
        ai = await asyncio.to_thread(bound.invoke, messages)
        messages.append(ai)

        reasoning = (ai.content or "").strip() if isinstance(ai.content, str) else ""
        tool_calls = getattr(ai, "tool_calls", None) or []

        if not tool_calls:
            answer = reasoning or str(ai.content)
            if sources:
                yield {"type": "sources", "data": sources}
            for chunk in _pseudo_stream_chunks(answer):
                yield {"type": "token", "content": chunk}
                await asyncio.sleep(0.005)
            yield {"type": "done", "tokens": len(answer), "tool_trace": tool_trace}
            return

        if reasoning:
            yield {"type": "agent_thought", "content": reasoning}

        for tc in tool_calls:
            name = tc.get("name", "")
            args = tc.get("args", {})
            _id = tc.get("id", "")
            tool_trace.append({"reasoning": reasoning, "tool": name, "args": args})
            yield {"type": "tool_call", "tool": name, "params": args}

            fn = tool_by_name.get(name)
            if fn is None:
                result = {"success": False, "message": f"未知工具: {name}"}
            else:
                try:
                    result = await asyncio.to_thread(_invoke_tool_sync, fn, args)
                except Exception as e:  # noqa: BLE001
                    result = {"success": False, "message": f"工具执行异常: {e}"}

            _collect_ids(ctx, result)
            if isinstance(result, dict):
                src = result.get("sources")
                if src:
                    sources.extend(src if isinstance(src, list) else [src])

            yield {
                "type": "tool_result",
                "tool": name,
                "success": bool(result.get("success", False)),
                "message": result.get("message", ""),
            }
            messages.append(ToolMessage(content=_fmt_result(result), tool_call_id=_id))

    # 达到 max_steps 仍未产出答案兜底
    logger.warning("[React] 流式 ReAct 达到最大步数，未收敛")
    fallback = "抱歉，我尝试了几步仍没能完全回答您的问题，您能补充一下吗？"
    for chunk in _pseudo_stream_chunks(fallback):
        yield {"type": "token", "content": chunk}
        await asyncio.sleep(0.005)
    yield {"type": "done", "tokens": len(fallback), "tool_trace": tool_trace}


__all__ = ["run_react_agent", "astream_react", "REACT_SAFE_TOOLS", "SYSTEM_PROMPT"]