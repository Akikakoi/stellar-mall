"""LangChain RAG 主链路：查询改写 + Prompt + 流式生成 + 引用来源。"""
from __future__ import annotations
from typing import AsyncGenerator, List, Optional, Tuple

from app.config import settings
from app.core.logger import logger
from app.rag.llm import get_langchain_chat
from app.rag.retriever import hybrid_retrieve, rerank, get_query_cache


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


async def _rewrite_query_if_needed(query: str, history: List[Tuple[str, str]]) -> str:
    """若开启改写且存在多轮上下文，则改写；否则返回原 query。"""
    if not settings.QUERY_REWRITE_ENABLED:
        return query
    if not history or len(history) < 1:
        return query
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
        return rewritten or query
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
    # 1) 先查缓存
    cache = get_query_cache()
    hit = cache.get(query)
    if hit:
        ans, srcs = hit
        for ch in ans:
            yield {"type": "token", "content": ch}
        yield {"type": "sources", "data": srcs}
        yield {"type": "done", "tokens": 0}
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
    # 5) 写入缓存（FAQ 模式，只有有来源时才缓存）
    if sources and full_answer:
        try:
            cache.put(query, full_answer, sources)
        except Exception as e:  # noqa
            logger.warning(f"写缓存失败: {e}")
    yield {"type": "done", "tokens": tokens}
