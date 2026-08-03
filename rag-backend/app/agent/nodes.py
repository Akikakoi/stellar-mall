"""LangGraph 节点逻辑：意图识别、参数检查、工具执行、回答生成等。"""
from __future__ import annotations
import hashlib
import json
from typing import Dict, Any, List

from langchain_core.messages import HumanMessage, SystemMessage, AIMessage, ToolMessage

from app.config import settings
from app.core.logger import logger
from app.rag.llm import get_langchain_chat
from app.rag.llm_cache import get_cache_sync, put_cache_sync, INTENT_PROMPT_HASH, AGENT_PROMPT_HASH
from app.agent.state import AgentState
from app.agent.prompts import INTENT_CLASSIFICATION_PROMPT, AGENT_SYSTEM_PROMPT, PARAM_MISSING_PROMPT
from app.agent.tools import (
    kb_search_tool, kb_spec_compare, query_order_tool, apply_after_sales_tool,
    query_cart_tool, clear_cart_tool, delete_cart_item_tool, update_cart_item_tool,
    cancel_order_tool, confirm_receipt_tool, query_favorite_tool,
    add_to_cart_tool, query_wallet_tool, product_search_tool, query_reviews_tool,
)
from app.agent.tools.resolve_sku_tool import resolve_sku_tool


INTENT_DESC_MAP = {
    "product_consult": "咨询商品信息",
    "order_query": "查询订单",
    "cancel_order": "取消订单",
    "confirm_receipt": "确认收货",
    "cart_query": "查询购物车",
    "cart_add": "加入购物车",
    "cart_clear": "清空购物车",
    "cart_delete": "删除购物车商品",
    "cart_update": "修改购物车商品",
    "after_sales": "申请售后",
    "favorite_query": "查询收藏夹",
    "wallet_query": "查询钱包余额",
    "product_search": "搜索商品",
    "review_query": "查询商品评价",
    "small_talk": "闲聊",
    "other": "其他问题",
}

TOOL_REQUIRED_PARAMS = {
    "kb_search": {
        "required": ["query"],
        "optional": ["top_k", "tags_filter"],
    },
    "query_order": {
        "required": [],  # 有 token 可以查全部，order_id 是可选
        "optional": ["order_id", "status"],
    },
    "apply_after_sales": {
        "required": ["order_id", "after_sales_type", "reason"],
        "optional": ["description", "contact_phone"],
    },
    "query_cart": {
        "required": [],  # 有 token 即可查询购物车
        "optional": [],
    },
    "clear_cart": {
        "required": [],  # 有 token 即可清空购物车
        "optional": [],
    },
    "delete_cart_item": {
        "required": ["cart_item_id"],
        "optional": [],
    },
    "update_cart_item": {
        "required": ["cart_item_id"],
        "optional": ["qty", "checked"],
    },
    "cancel_order": {
        "required": ["order_id"],
        "optional": [],
    },
    "confirm_receipt": {
        "required": ["order_id"],
        "optional": [],
    },
    "query_favorite": {
        "required": [],
        "optional": [],
    },
    "add_to_cart": {
        "required": ["sku_id"],
        "optional": ["qty"],
    },
    "query_wallet": {
        "required": [],
        "optional": [],
    },
    "product_search": {
        "required": ["query"],
        "optional": ["page", "page_size", "category"],
    },
    "query_reviews": {
        "required": ["spu_id"],
        "optional": ["page", "page_size"],
    },
}

INTENT_TO_TOOL = {
    "product_consult": "kb_search",
    "order_query": "query_order",
    "cancel_order": "cancel_order",
    "confirm_receipt": "confirm_receipt",
    "cart_query": "query_cart",
    "cart_add": "add_to_cart",
    "cart_clear": "clear_cart",
    "cart_delete": "delete_cart_item",
    "cart_update": "update_cart_item",
    "after_sales": "apply_after_sales",
    "favorite_query": "query_favorite",
    "wallet_query": "query_wallet",
    "product_search": "product_search",
    "review_query": "query_reviews",
}


def _get_last_user_query(state: AgentState) -> str:
    """从状态中提取最后一条用户消息。"""
    messages = state.get("messages", [])
    for msg in reversed(messages):
        if isinstance(msg, HumanMessage):
            return msg.content if isinstance(msg.content, str) else str(msg.content)
    return ""


def _format_recent_history(messages: list, max_turns: int = 6) -> str:
    """把最近 max_turns 条 Human/AIMessage 拼成对话文本，用于上下文提示。

    返回示例：
        用户: 帮我把星耀 X100 Pro Max 的 16+512G 加入购物车
        客服: 请问对应的 SKU 编号是多少呢？
    """
    if not messages:
        return "（无）"
    lines = []
    for msg in messages[-(max_turns * 2):]:  # 用户+客服算 1 turn
        if isinstance(msg, HumanMessage):
            content = msg.content if isinstance(msg.content, str) else str(msg.content)
            lines.append(f"用户: {content}")
        elif isinstance(msg, AIMessage):
            content = msg.content if isinstance(msg.content, str) else str(msg.content)
            # 截断过长的 AI 回复，避免提示词膨胀
            if len(content) > 300:
                content = content[:300] + "…"
            lines.append(f"客服: {content}")
    return "\n".join(lines) if lines else "（无）"


# 规格属性关键词映射表：用户问题中的属性词 → 知识库查询关键词
_SPEC_ATTR_KEYWORDS = {
    "充电": "充电功率 有线快充 电池容量 快充 电池 充电",
    "充电功率": "充电功率 有线快充 快充 充电",
    "续航": "电池容量 快充速度 电池 续航",
    "电池": "电池容量 有线快充 电池 快充 mAh Wh",
    "屏幕": "屏幕 分辨率 刷新率 显示 英寸",
    "拍照": "摄像头 像素 拍照 摄影 镜头",
    "像素": "摄像头 像素 镜头",
    "处理器": "处理器 芯片 CPU 核心 核",
    "芯片": "处理器 芯片 CPU 核心",
    "内存": "内存 RAM",
    "存储": "存储 ROM",
    "重量": "机身尺寸 重量",
    "尺寸": "机身尺寸 重量",
}


def _extract_spec_attrs(query: str) -> str:
    """从用户问题中提取要比较的规格属性关键词。

    例如："哪一款手机充电功率最高" → "充电功率 有线快充"
        "哪个笔记本屏幕最好" → "屏幕 分辨率 刷新率"
    """
    found = set()
    for kw, attrs in _SPEC_ATTR_KEYWORDS.items():
        if kw in query:
            for a in attrs.split():
                found.add(a)
    return " ".join(sorted(found))


