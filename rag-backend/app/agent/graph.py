"""LangGraph 状态图定义与构建。"""
from __future__ import annotations

from langgraph.graph import StateGraph, END

from app.agent.state import AgentState
from app.agent.nodes import (
    intent_classification_node,
    check_params_node,
    ask_params_node,
    tool_execution_node,
    generate_answer_node,
    route_after_intent,
    route_after_params,
)


def build_agent_graph():
    """构建电商客服智能体状态图。

    图结构：
        [intent_classification] → 路由
            → product_consult/order_query/after_sales → [check_params] → 路由
                                                                 → 缺参 → [ask_params] → END
                                                                 → 齐全 → [execute_tool] → [generate_answer] → END
            → small_talk/other → [generate_answer] → END
    """
    workflow = StateGraph(AgentState)

    workflow.add_node("intent_classification", intent_classification_node)
    workflow.add_node("check_params", check_params_node)
    workflow.add_node("ask_params", ask_params_node)
    workflow.add_node("execute_tool", tool_execution_node)
    workflow.add_node("generate_answer", generate_answer_node)

    workflow.set_entry_point("intent_classification")

    workflow.add_conditional_edges(
        "intent_classification",
        route_after_intent,
        {
            "check_params": "check_params",
            "generate_answer": "generate_answer",
        }
    )

    workflow.add_conditional_edges(
        "check_params",
        route_after_params,
        {
            "ask_params": "ask_params",
            "execute_tool": "execute_tool",
        }
    )

    workflow.add_edge("ask_params", END)
    workflow.add_edge("execute_tool", "generate_answer")
    workflow.add_edge("generate_answer", END)

    return workflow.compile()


agent_graph = build_agent_graph()


def run_agent(query: str, user_id: int = None, mall_token: str = None,
              conversation_history: list = None) -> dict:
    """同步运行 Agent，返回结果。

    Args:
        query: 用户问题
        user_id: 用户ID
        mall_token: 商城用户 token
        conversation_history: 历史对话列表 [(role, content), ...]

    Returns:
        dict: {
            "answer": str,
            "sources": list,
            "intent": str,
            "tool_results": dict,
            "missing_params": list,
        }
    """
    from langchain_core.messages import AIMessage, HumanMessage

    # 把历史对话还原成 LangChain 消息对象，注入 messages 列表；
    # 借助 add_messages reducer，所有节点（意图识别、参数检查、追问生成等）
    # 都能自然获得完整上下文，避免"短回答（2/1/是/否）"被当作独立问题处理
    messages: list = []
    for role, content in (conversation_history or []):
        if role == "user":
            messages.append(HumanMessage(content=content))
        elif role == "assistant":
            messages.append(AIMessage(content=content))
    messages.append(HumanMessage(content=query))

    state = {
        "messages": messages,
        "user_id": user_id,
        "mall_token": mall_token,
        "conversation_history": conversation_history or [],
        "required_params": {},
    }

    result = agent_graph.invoke(state)

    return {
        "answer": result.get("final_answer", ""),
        "sources": result.get("sources", []),
        "intent": result.get("intent", "other"),
        "tool_results": result.get("tool_results", {}),
        "missing_params": result.get("missing_params", []),
        "current_tool": result.get("current_tool"),
    }
