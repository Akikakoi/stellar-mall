"""LangChain RAG 主链路：查询改写 + Prompt + 流式生成 + 引用来源。"""
from __future__ import annotations
import asyncio
import hashlib
from typing import AsyncGenerator, List, Optional, Tuple

from app.config import settings
from app.core.logger import logger
from app.rag.llm import get_langchain_chat
from app.rag.retriever import hybrid_retrieve, rerank
from app.rag.llm_cache import get_llm_cache, RAG_PROMPT_HASH, REWRITE_PROMPT_HASH


def _fix_garbled_tags(text: str) -> str:
    """修复 ChromaDB 中可能存在的 GBK→Latin-1 编码错乱。"""
    if not text or all(ord(c) < 128 or '\u4e00' <= c <= '\u9fff' or c.isspace() for c in text):
        return text
    try:
        fixed = text.encode('latin-1').decode('gbk')
        if any('\u4e00' <= c <= '\u9fff' for c in fixed):
            return fixed
    except (UnicodeEncodeError, UnicodeDecodeError):
        pass
    return text


# ---------------------------------------------------------
# Prompt 模板（电商客服场景）
# ---------------------------------------------------------
SYSTEM_PROMPT = """你是「星耀商城」的智能导购 **小星**。
你懂硬件、看得懂参数表、能一眼判断"这个配置到底值不值"。

【说话方式】
- 参数对比是你的主场——你天生喜欢甩表格。充电功率、屏幕分辨率、芯片跑分，如数家珍。
- 偶尔用生活化的类比让参数落地。比如"120W 快充什么概念？刷个牙的功夫就充差不多了"。
- 不堆砌术语。对方明显是小白时，用"反应很快、很流畅"代替"内存带宽吞吐量"。
- 回答简洁。能一句话说清的绝不用两句。重点是让对方"懂了"而不是"听完了"。
- 提到热销商品可以小小开个玩笑。比如玉米手机："名字像农作物，配置是真的猛"。但仅限一句，不频繁 cue。

【行为准则】
- **数据第一**：必须基于下面提供的【知识库参考片段】或【商城搜索结果】回答，禁止编造任何参数。
- **诚实**：产品的短板要敢说。参数查不到时直说"我这边的资料里没写，要不你去官方看看？"，不瞎编。
- **有观点**：对比时主动标出差异项，用一句话给出判断——"如果你更看重充电速度，选 A；如果你预算有限，B 够用了"。
- **禁止幻觉**：商品名称必须与参考片段/商城返回 100%% 一致，禁止近音字替换（"玉米"≠"红米"）。表格中标"暂无"而非胡编。
- 可以适当使用 Markdown（表格、列表、加粗），让对比一目了然。

【你是谁】
- 星耀商城的导购小星，一个懂数码、有判断力的朋友。你不是复读机，你给建议。
- 新对话第一次打招呼时简单介绍自己：比如"嗨，我是小星，星耀商城的导购。想找什么？"
- 不需要每轮都自我介绍。
"""

REWRITE_PROMPT = """你是一个查询改写助手。给定【历史对话】和【用户当前问题】，请将用户当前问题改写为一个**完整、独立、无指代**的中文查询，以便用于知识库检索。
- 如果用户问题本身完整（没有"它/这个/那个/参数/多少钱"这类指代），可原样返回。
- 如果用户引入新知识域，请直接改写覆盖新内容。
- 请只输出改写后的查询文本，不要任何前缀、后缀或解释。
"""


def _format_context(ranked_docs: List[Tuple]) -> Tuple[str, list]:
    """拼接参考片段，返回 (context_text, sources_list)。"""
    sources = []
    lines = []
    for i, (doc, score) in enumerate(ranked_docs, start=1):
        meta = doc.metadata or {}
        src = {
            "id": i,
            "doc_name": str(meta.get("doc_name", "未知文档")),
            "chunk_index": int(meta.get("chunk_index", 0)),
            "page": meta.get("page"),
            "content": doc.page_content,
            "score": float(score),
            "tags": _fix_garbled_tags(meta.get("tags") or ""),
        }
        sources.append(src)
        lines.append(f"---参考片段 [{i}]  来源:{src['doc_name']}  相关度:{score:.3f}  页码:{src['page']}---")
        lines.append(doc.page_content)
    return "\n\n".join(lines), sources


def _build_history_window(history: List[Tuple[str, str]], max_turns: int = 6) -> List[Tuple[str, str]]:
    """保留最近 N 轮对话。"""
    return list(history[-max_turns:]) if history else []


