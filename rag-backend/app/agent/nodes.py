"""LangGraph 节点逻辑：意图识别、参数检查、工具执行、回答生成等。"""
from __future__ import annotations
import json
from typing import Dict, Any, List

from langchain_core.messages import HumanMessage, SystemMessage, AIMessage, ToolMessage

from app.config import settings
from app.core.logger import logger
from app.rag.llm import get_langchain_chat
from app.agent.state import AgentState
from app.agent.prompts import INTENT_CLASSIFICATION_PROMPT, AGENT_SYSTEM_PROMPT, PARAM_MISSING_PROMPT
from app.agent.tools import (
    kb_search_tool, query_order_tool, apply_after_sales_tool,
    query_cart_tool, clear_cart_tool, delete_cart_item_tool, update_cart_item_tool,
    cancel_order_tool, confirm_receipt_tool, query_favorite_tool,
    add_to_cart_tool, query_wallet_tool, product_search_tool, query_reviews_tool,
)


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


def intent_classification_node(state: AgentState) -> Dict[str, Any]:
    """意图识别节点：判断用户问题属于哪个类别。"""
    query = _get_last_user_query(state)
    if not query:
        return {"intent": "other", "intent_confidence": 0.0}

    llm = get_langchain_chat()
    prompt = INTENT_CLASSIFICATION_PROMPT.format(query=query)

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

        return {
            "intent": intent,
            "intent_confidence": confidence,
        }
    except Exception as e:
        logger.warning(f"意图识别失败，默认 other: {e}")
        return {"intent": "other", "intent_confidence": 0.0}


def check_params_node(state: AgentState) -> Dict[str, Any]:
    """参数检查节点：检查当前意图对应的工具参数是否齐全。"""
    intent = state.get("intent", "other")
    tool_name = INTENT_TO_TOOL.get(intent)

    if not tool_name or tool_name not in TOOL_REQUIRED_PARAMS:
        return {"missing_params": [], "current_tool": tool_name}

    required_params = TOOL_REQUIRED_PARAMS[tool_name]["required"]
    query = _get_last_user_query(state)
    collected = state.get("required_params", {})

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
            else:
                missing.append(param)

    return {
        "missing_params": missing,
        "required_params": collected,
        "current_tool": tool_name,
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
            result = kb_search_tool.invoke({"query": query})
            sources = result.get("sources", []) if isinstance(result, dict) else []
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


def generate_answer_node(state: AgentState) -> Dict[str, Any]:
    """回答生成节点：根据工具结果生成最终回答。"""
    intent = state.get("intent", "other")
    tool_result = state.get("tool_results", {})
    query = _get_last_user_query(state)

    if intent == "small_talk":
        return _handle_small_talk(state)

    if intent == "other":
        return _handle_other(state)

    llm = get_langchain_chat()

    context = _build_tool_context(tool_result, intent)

    sys_msg = SystemMessage(content=AGENT_SYSTEM_PROMPT)
    user_msg = HumanMessage(content=f"""用户问题：{query}

工具返回结果：
{context}

请根据工具返回的结果，用友好、专业的语气回答用户的问题。
- 回答要简洁明了，重点突出
- 适当使用 Markdown 格式（列表、加粗）
- 有数据的地方用数据说话
- 如果工具返回失败或没有结果，如实告知用户
- 涉及订单号等信息，提醒用户注意保管
""")

    try:
        answer_chunks = []
        for chunk in llm.stream([sys_msg, user_msg]):
            txt = chunk.content if hasattr(chunk, "content") else str(chunk)
            if txt:
                answer_chunks.append(str(txt))

        full_answer = "".join(answer_chunks)
        return {
            "final_answer": full_answer,
            "stream_chunks": answer_chunks,
        }
    except Exception as e:
        logger.error(f"生成回答失败: {e}")
        return {"final_answer": f"抱歉，生成回答时出现异常：{str(e)}"}


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


def _build_tool_context(tool_result: Any, intent: str) -> str:
    """把工具结果格式化为上下文文本。"""
    if not tool_result:
        return "（无结果）"

    if isinstance(tool_result, dict):
        if not tool_result.get("success", True):
            return f"工具调用失败：{tool_result.get('message', '未知错误')}"

        if intent == "product_consult":
            sources = tool_result.get("sources", [])
            if not sources:
                return "（知识库中没有找到相关内容）"
            lines = []
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
            if not data:
                return f"（未找到与「{tool_result.get('query', '')}」相关的商品）"
            lines = [tool_result.get("message", ""), ""]
            for i, item in enumerate(data, 1):
                lines.append(f"{i}. **{item.get('name', '')}**")
                lines.append(f"   SPU编号：{item.get('id', '')}  |  价格区间：¥{item.get('min_price', '0')} ~ ¥{item.get('max_price', '0')}")
                lines.append(f"   销量：{item.get('sale_count', 0)}  |  库存：{item.get('total_stock', 0)}")
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
    intent = state.get("intent", "other")
    if intent in ("product_consult", "order_query", "cancel_order", "confirm_receipt",
                    "cart_query", "cart_add", "cart_clear", "cart_delete", "cart_update", "after_sales",
                    "favorite_query", "wallet_query", "product_search", "review_query"):
        return "check_params"
    elif intent == "small_talk":
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
