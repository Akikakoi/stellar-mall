"""LLM 客户端封装：基于 DashScope / 通义千问，兼容 LangChain ChatModel。

支持两类 API Key：
  1) 公共账号 Key（sk-xxx）：默认走 https://dashscope.aliyuncs.com
  2) 专属业务空间 Key（sk-ws-xxx）：必须走 settings.OPENAI_COMPATIBLE_BASE_URL
     里配置的 workspace 专属域名，否则会报 InvalidApiKey。

策略：优先走 ChatOpenAI（OpenAI 兼容模式），用户体验更稳定；若失败再回退
ChatTongyi（百炼原生 SDK）。两种模式都会读取 settings.OPENAI_COMPATIBLE_BASE_URL /
settings.DASHSCOPE_BASE_URL 作为 base_url。
"""
from __future__ import annotations
from typing import Any

from app.config import settings
from app.core.logger import logger


def _is_workspace_key(api_key: str) -> bool:
    """判断是不是专属业务空间 Key（sk-ws- 开头），这类 Key 必须走 workspace 专属域名。"""
    return isinstance(api_key, str) and api_key.startswith("sk-ws-")


def get_langchain_chat():
    """返回 LangChain 可用的 Chat Model。优先使用 OpenAI 兼容模式 ChatOpenAI。"""
    api_key = settings.DASHSCOPE_API_KEY
    if not api_key:
        raise RuntimeError("未配置 DASHSCOPE_API_KEY，请在 backend/.env 中填写")

    # 通用参数：两边都要
    common = dict(
        model=settings.LLM_MODEL_NAME,
        temperature=settings.LLM_TEMPERATURE,
        max_tokens=settings.LLM_MAX_TOKENS,
        streaming=True,
    )

    # 1) 优先：ChatOpenAI（OpenAI 兼容协议，业务空间 Key 必须走这里 + 自定义 base_url）
    base_url = settings.OPENAI_COMPATIBLE_BASE_URL
    try:
        from langchain_openai import ChatOpenAI
        llm = ChatOpenAI(api_key=api_key, base_url=base_url, **common)
        logger.info(
            f"[LLM] 使用 ChatOpenAI(DashScope兼容模式): model={settings.LLM_MODEL_NAME}"
            f" base_url={base_url}"
            + (" workspace专用Key" if _is_workspace_key(api_key) else "")
        )
        return llm
    except Exception as e:  # noqa
        logger.warning(f"ChatOpenAI 初始化失败，尝试 ChatTongyi: {e}")

    # 2) 兜底：ChatTongyi（百炼原生 langchain_community 适配器）
    #    注意：ChatTongyi 主要支持公共 Key，业务空间 Key 很可能因 host 不匹配而失败
    try:
        from langchain_community.chat_models.tongyi import ChatTongyi
        llm = ChatTongyi(
            dashscope_api_key=api_key,
            # dashscope_base_url 参数仅在较新版本的 langchain-community 才有，
            # 用 **kwargs 传，缺失时忽略，避免 AttributeError
            **({"dashscope_base_url": settings.DASHSCOPE_BASE_URL}
               if settings.DASHSCOPE_BASE_URL else {}),
            **common,
        )
        logger.info(f"[LLM] 使用 ChatTongyi: model={settings.LLM_MODEL_NAME}")
        return llm
    except Exception as e:  # noqa
        raise RuntimeError(
            f"无法初始化 LLM 客户端（ChatOpenAI + ChatTongyi 都失败）。"
            f"最后一个错误: {e}"
        )