# ---------------------------------------------------------------------------
# Query Rewrite 条件化
# ---------------------------------------------------------------------------
# 改写是一次完整 LLM 往返（1-2s）。多轮对话下原实现【每次都改写】，
# 但绝大多数追问本身已自足（"星耀X100的充电功率"、"保修政策是什么"），
# 改写只会原样返回，白白多花一次往返。这里先用零成本启发式判断是否需要改写。
#
# 本模块顶层不要 import re（历史上 chains.py 无该依赖），在函数内局部导入。
def _query_needs_rewrite(query: str, history: List[Tuple[str, str]]) -> bool:
    """判断当前 query 是否真的需要改写（零 LLM 成本启发式）。

    返回 True 表示「需要改写」，即存在指代/省略，脱离上下文无法独立检索。
    返回 False 表示「已自足」，直接用原 query 检索即可。

    判定思路：
      1. 无历史 → 自足。
      2. 含指代词/省略触发词 → 需要改写（"它/这个/那个/还有呢/那...呢"）。
      3. 含明确领域名词 → 自足（"发票怎么开"这类短问也已完整，不依赖上下文）。
      4. 长度足够且不含指代 → 自足（长问句通常已含完整实体）。
      5. 其余模糊短问 → 需要改写（保守，避免召回变差）。
    """
    import re

    if not history:
        return False

    q = (query or "").strip()
    if not q:
        return False

    # 指代 / 省略触发词：命中即需改写
    # 注意：中文指示代词组合极多（这台/这条/这批/这只…），用「这/那 + 量词」的宽松形式覆盖，
    # 只写死"这个/这款"会漏掉"这台多少钱"这类高频追问。
    _REF_PATTERNS = (
        r'(它|它们|这台|这条|这批|这[个款种只件部套])',
        r'(那台|那条|那批|那[个款种只件部套])',
        r'(该款|此款|此商品|该商品|这个商品)',
        r'(还有|还有呢|别的|其他的|其它的|另外|再来|再推荐|换一个|换一款|其他的呢)',
        r'(呢|吗)\s*[?？]?$',          # "那电池呢" / "价格呢"
        r'^(那|那么|然后|接着|所以|继续)',
        r'(上面|刚才|之前|前面|刚说|你说的|推荐的那)',
    )
    if any(re.search(p, q) for p in _REF_PATTERNS):
        return True

    # 明确领域名词：出现即说明问句自带主题，无需依赖上下文补全实体
    _TOPIC_NOUNS = (
        "发票", "保修", "质保", "退换", "退货", "换货", "退款", "物流", "快递", "运费",
        "配送", "发货", "收货", "政策", "规则", "流程", "说明", "参数", "规格", "配置",
        "充电", "电池", "屏幕", "像素", "内存", "存储", "处理器", "芯片", "摄像头",
        "价格", "多少钱", "优惠", "优惠券", "积分", "会员", "支付", "订单", "售后",
        "安装", "维修", "保养", "激活", "序列号", "防伪",
    )
    if any(n in q for n in _TOPIC_NOUNS):
        return False

    # 无指代且足够长 → 视为自足，跳过改写
    if len(q) >= settings.QUERY_REWRITE_MIN_LEN:
        return False

    # 短问句又无指代（如"怎么样""有货吗"）→ 语义不完整，保守走改写
    return True


def _rewrite_cache_key(query: str, history: List[Tuple[str, str]]) -> str:
    """改写缓存 key：只依赖上一轮用户问句 + 当前 query（避免整段历史抖动导致不命中）。"""
    last_user = ""
    for role, content in reversed(history or []):
        if role == "user":
            last_user = content or ""
            break
    raw = f"{last_user}\n>>>\n{query}"
    return "llm:rewrite:" + hashlib.sha256(raw.encode("utf-8")).hexdigest()


def _rewrite_cache_get(key: str) -> Optional[str]:
    """从 L1 Redis 读改写结果（同步，短超时，失败静默）。"""
    try:
        from app.rag.llm_cache import _redis_sync, _redis_available
        if not _redis_available or not _redis_sync:
            return None
        val = _redis_sync.get(key)
        return val if isinstance(val, str) and val else None
    except Exception:
        return None


def _rewrite_cache_put(key: str, value: str) -> None:
    """写入改写结果到 L1 Redis（失败静默）。"""
    try:
        from app.rag.llm_cache import _redis_sync, _redis_available
        if not _redis_available or not _redis_sync:
            return
        _redis_sync.setex(key, settings.LLM_CACHE_REDIS_TTL_REWRITE, value)
    except Exception:
        pass


async def _rewrite_query_if_needed(query: str, history: List[Tuple[str, str]]) -> str:
    """按需改写 query：命中缓存的改写结果 / 判断无需改写时直接返回原 query。

    优化点（对比原实现）：
      1. 自足问句直接跳过（不调 LLM）；
      2. 改写结果按 (上一轮问句, 当前问句) 入 L1 缓存，重复追问零成本命中；
      3. 失败静默回退原 query，行为与原实现一致。
    """
    if not settings.QUERY_REWRITE_ENABLED:
        return query
    if not history or len(history) < 1:
        return query

    # 1) 零成本启发式：自足问句直接跳过改写
    if not _query_needs_rewrite(query, history):
        logger.info(f"[QueryRewrite] 跳过（问句已自足）: {query[:40]!r}")
        return query

    # 2) 改写结果缓存（同一上一轮 + 同一追问 → 复用）
    ck = _rewrite_cache_key(query, history)
    cached = _rewrite_cache_get(ck)
    if cached:
        logger.info(f"[QueryRewrite] 命中缓存: {query[:30]} -> {cached[:30]}")
        return cached

    try:
        llm = get_langchain_chat()
        from langchain_core.messages import HumanMessage, SystemMessage, AIMessage
        msgs = [SystemMessage(content=REWRITE_PROMPT)]
        for r, c in history[-6:]:
            if r == "user":
                msgs.append(HumanMessage(content=c))
            elif r == "assistant":
                msgs.append(AIMessage(content=c))
        msgs.append(HumanMessage(content=f"当前用户问题：{query}\n改写后的独立查询："))
        resp = await llm.ainvoke(msgs)
        rewritten = (resp.content or "").strip()
        logger.info(f"[QueryRewrite] 原: {query[:30]} -> 新: {rewritten[:30]}")
        if rewritten:
            _rewrite_cache_put(ck, rewritten)
            return rewritten
        return query
    except Exception as e:  # noqa
        logger.warning(f"查询改写失败，使用原 query: {e}")
        return query


