from typing import List, TypedDict, Optional, Annotated, Any
from langgraph.graph.message import add_messages


class AgentState(TypedDict, total=False):
    messages: Annotated[list, add_messages]
    intent: Optional[str]
    intent_confidence: Optional[float]
    uses_react: Optional[bool]        # 是否走 ReAct 真流式（other 场景）
    agent_thought: Optional[str]      # ReAct/智能体思考过程
    required_params: dict
    missing_params: List[str]
    current_tool: Optional[str]
    tool_results: dict
    sources: list
    user_id: Optional[int]
    mall_token: Optional[str]
    conversation_history: List[tuple]
    final_answer: Optional[str]
    stream_chunks: List[str]
    kb_tags_filter: Optional[list]   # 意图覆盖时附加的 tags_filter
    cart_listing: Optional[List[str]]  # 最近一次向用户展示的购物车条目 ID 顺序（"第N个"→真实 ID 映射）