# 用户问题中的品类关键词 → KB 文档 tags 值（用于 ChromaDB tags_filter 过滤）
_QUERY_CATEGORY_TO_TAG = {
    "手机": "智能手机",
    "平板": "平板电脑",
    "平板电脑": "平板电脑",
    "笔记本": "笔记本电脑",
    "笔记本电脑": "笔记本电脑",
    "手表": "智能穿戴",
    "手环": "智能穿戴",
    "耳机": "智能穿戴",
    "眼镜": "智能穿戴",
    "戒指": "智能穿戴",
    "穿戴": "智能穿戴",
    "路由": "其他",
    "充电器": "其他",
    "充电宝": "其他",
}


def _resolve_query_tags(query: str) -> list | None:
    """从用户问题中提取产品品类，映射为 KB 文档的 tags 值列表，用于 tags_filter 过滤。

    例如："哪一款手机充电功率更高" → ["智能手机"]
        "哪个笔记本屏幕最好" → ["笔记本电脑"]
        "平板电脑哪款最便宜" → ["平板电脑"]
    """
    for kw, tag in sorted(_QUERY_CATEGORY_TO_TAG.items(), key=lambda x: len(x[0]), reverse=True):
        if kw in query:
            return [tag]
    return None


def intent_classification_node(state: AgentState) -> Dict[str, Any]:
    """意图识别节点：判断用户问题属于哪个类别。"""
    query = _get_last_user_query(state)
    if not query:
        return {"intent": "other", "intent_confidence": 0.0}

    # 构建对话历史文本片段（最多 6 条最近消息）
    history_text = _format_recent_history(state.get("messages", []), max_turns=6)

    # 短回答（如 "2"、"1"、"是"、"否"）在有历史时不能走缓存，
    # 否则上一次独立问 "2" 的结果（other）会污染本次带上下文的判断
    has_history = bool(history_text and history_text != "（无）")
    is_short_reply = len(query.strip()) <= 6
    use_cache = not (has_history and is_short_reply)

    # === LLM 缓存：意图识别 ===
    cached = None
    if use_cache:
        cached = get_cache_sync(
            query=query,
            model=settings.LLM_MODEL_NAME,
            temperature=settings.LLM_TEMPERATURE,
            system_prompt_hash=INTENT_PROMPT_HASH,
            cache_type="intent",
        )
    if cached:
        logger.info(f"[Intent] cache HIT  query={query[:30]}...  intent={cached['intent']}")
        return {
            "intent": cached.get("intent", "other"),
            "intent_confidence": cached.get("tokens_used", 90) / 100.0,
        }

    llm = get_langchain_chat()
    prompt = INTENT_CLASSIFICATION_PROMPT.format(
        history=history_text,
        query=query,
    )

    try:
        resp = llm.invoke([SystemMessage(content="你是一个严格的JSON输出助手。"),
                           HumanMessage(content=prompt)])
        content = resp.content if hasattr(resp, "content") else str(resp)
        content = content.strip()
        if content.startswith("```json"):
            content = content[7:]
        if content.endswith("```"):
            content = content[:-3]
        content = content.strip()

        result = json.loads(content)
        intent = result.get("intent", "other")
        confidence = float(result.get("confidence", 0.5))

        logger.info(f"[Intent] query={query[:30]}... -> intent={intent} conf={confidence:.2f}")

        # === 异步写入缓存（同样跳过短回答 + 有历史的场景） ===
        if use_cache:
            try:
                put_cache_sync(
                    query=query,
                    model=settings.LLM_MODEL_NAME,
                    temperature=settings.LLM_TEMPERATURE,
                    system_prompt_hash=INTENT_PROMPT_HASH,
                    context_hash=None,
                    answer=json.dumps(result, ensure_ascii=False),
                    sources=[],
                    intent=intent,
                    tokens_used=int(confidence * 100),
                    cache_type="intent",
                )
            except Exception:
                pass

        return {
            "intent": intent,
            "intent_confidence": confidence,
        }
    except Exception as e:
        logger.warning(f"意图识别失败，默认 other: {e}")
        return {"intent": "other", "intent_confidence": 0.0}