async def astream_answer_with_sources(
    query: str,
    history: Optional[List[Tuple[str, str]]] = None,
    top_k: Optional[int] = None,
    tags_filter: Optional[List[str]] = None,
    use_rewrite: Optional[bool] = None,
) -> AsyncGenerator[dict, None]:
    """RAG 主链路，按事件 yield：
    - {"type": "token", "content": "..."}
    - {"type": "sources", "data": [...]}
    - {"type": "done", "tokens": N}
    """
    history = history or []
    # 1) 先查缓存（三层：L1 Redis 精确 + L2 Chroma 语义）
    cache = get_llm_cache()
    hit = await cache.get(
        query=query,
        model=settings.LLM_MODEL_NAME,
        temperature=settings.LLM_TEMPERATURE,
        system_prompt_hash=RAG_PROMPT_HASH,
        cache_type="answer",
    )
    if hit:
        for ch in hit["answer"]:
            yield {"type": "token", "content": ch}
        yield {"type": "sources", "data": hit.get("sources", [])}
        yield {"type": "done", "tokens": hit.get("tokens_used", 0)}
        return

    # 2) 查询改写
    if use_rewrite is None:
        use_rewrite = settings.QUERY_REWRITE_ENABLED
    if use_rewrite:
        effective_query = await _rewrite_query_if_needed(query, history)
    else:
        effective_query = query

    # 3) 混合召回 + 精排
    try:
        recalled = hybrid_retrieve(effective_query, top_k=top_k or settings.RETRIEVER_TOP_K,
                                   tags_filter=tags_filter)
    except Exception as e:  # noqa
        logger.exception(f"混合召回失败: {e}")
        recalled = []
    ranked = rerank(effective_query, recalled, top_k=settings.RERANK_TOP_K)
    context_text, sources = _format_context(ranked)

    yield {"type": "sources", "data": sources}

    # 4) 组装 Prompt + 流式调用 LLM
    llm = get_langchain_chat()
    from langchain_core.messages import SystemMessage, HumanMessage, AIMessage

    msgs = [SystemMessage(content=SYSTEM_PROMPT)]
    if context_text:
        msgs[0] = SystemMessage(content=SYSTEM_PROMPT + "\n\n【知识库参考片段】如下：\n" + context_text)
    else:
        msgs[0] = SystemMessage(content=SYSTEM_PROMPT + "\n\n⚠️  知识库当前没有找到任何参考片段。")
    for r, c in _build_history_window(history, max_turns=6):
        if r == "user":
            msgs.append(HumanMessage(content=c))
        elif r == "assistant":
            msgs.append(AIMessage(content=c))
    msgs.append(HumanMessage(content=query))

    answer_chunks: List[str] = []
    tokens = 0
    try:
        async for tok in llm.astream(msgs):
            txt = tok.content if hasattr(tok, "content") else str(tok)
            if txt is None:
                continue
            answer_chunks.append(str(txt))
            yield {"type": "token", "content": str(txt)}
    except Exception as e:  # noqa
        logger.exception(f"LLM 流式生成失败: {e}")
        msg = f"（大模型调用异常：{e}）"
        answer_chunks.append(msg)
        yield {"type": "token", "content": msg}

    full_answer = "".join(answer_chunks)
    # 5) 写入缓存（三层：L1 Redis + L2 Chroma，有来源时才缓存）
    if sources and full_answer:
        try:
            context_hash = hashlib.sha256(
                ",".join(sorted(str(s.get("doc_id", s.get("doc_name", ""))) for s in sources)).encode()
            ).hexdigest()
            asyncio.create_task(cache.put(
                query=query, model=settings.LLM_MODEL_NAME,
                temperature=settings.LLM_TEMPERATURE,
                system_prompt_hash=RAG_PROMPT_HASH,
                context_hash=context_hash,
                answer=full_answer, sources=sources, intent="product_consult",
                tokens_used=len(full_answer), cache_type="answer",
            ))
        except Exception as e:  # noqa
            logger.warning(f"写缓存失败: {e}")
    yield {"type": "done", "tokens": tokens}