def check_params_node(state: AgentState) -> Dict[str, Any]:
    """参数检查节点：检查当前意图对应的工具参数是否齐全。"""
    import re
    intent = state.get("intent", "other")
    query = _get_last_user_query(state)
    tool_name = INTENT_TO_TOOL.get(intent)

    # 用户明确要求使用知识库时，强制走 kb_search，避免被"比较/搜索"意图带偏到商城 API
    kb_override = False
    if "知识库检索" in query or "调用知识库" in query or "查询知识库" in query:
        tool_name = "kb_search"
        kb_override = True
        # 清理查询：去掉"调用知识库检索查询"等指令词，提取纯查询内容
        cleaned = re.sub(
            r'(调用)?(知识库检索|查询知识库)(查询)?[，,，]?\s*',
            '', query
        ).strip()
        if cleaned:
            query = cleaned

    # 规格参数比较/筛选类问题应直接走知识库（product_consult），而不是商城搜索（product_search）
    # 原因：Mall API 的 /user/spu/page?name= 对自然语言整句搜索效果差，
    #       整句"哪一款手机充电功率更高"只会匹配到 1 款手机 + 2 个配件，漏掉另外 5 款手机
    kb_tags_filter = None  # 当覆盖为 kb_search 时，附加的 tags_filter
    should_check_spec = (
        (intent == "product_search" and tool_name == "product_search")
        or intent == "other"
        or intent == "product_consult"  # 单产品查属性也走 spec_compare
    )
    if should_check_spec and not kb_override:
        spec_comparison_patterns = [
            r'(哪[一款个]|哪种|哪一[款种]|哪个).*(充电|功率|瓦|[0-9]+W|电池|续航|屏幕|拍照|��素|内存|存储|处理器|芯片|参数|配置|规格|价格|最便宜|最贵)',
            r'(充电|功率|瓦|电池|续航|屏幕|拍照|像素|内存|存储|处理器|芯片|参数|配置|规格).*(比较|对比|哪[一款个]|哪个|最好|最高|最快|最强|最大|最小|最便宜|最贵)',
            r'.*(哪[一款个]).*(哪[一款个]).*',
            r'(一共|总共|到底)?有几[款个种台].*',
            r'(列出|列举|全部|所有).*[款个商品品类].*',
            r'有多少[款个台种].*',
            r'.*知识库.*有几.*',
            r'有没有.*(手机|平板|笔记本|手表|耳机|充电|路由)',
            r'(支持|兼容|具备|配备).*(快充|W|瓦|瓦特).*',
            # 单产品查属性："XXX 的充电功率是多少" / "XXX 的屏幕参数"
            r'.*的(充电|功率|电池|屏幕|拍照|像素|内存|存储|处理器|芯片|重量|尺寸|价格).*(是多少|多少|怎么样|如何|多大|参数)',
            # 单产品问特性："是否支持反向充电" / "能不能快充" / "支持无线充电吗"
            r'(是否|能不能|可以|能)\s*(支持|用|使用)?.{0,10}(充电|快充|无线|反向|防水|NFC|红外|指纹|蓝牙|WiFi|5G)',
            r'(支持|兼容|具备|配备|内置).{0,10}(充电|快充|无线|反向|防水|NFC|红外|指纹|蓝牙|WiFi|5G)',
        ]
        if any(re.search(p, query) for p in spec_comparison_patterns):
            tool_name = "kb_search"
            intent = "product_consult"
            kb_override = True
            kb_tags_filter = _resolve_query_tags(query)
            logger.info(f"[CheckParams] 规格比较类问题，覆盖为 product_consult -> kb_search, tags_filter={kb_tags_filter}, query={query[:30]!r}")

    if not tool_name or tool_name not in TOOL_REQUIRED_PARAMS:
        return {"missing_params": [], "current_tool": tool_name}

    required_params = TOOL_REQUIRED_PARAMS[tool_name]["required"]
    collected = state.get("required_params", {})

    # 规格比较/枚举覆盖：KB 检索需要更高的 top_k，确保多款产品的 spec 都进结果
    if kb_override and tool_name == "kb_search":
        collected["top_k"] = 20
        if kb_tags_filter:
            collected["tags_filter"] = kb_tags_filter
        collected["original_query"] = query  # 保存原始问句
        # 规格比较类问题的查询改写
        if kb_tags_filter:
            spec_attrs = _extract_spec_attrs(query)
            if spec_attrs:
                tag = kb_tags_filter[0]
                cat_word = ""
                if tag == "智能手机":
                    cat_word = "手机"
                elif tag == "平板电脑":
                    cat_word = "平板电脑"
                elif tag == "笔记本电脑":
                    cat_word = "笔记本"
                rewritten = f"{cat_word} {spec_attrs}" if cat_word else spec_attrs
                collected["query"] = rewritten
                logger.info(f"[CheckParams] 查询改写: {query[:30]!r} -> {rewritten!r}")

    missing = []
    for param in required_params:
        if param not in collected or not collected[param]:
            if param == "query":
                collected["query"] = query
            elif param == "order_id":
                extracted = _extract_order_id(query, state.get("messages", []))
                if extracted:
                    collected["order_id"] = extracted
                else:
                    missing.append(param)
            elif param == "after_sales_type":
                extracted = _extract_after_sales_type(query)
                if extracted:
                    collected["after_sales_type"] = extracted
                else:
                    missing.append(param)
            elif param == "reason":
                reason = _extract_reason(query)
                if reason:
                    collected["reason"] = reason
                else:
                    missing.append(param)
            elif param == "cart_item_id":
                extracted = _extract_cart_item_id(query, state.get("messages", []))
                if extracted:
                    collected["cart_item_id"] = extracted
                else:
                    missing.append(param)
            elif param == "sku_id":
                extracted = _extract_sku_id(query, state.get("messages", []))
                if extracted:
                    collected["sku_id"] = extracted
                elif intent == "cart_add":
                    # 自动解析：从完整历史中提取商品名+规格描述，调用 resolve_sku_tool
                    new_sku = _auto_resolve_sku(state)
                    if new_sku:
                        collected["sku_id"] = new_sku
                    else:
                        # 自动解析失败（规格不存在等），直接生成友好回答，不追问 SKU 编号
                        fail_msg = state.get("resolve_sku_message", "")
                        failed_spec = state.get("resolve_sku_failed_spec", "")
                        if fail_msg:
                            # 已有详细失败信息（来自 resolve_sku_tool），直接返回，
                            # current_tool 设为 None 避免 execute_tool 走不完整的加购流程
                            return {
                                "missing_params": [],
                                "required_params": collected,
                                "current_tool": None,
                                "final_answer": fail_msg,
                            }
                        # 兜底：真的无法解析
                        missing.append(param)
                else:
                    missing.append(param)
            else:
                missing.append(param)

    return {
        "missing_params": missing,
        "required_params": collected,
        "current_tool": tool_name,
        "intent": "product_consult" if kb_override else intent,
        "kb_tags_filter": kb_tags_filter,
    }


def _extract_order_id(query: str, messages: list) -> str | None:
    """从用户消息中提取订单号。"""
    import re
    pattern = r'(?:订单号|单号|order[ _-]?no?|ORDER|NO)[：: ]*([A-Za-z0-9]{6,})'
    m = re.search(pattern, query, re.IGNORECASE)
    if m:
        return m.group(1)
    pattern2 = r'\b([A-Z]{2,}\d{8,}|\d{10,20})\b'
    m2 = re.search(pattern2, query)
    if m2:
        return m2.group(1)
    for msg in reversed(messages[-6:]):
        if isinstance(msg, HumanMessage):
            content = msg.content if isinstance(msg.content, str) else str(msg.content)
            m = re.search(pattern, content, re.IGNORECASE)
            if m:
                return m.group(1)
    return None


def _extract_after_sales_type(query: str) -> str | None:
    """从用户问题中提取售后类型。"""
    q = query.lower()
    if "退款" in q or "退钱" in q or "refund" in q:
        return "refund"
    if "退货" in q or "退回" in q or "return" in q:
        return "return"
    if "换货" in q or "换一个" in q or "exchange" in q:
        return "exchange"
    if "售后" in q or "维修" in q or "质量" in q:
        return "other"
    return None


def _extract_reason(query: str) -> str | None:
    """从用户问题中提取售后原因。"""
    if len(query) > 5 and ("质量" in query or "坏了" in query or "不行" in query
                          or "不合适" in query or "不想要" in query or "色差" in query):
        return query[:100]
    return None


def _extract_cart_item_id(query: str, messages: list) -> str | None:
    """从用户消息中提取购物车商品ID。"""
    import re
    # 匹配 "第N个" 或 "商品ID: N" 或纯数字ID
    patterns = [
        r'第\s*(\d+)\s*个',
        r'(?:购物车)?(?:商品)?ID[：: ]*(\d+)',
        r'编号[：: ]*(\d+)',
    ]
    for pat in patterns:
        m = re.search(pat, query)
        if m:
            return m.group(1)
    return None


def _auto_resolve_sku(state: AgentState) -> str | None:
    """当用户通过自然语言描述商品（如"星耀 X100 Pro Max的16+512版本"）时，
    自动搜索商品、匹配规格，返回 sku_id。

    使用**最新**用户消息（而非最长消息），确保处理当前请求而非历史消息。

    Returns:
        sku_id 字符串 或 None（解析失败）
    失败时，将失败原因写入 state["resolve_sku_message"]，供后续节点使用。
    """
    import re

    messages = state.get("messages", [])
    mall_token = state.get("mall_token")

    # 使用最新一条用户消息（get_last_user_query 的逻辑）
    first_user_query = ""
    for msg in reversed(messages):
        if isinstance(msg, HumanMessage):
            content = msg.content if isinstance(msg.content, str) else str(msg.content)
            first_user_query = content
            break

    if not first_user_query:
        return None

    # 去掉常见的意图前缀，提取核心商品描述
    clean = first_user_query
    for prefix in ["帮我把", "把", "我想把", "请把", "给我把"]:
        if clean.startswith(prefix):
            clean = clean[len(prefix):]
            break

    # 去掉常见的后缀
    for suffix in ["加入购物车", "加入我的购物车", "添加到购物车", "加购物车", "加一下购物车",
                   "添加到我的购物车"]:
        if clean.endswith(suffix):
            clean = clean[:-len(suffix)].rstrip()
            break

    # 提取"的版本"/"的"之后的部分作为规格关键词
    spec_keywords = ""
    for sep in ["的版本", "版本", "的"]:
        idx = clean.rfind(sep)
        if idx > 0:
            spec_keywords = clean[idx + len(sep):].strip()
            clean = clean[:idx].strip()
            # 如果 spec_keywords 为空（如"的版本"后没东西），
            # 继续用剩余的 clean 再找一次"的"提取规格
            if not spec_keywords and sep != "的":
                continue
            break

    if not clean or len(clean) < 2:
        return None

    # 组合规格描述：显式规格 + 原始 query 中提取的模式
    spec_desc = spec_keywords or None
    if not spec_desc:
        from app.agent.tools.resolve_sku_tool import _extract_spec_pattern
        spec_desc = _extract_spec_pattern(first_user_query)

    logger.info(f"[auto_resolve_sku] query='{first_user_query[:50]}...' product='{clean}' spec='{spec_desc}'")

    try:
        result = resolve_sku_tool.invoke({
            "product_name": clean,
            "spec_description": spec_desc,
            "mall_token": mall_token,
        })
        if result.get("success") and result.get("sku_id"):
            sku_id = result.get("sku_id")
            logger.info(f"[auto_resolve_sku] SUCCESS sku_id={sku_id} spu={result.get('spu_name')} reason={result.get('match_reason')}")
            return str(sku_id)
        else:
            failure_msg = result.get("message", "规格匹配失败")
            logger.info(f"[auto_resolve_sku] FAILED: {failure_msg}")
            # 透传失败消息给下游节点
            state["resolve_sku_message"] = failure_msg
            state["resolve_sku_failed_spec"] = spec_desc or "未知规格"
            return None
    except Exception as e:
        err_msg = f"SKU 解析异常: {e}"
        logger.warning(f"[auto_resolve_sku] {err_msg}")
        state["resolve_sku_message"] = err_msg
        return None


def _extract_sku_id(query: str, messages: list) -> str | None:
    """从用户消息中提取 SKU 编号。

    支持以下场景：
    1. 显式表达：'SKU 编号 12' / 'sku_id=12' / '第 12 个' / '编号 12'
    2. 隐式回答：上一条客服消息询问了 SKU 编号，而用户用纯数字回答（"2"/"12"），
       此时数字即视为 SKU 编号
    """
    import re

    # 1) 显式匹配
    explicit_patterns = [
        r'SKU\s*(?:编号|ID|id)[：: =]*\s*(\d+)',
        r'sku[_-]?id[：: =]*\s*(\d+)',
        r'(?:SKU|sku)\s*[：: ]*\s*(\d+)',
        r'第\s*(\d+)\s*个(?:SKU|sku)?',
        r'编号[：: ]*(\d+)',
    ]
    for pat in explicit_patterns:
        m = re.search(pat, query, re.IGNORECASE)
        if m:
            return m.group(1)

    # 2) 隐式回答：纯数字 + 上一轮客服在追问 SKU
    if re.fullmatch(r'\s*\d{1,12}\s*', query):
        # 查找最近一条客服消息
        for msg in reversed(messages[-4:]):
            if isinstance(msg, AIMessage):
                content = msg.content if isinstance(msg.content, str) else str(msg.content)
                if "SKU" in content or "sku" in content or "规格编号" in content:
                    return query.strip()
                # 客服消息中没有 SKU 相关字样，停止查找
                return None
    return None


def ask_params_node(state: AgentState) -> Dict[str, Any]:
    """缺参追问节点：生成追问话术。"""
    intent = state.get("intent", "other")
    missing = state.get("missing_params", [])
    if not missing:
        return {"final_answer": ""}

    intent_desc = INTENT_DESC_MAP.get(intent, "办理业务")
    missing_desc = []
    for p in missing:
        if p == "order_id":
            missing_desc.append("订单号")
        elif p == "after_sales_type":
            missing_desc.append("售后类型（退款/退货/换货）")
        elif p == "reason":
            missing_desc.append("售后原因")
        elif p == "query":
            missing_desc.append("查询内容")
        elif p == "cart_item_id":
            missing_desc.append("购物车商品编号（先查看购物车获取编号）")
        else:
            missing_desc.append(p)

    history_lines = []
    for msg in state.get("messages", [])[-6:]:
        if isinstance(msg, HumanMessage):
            history_lines.append(f"用户: {msg.content}")
        elif isinstance(msg, AIMessage):
            history_lines.append(f"客服: {msg.content}")
    history_text = "\n".join(history_lines) if history_lines else "（无历史对话）"

    llm = get_langchain_chat()
    prompt = PARAM_MISSING_PROMPT.format(
        intent_description=intent_desc,
        missing_params="、".join(missing_desc),
        history=history_text,
    )

    try:
        resp = llm.invoke([HumanMessage(content=prompt)])
        question = resp.content if hasattr(resp, "content") else str(resp)
        return {"final_answer": str(question).strip()}
    except Exception as e:
        logger.warning(f"生成追问失败: {e}")
        first_param = missing_desc[0] if missing_desc else "信息"
        return {"final_answer": f"请问您的{first_param}是什么？"}


def tool_execution_node(state: AgentState) -> Dict[str, Any]:
    """工具执行节点：调用对应的工具。"""
    tool_name = state.get("current_tool")
    params = state.get("required_params", {})
    user_id = state.get("user_id")
    mall_token = state.get("mall_token")

    if not tool_name:
        return {"tool_results": {"success": False, "message": "未指定工具"}}

    try:
        if tool_name == "kb_search":
            query = params.get("query", "")
            top_k = params.get("top_k")
            tags_filter = params.get("tags_filter") or state.get("kb_tags_filter")

            # 规格比较覆盖：走关键词匹配而非向量搜索，确保每款产品都有 spec
            is_spec_override = top_k and top_k >= 20  # check_params 覆盖时设置 top_k=20
            if is_spec_override:
                original_query = params.get("original_query", query)
                spec_attrs = _extract_spec_attrs(original_query)
                if spec_attrs and tags_filter:
                    spec_kw = spec_attrs.split()
                    logger.info(f"[ExecuteTool] 规格比较: tags={tags_filter}, keywords={spec_kw}")
                    result = kb_spec_compare(
                        query=query, tags_filter=tags_filter,
                        spec_keywords=spec_kw, per_product=3,
                    )
                elif tags_filter:
                    # 枚举/全量统计模式：有品类标签但无 spec 关键词，取全部品类文档
                    broad_kw = ["##", "|", "# ", "核心参数", "电池", "快充", "W", "处理器", "屏幕", "产品参数", "规格"]
                    logger.info(f"[ExecuteTool] 枚举/全量统计: tags={tags_filter}")
                    result = kb_spec_compare(
                        query=query, tags_filter=tags_filter,
                        spec_keywords=broad_kw, per_product=1,
                    )
                else:
                    # 单产品查属性：无品类标签，但 query 含产品名，用向量搜索 + 大 top_k
                    logger.info(f"[ExecuteTool] 单产品属性查询（向量搜索，top_k=20）: query={query[:40]!r}")
                    result = kb_search_tool.invoke({"query": query, "top_k": 20})
            else:
                invoke_args = {"query": query}
                if top_k:
                    invoke_args["top_k"] = top_k
                if tags_filter:
                    invoke_args["tags_filter"] = tags_filter
                logger.info(f"[ExecuteTool] kb_search: query={query[:40]!r} top_k={top_k} tags_filter={tags_filter}")
                result = kb_search_tool.invoke(invoke_args)
            sources = result.get("sources", []) if isinstance(result, dict) else []
            logger.info(f"[ExecuteTool] kb_search 返回 {len(sources)} 条结果")
            return {
                "tool_results": result,
                "sources": sources,
            }

        elif tool_name == "query_order":
            result = query_order_tool.invoke({
                "order_id": params.get("order_id"),
                "status": params.get("status"),
                "mall_token": mall_token,
                "user_id": user_id,
            })
            return {"tool_results": result}

        elif tool_name == "apply_after_sales":
            result = apply_after_sales_tool.invoke({
                "order_id": params.get("order_id", ""),
                "after_sales_type": params.get("after_sales_type", "other"),
                "reason": params.get("reason", ""),
                "description": params.get("description", ""),
                "contact_phone": params.get("contact_phone", ""),
                "user_id": user_id,
            })
            return {"tool_results": result}

        elif tool_name == "query_cart":
            result = query_cart_tool.invoke({
                "mall_token": mall_token,
                "user_id": user_id,
            })
            return {"tool_results": result}

        elif tool_name == "clear_cart":
            result = clear_cart_tool.invoke({
                "mall_token": mall_token,
                "user_id": user_id,
            })
            return {"tool_results": result}

        elif tool_name == "delete_cart_item":
            result = delete_cart_item_tool.invoke({
                "cart_item_id": params.get("cart_item_id", ""),
                "mall_token": mall_token,
                "user_id": user_id,
            })
            return {"tool_results": result}

        elif tool_name == "update_cart_item":
            result = update_cart_item_tool.invoke({
                "cart_item_id": params.get("cart_item_id", ""),
                "qty": params.get("qty"),
                "checked": params.get("checked"),
                "mall_token": mall_token,
                "user_id": user_id,
            })
            return {"tool_results": result}

        elif tool_name == "cancel_order":
            result = cancel_order_tool.invoke({
                "order_id": params.get("order_id", ""),
                "mall_token": mall_token,
                "user_id": user_id,
            })
            return {"tool_results": result}

        elif tool_name == "confirm_receipt":
            result = confirm_receipt_tool.invoke({
                "order_id": params.get("order_id", ""),
                "mall_token": mall_token,
                "user_id": user_id,
            })
            return {"tool_results": result}

        elif tool_name == "query_favorite":
            result = query_favorite_tool.invoke({
                "mall_token": mall_token,
                "user_id": user_id,
            })
            return {"tool_results": result}

        elif tool_name == "add_to_cart":
            result = add_to_cart_tool.invoke({
                "sku_id": params.get("sku_id", ""),
                "qty": params.get("qty", 1),
                "mall_token": mall_token,
                "user_id": user_id,
            })
            return {"tool_results": result}

        elif tool_name == "query_wallet":
            result = query_wallet_tool.invoke({
                "mall_token": mall_token,
                "user_id": user_id,
            })
            return {"tool_results": result}

        elif tool_name == "product_search":
            result = product_search_tool.invoke({
                "query": params.get("query", ""),
                "page": params.get("page", 1),
                "page_size": params.get("page_size", 10),
                "category": params.get("category"),
                "mall_token": mall_token,
                "user_id": user_id,
            })
            sources = result.get("data", []) if isinstance(result, dict) else []

            # 智能补充：商品搜索/比较时，从知识库补充规格参数（充电功率、电池等）
            kb_specs = None
            query_text = params.get("query", "")
            # 判断是否涉及规格比较的关键词
            spec_keywords = [
                "充电", "电池", "续航", "功率", "瓦", "W",
                "屏幕", "拍照", "像素", "内存", "存储", "处理器", "芯片",
                "最快", "最高", "最大", "最好", "最强", "哪款", "比较", "对比",
            ]
            if result.get("success") and result.get("data") and any(kw in query_text for kw in spec_keywords):
                try:
                    kb_specs = []
                    seen_keys = set()  # (doc_name, chunk_index) 去重
                    # 同时记录每个 product 至少命中了几个 doc，避免重复
                    seen_product_docs = {}  # pname -> set(doc_name)
                    product_names = [item.get("name", "") for item in result.get("data", []) if item.get("name")][:6]
                    spec_attrs = _extract_spec_attrs(query_text)

                    def _add_sources(kb_res, pname=None):
                        if not kb_res.get("success") or not kb_res.get("sources"):
                            return 0
                        added = 0
                        for src in kb_res["sources"]:
                            key = (src.get("doc_name", ""), src.get("chunk_index", 0))
                            if key not in seen_keys:
                                seen_keys.add(key)
                                if pname and not src.get("matched_product"):
                                    src["matched_product"] = pname
                                # 记录 product 命中过哪些 doc
                                if pname:
                                    seen_product_docs.setdefault(pname, set()).add(src.get("doc_name", ""))
                                kb_specs.append(src)
                                added += 1
                        return added

                    # 方案 1：逐个商品单独检索（精准但召回可能不足）
                    # 重要修复：tags_filter 用准确的 "智能手机"（KB 文档的真实 tag）
                    #            之前用 "手机" 虽能 $contains 命中，但语义不对且容易被改库影响
                    for pname in product_names:
                        kb_q = f"{pname} {spec_attrs}" if spec_attrs else f"{pname} 充电功率 有线快充 电池容量"
                        try:
                            kb_res = kb_search_tool.invoke({
                                "query": kb_q,
                                "top_k": 6,
                                "tags_filter": ["智能手机"],
                            })
                            _add_sources(kb_res, pname)
                        except Exception:
                            pass
                        if len(kb_specs) >= 18:  # 6 款商品 * 3 条
                            break

                    # 方案 2（关键修复）：始终跑一次全量兜底检索，覆盖未被方案 1 命中的商品
                    # 之前错误地写成 `if not kb_specs`：只要方案 1 找到任意一条，方案 2 就不跑
                    # 导致其他商品的 spec（比如玉米手机 10 Pro 的 120W）永远拿不到
                    # 现在：始终跑一次，不带 tags_filter，覆盖更广
                    try:
                        fallback_res = kb_search_tool.invoke({
                            "query": query_text,
                            "top_k": 12,
                        })
                        _add_sources(fallback_res)
                    except Exception:
                        pass

                    # 方案 3（关键修复）：对每个还没命中 spec 的商品，再单独查一次（不带 tags_filter）
                    # 确保每款返回的商品都有 spec 片段进入上下文
                    for pname in product_names:
                        if pname in seen_product_docs and seen_product_docs[pname]:
                            continue  # 已经被方案 1/2 命中，跳过
                        try:
                            kb_res = kb_search_tool.invoke({
                                "query": f"{pname} 充电功率 有线快充 电池容量",
                                "top_k": 5,
                            })
                            _add_sources(kb_res, pname)
                        except Exception:
                            pass

                    if kb_specs:
                        logger.info(
                            f"[product_search+KB] 为 {len(product_names)} 款商品补充 {len(kb_specs)} 条规格参数片段, 命中商品={list(seen_product_docs.keys())}"
                        )
                except Exception as e:
                    logger.warning(f"[product_search+KB] 知识库补充失败: {e}")

            result["kb_specs"] = kb_specs
            return {
                "tool_results": result,
                "sources": sources,
            }

        elif tool_name == "query_reviews":
            result = query_reviews_tool.invoke({
                "spu_id": params.get("spu_id", ""),
                "page": params.get("page", 1),
                "page_size": params.get("page_size", 10),
                "mall_token": mall_token,
                "user_id": user_id,
            })
            return {"tool_results": result}

        else:
            return {"tool_results": {"success": False, "message": f"未知工具: {tool_name}"}}

    except Exception as e:
        logger.error(f"工具执行异常 {tool_name}: {e}")
        return {"tool_results": {"success": False, "message": f"工具执行失败: {str(e)}"}}


def _extract_product_ids(tool_result: dict) -> List[int]:
    """从工具结果中提取商品 ID 列表（用于缓存精准失效）。"""
    ids = []
    try:
        data = tool_result.get("data", {})
        if isinstance(data, dict):
            items = data.get("records", data.get("list", []))
            if isinstance(items, list):
                for item in items:
                    if isinstance(item, dict):
                        for key in ("spu_id", "id", "sku_id"):
                            if key in item:
                                ids.append(int(item[key]))
                                break
        # 知识库搜索结果
        sources = tool_result.get("sources", [])
        for s in sources:
            if isinstance(s, dict) and "spu_id" in s:
                ids.append(int(s["spu_id"]))
    except Exception:
        pass
    return list(set(ids)) if ids else None


def generate_answer_node(state: AgentState) -> Dict[str, Any]:
    """回答生成节点：根据工具结果生成最终回答。"""
    intent = state.get("intent", "other")
    tool_result = state.get("tool_results", {})
    query = _get_last_user_query(state)

    # 如果已有预生成的 final_answer（如 check_params 因 SKU 解析失败直接返回的），直接使用
    preset_answer = state.get("final_answer", "")
    if preset_answer and not state.get("current_tool"):
        return {"final_answer": preset_answer}

    if intent == "small_talk":
        return _handle_small_talk(state)

    if intent == "other":
        return _handle_other(state)

    # === LLM 缓存：答案生成 ===
    context_hash = hashlib.sha256(
        json.dumps(tool_result, sort_keys=True, ensure_ascii=False, default=str).encode()
    ).hexdigest()
    cached = get_cache_sync(
        query=query,
        model=settings.LLM_MODEL_NAME,
        temperature=settings.LLM_TEMPERATURE,
        system_prompt_hash=AGENT_PROMPT_HASH,
        context_hash=context_hash,
        cache_type="answer",
    )
    if cached:
        logger.info(f"[Agent] cache HIT  query={query[:30]}...  intent={intent}")
        return {
            "final_answer": cached["answer"],
            "stream_chunks": list(cached["answer"]),
            "from_cache": True,
            "sources": cached.get("sources", []),
        }

    llm = get_langchain_chat()

    context = _build_tool_context(tool_result, intent)

    sys_msg = SystemMessage(content=AGENT_SYSTEM_PROMPT)
    # 根据不同意图定制回答提示
    extra_hints = ""
    if intent == "product_search":
        extra_hints = (
            "- 如果工具结果中包含「知识库中相关商品的规格参数」，务必结合这些数据回答比较/筛选/推荐类问题\n"
            "- 比较时用表格或列表展示各款商品的关键差异，重点突出用户关心的维度\n"
        )

    # 防幻觉：商品名称必须严格使用工具返回的真实名称
    anti_hallucination = (
        "【防幻觉强约束 — 违反任何一条都是严重错误】\n"
        "- 回答中所有数字（功率、容量、价格、尺寸等）必须**严格等于**工具返回的数值，不得修改哪怕 1 个数字\n"
        "- 如果用户问「有没有 200W 快充」但工具返回的数据里没有任何产品标注 200W，只能回答「没有，最高 XXW」\n"
        "- 商品名称必须与工具返回 100% 一致，禁止近音字替换\n"
        "- 不存在的数据写「暂无」，禁止编造\n"
    )

    # 硬数据校验：如果用户问的规格数值在上下文中不存在，直接注入警告
    import re as _re
    user_nums = set()
    for m in _re.finditer(r'(\d+)\s*W', query):
        user_nums.add(int(m.group(1)))
    if user_nums:
        # 搜索整个上下文（不是只搜 sources 的 content，也搜 product_count/product_names 等元信息）
        context_str = context + " " + str(tool_result.get("product_names", []))
        context_nums = set(int(n) for n in _re.findall(r'(\d+)\s*W', context_str))
        missing_nums = user_nums - context_nums
        if missing_nums:
            context = (
                f"⚠️ 注意：以下数据中**没有任何产品标注 {', '.join(f'{n}W' for n in sorted(missing_nums))} 快充**。"
                f"如果你在数据中确实没有找到该规格，请如实告知用户，不要编造。\n\n{context}"
            )

    user_msg = HumanMessage(content=f"""用户问题：{query}

工具返回结果：
{context}

请根据工具返回的结果，用友好、专业的语气回答用户的问题。
- 回答要简洁明了，重点突出
- 适当使用 Markdown 格式（列表、加粗、表格）
- 有数据的地方用数据说话
- **绝不要**在回复中暴露任何技术错误信息、英文异常、Python traceback 或"工具调用失败"等字样
- 如果操作失败，只告诉用户"暂时无法完成，请确认商品名称和型号是否正确"，**不要输出任何技术术语**
- 涉及订单号等信息，提醒用户注意保管
{extra_hints}
{anti_hallucination}""")

    try:
        answer_chunks = []
        for chunk in llm.stream([sys_msg, user_msg]):
            txt = chunk.content if hasattr(chunk, "content") else str(chunk)
            if txt:
                answer_chunks.append(str(txt))

        full_answer = "".join(answer_chunks)

        # === 异步写入缓存 ===
        try:
            # 提取商品 ID
            product_ids = _extract_product_ids(tool_result)
            put_cache_sync(
                query=query,
                model=settings.LLM_MODEL_NAME,
                temperature=settings.LLM_TEMPERATURE,
                system_prompt_hash=AGENT_PROMPT_HASH,
                context_hash=context_hash,
                answer=full_answer,
                sources=state.get("sources", []),
                intent=intent,
                tokens_used=len(full_answer),
                product_ids=product_ids,
                cache_type="answer",
            )
        except Exception:
            pass

        return {
            "final_answer": full_answer,
            "stream_chunks": answer_chunks,
        }
    except Exception as e:
        logger.error(f"生成回答失败: {e}")
        return {"final_answer": "抱歉，系统暂时无法处理您的请求，请稍后重试。"}


def _handle_small_talk(state: AgentState) -> Dict[str, Any]:
    """处理闲聊。"""
    query = _get_last_user_query(state)
    llm = get_langchain_chat()
    try:
        resp = llm.invoke([
            SystemMessage(content="你是一个友好的电商客服。用户和你闲聊时，请用亲切、简洁的语气回应，然后引导用户说出具体的需求，比如'请问有什么可以帮您的吗？'"),
            HumanMessage(content=query),
        ])
        answer = resp.content if hasattr(resp, "content") else str(resp)
        return {"final_answer": str(answer)}
    except Exception as e:
        return {"final_answer": "您好！请问有什么可以帮您的吗？"}


def _handle_other(state: AgentState) -> Dict[str, Any]:
    """处理其他问题。"""
    query = _get_last_user_query(state)
    return {
        "final_answer": f"抱歉，我暂时无法理解您的问题：「{query}」。\n\n我可以帮您：\n- 🔍 查询商品信息\n- 📦 查询订单 / 取消订单 / 确认收货\n- 🛒 查看/管理购物车\n- ❤️ 查看收藏夹\n- 🔧 申请售后服务\n\n请告诉我您需要什么帮助？"
    }


def _sanitize_error_message(raw_msg: str) -> str:
    """清洗技术错误信息，去掉 Python 异常和英文提示，转为用户友好的中文描述。"""
    import re

    msg = raw_msg or ""

    # 1. 去掉常见 Python 异常堆栈
    msg = re.sub(r'invalid literal for int\(\) with base 10[^:]*:?\s*[\'"]?[^\'"]*[\'"]?',
                 "数据类型错误", msg)
    msg = re.sub(r"unsupported operand type\(s\)[^:]*:?\s*'[^']*'\s*(and|&)\s*'[^']*'",
                 "参数类型不匹配", msg)
    msg = re.sub(r"KeyError:\s*'[^']*'", "数据字段缺失", msg)
    msg = re.sub(r"ValueError[:\s]*[^,\.]*", "数据格式错误", msg)
    msg = re.sub(r"TypeError[:\s]*[^,\.]*", "参数类型不匹配", msg)
    msg = re.sub(r"AttributeError[:\s]*[^,\.]*", "数据字段缺失", msg)
    msg = re.sub(r"ConnectionError[^,\.]*", "网络连接失败", msg)
    msg = re.sub(r"TimeoutError[^,\.]*", "请求超时", msg)
    msg = re.sub(r"HTTP\s*\d{3}[:\s]*[^,\.]*", "商城服务异常", msg)
    msg = re.sub(r"工具执行失败[:\s]*", "", msg)

    # 2. 去掉残存的英文和路径信息
    msg = re.sub(r"'[a-z_]+\.[a-z_]+'", "", msg)
    msg = re.sub(r"in\s+\w+\.py", "", msg).strip()

    # 3. 兜底：如果清洗后仍然很长或全是英文，给一个通用提示
    if not msg or len(msg) > 200:
        msg = "系统暂时无法处理您的请求"

    # 4. 美化
    msg = msg.strip().rstrip(".。")
    return msg if msg else "系统暂时无法处理您的请求"


def _build_tool_context(tool_result: Any, intent: str) -> str:
    """把工具结果格式化为上下文文本。"""
    if not tool_result:
        return "（无结果）"

    if isinstance(tool_result, dict):
        if not tool_result.get("success", True):
            # 清洗技术错误信息，转为用户友好提示
            raw_msg = tool_result.get("message", "未知错误")
            clean_msg = _sanitize_error_message(raw_msg)
            return f"⚠️ 操作未成功：{clean_msg}\n（请用友好的语气告知用户，不要暴露任何技术细节或英文异常信息）"

        if intent == "product_consult":
            sources = tool_result.get("sources", [])
            if not sources:
                return "（知识库中没有找到相关内容）"
            lines = []
            # 枚举模式：附加产品总数信息，帮助 LLM 准确回答"一共有几款"
            pc = tool_result.get("product_count")
            if pc:
                pn = tool_result.get("product_names", [])
                lines.append(f"**知识库中该品类共有 {pc} 款产品：** {', '.join(pn[:20])}")
                lines.append("")
            for src in sources:
                lines.append(f"--- 来源: {src.get('doc_name', '未知')}  相关度: {src.get('score', 0):.3f} ---")
                lines.append(src.get("content", ""))
            return "\n\n".join(lines)

        elif intent == "order_query":
            data = tool_result.get("data")
            if tool_result.get("type") == "list":
                orders = data or []
                if not orders:
                    return "（没有找到订单）"
                lines = [f"共找到 {len(orders)} 个订单：\n"]
                for i, o in enumerate(orders, 1):
                    lines.append(f"**订单{i}：** {o.get('order_no', '')}")
                    lines.append(f"- 状态：{o.get('status_text', '未知')}")
                    lines.append(f"- 金额：¥{o.get('pay_amount', '0')}")
                    lines.append(f"- 下单时间：{o.get('create_time', '')}")
                    items = o.get("items", [])
                    if items:
                        item_names = [it.get("name", "") for it in items]
                        lines.append(f"- 商品：{'、'.join(item_names)}")
                    lines.append("")
                return "\n".join(lines)
            else:
                if not data:
                    return "（没有找到该订单）"
                o = data
                lines = [
                    f"**订单号：** {o.get('order_no', '')}",
                    f"**状态：** {o.get('status_text', '未知')}",
                    f"**总金额：** ¥{o.get('total_amount', '0')}",
                    f"**实付：** ¥{o.get('pay_amount', '0')}",
                    f"**收货地址：** {o.get('address', '')}",
                    f"**下单时间：** {o.get('create_time', '')}",
                    "",
                    "**订单明细：**",
                ]
                for item in o.get("items", []):
                    lines.append(f"- {item.get('name', '')} ({item.get('sku_spec', '')}) × {item.get('quantity', 0)}  ¥{item.get('price', 0)}")
                return "\n".join(lines)

        elif intent == "after_sales":
            if tool_result.get("success"):
                return f"""售后申请提交成功！
- 工单号：{tool_result.get('ticket_no', '')}
- 关联订单：{tool_result.get('order_id', '')}
- 售后类型：{tool_result.get('type_text', '')}
- 原因：{tool_result.get('reason', '')}
- 当前状态：{tool_result.get('status_text', '')}
- 预计处理时间：1-3个工作日"""
            else:
                return f"售后申请失败：{tool_result.get('message', '未知原因')}"

        elif intent == "cart_query":
            data = tool_result.get("data")
            if not data:
                return "（购物车为空）"
            lines = [
                f"购物车共 {tool_result.get('count', 0)} 件商品，",
                f"合计：¥{tool_result.get('total_price', '0')}",
                "",
            ]
            for i, item in enumerate(data, 1):
                checked_mark = "√" if item.get("checked") == 1 else "○"
                lines.append(f"{checked_mark} {i}. {item.get('spu_name', '')} ({item.get('sku_specs', '')})")
                lines.append(f"   × {item.get('qty', 0)}  ¥{item.get('sku_price', '0')}  小计：¥{item.get('subtotal', '0')}")
                lines.append("")
            return "\n".join(lines)

        elif intent == "cart_add":
            return tool_result.get("message", "")

        elif intent == "wallet_query":
            if not tool_result.get("success"):
                return f"钱包查询失败：{tool_result.get('message', '未知错误')}"
            lines = [
                "**钱包账户信息：**",
                f"- 当前余额：¥{tool_result.get('balance', '0')}",
                f"- 冻结金额：¥{tool_result.get('frozen', '0')}",
                f"- 累计充值：¥{tool_result.get('total_recharge', '0')}",
                f"- 累计消费：¥{tool_result.get('total_consume', '0')}",
            ]
            return "\n".join(lines)

        elif intent == "product_search":
            data = tool_result.get("data")
            kb_specs = tool_result.get("kb_specs")
            if not data:
                return f"（未找到与「{tool_result.get('query', '')}」相关的商品）"
            lines = [tool_result.get("message", ""), ""]
            for i, item in enumerate(data, 1):
                lines.append(f"{i}. **{item.get('name', '')}**")
                lines.append(f"   SPU编号：{item.get('id', '')}  |  价格区间：¥{item.get('min_price', '0')} ~ ¥{item.get('max_price', '0')}")
                lines.append(f"   销量：{item.get('sale_count', 0)}  |  库存：{item.get('total_stock', 0)}")
                lines.append("")

            # 补充知识库中的规格参数（充电功率、电池等），帮助回答比较/筛选问题
            if kb_specs:
                lines.append("---")
                lines.append("**知识库中相关商品的规格参数（用于回答比较类问题）：**")
                lines.append("")
                for src in kb_specs:
                    doc_name = src.get("doc_name", "未知文档")
                    matched = src.get("matched_product")
                    prefix = f"📄 来源：{doc_name}"
                    if matched:
                        prefix += f"  |  对应商品：{matched}"
                    lines.append(prefix)
                    lines.append(src.get("content", ""))
                    lines.append("")

            return "\n".join(lines)

        elif intent == "review_query":
            data = tool_result.get("data")
            if not data:
                return f"（暂无评价）"
            lines = [tool_result.get("message", ""), ""]
            for i, r in enumerate(data, 1):
                stars = "★" * int(r.get("rating", 0)) + "☆" * (5 - int(r.get("rating", 0)))
                lines.append(f"{i}. {r.get('nickname', '匿名用户')}  {stars}")
                lines.append(f"   {r.get('content', '')}")
                lines.append(f"   {r.get('create_time', '')}")
                if r.get("reply"):
                    lines.append(f"   ＞ 商家回复：{r.get('reply', '')}")
                lines.append("")
            return "\n".join(lines)

        elif intent in ("cart_clear", "cart_delete", "cart_update"):
            return tool_result.get("message", "")

        elif intent == "cancel_order":
            return tool_result.get("message", "")

        elif intent == "confirm_receipt":
            return tool_result.get("message", "")

        elif intent == "favorite_query":
            data = tool_result.get("data")
            if not data:
                return "（收藏夹为空）"
            lines = [
                f"收藏夹共 {tool_result.get('count', 0)} 件商品：",
                "",
            ]
            for i, item in enumerate(data, 1):
                lines.append(f"{i}. **{item.get('spu_name', '')}**")
                lines.append(f"   SPU编号：{item.get('spu_id', '')}  |  最低价：¥{item.get('min_price', '0')}")
                lines.append(f"   收藏时间：{item.get('create_time', '')}")
                lines.append("")
            return "\n".join(lines)

    return str(tool_result)


def route_after_intent(state: AgentState) -> str:
    """意图识别后的路由。"""
    import re
    intent = state.get("intent", "other")
    if intent in ("product_consult", "order_query", "cancel_order", "confirm_receipt",
                    "cart_query", "cart_add", "cart_clear", "cart_delete", "cart_update", "after_sales",
                    "favorite_query", "wallet_query", "product_search", "review_query"):
        return "check_params"
    elif intent == "small_talk":
        return "generate_answer"
    elif intent == "other":
        # 被意图识别误判为 other 的查询，如果看起来像商品/品类问题，也送 check_params
        messages = state.get("messages", [])
        query = ""
        if messages:
            last = messages[-1]
            if hasattr(last, "content"):
                query = last.content if isinstance(last.content, str) else str(last.content)
        rescue_patterns = [
            r'(一共|总共|到底)?有几[款个种台]',
            r'有多少[款个台种]',
            r'(列出|列举|全部|所有).*[款个商品品类]',
            r'知识库.*有几',
            r'(哪[一款个]).*(充电|功率|电池|屏幕|配置|参数|价格)',
            r'(比较|对比|推荐).*(手机|平板|笔记本|手表|耳机)',
            r'(手机|平板|笔记本|手表|耳机|充电).*(哪|推荐|比较|多少)',
        ]
        if any(re.search(p, query) for p in rescue_patterns):
            logger.info(f"[RouteAfterIntent] other→check_params rescue, query={query[:40]!r}")
            return "check_params"
        return "generate_answer"
    else:
        return "generate_answer"


def route_after_params(state: AgentState) -> str:
    """参数检查后的路由。"""
    missing = state.get("missing_params", [])
    if missing:
        return "ask_params"
    else:
        return "execute_tool"
