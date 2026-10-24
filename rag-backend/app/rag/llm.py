"""LLM 客户端封装：基于 OpenAI 兼容协议，兼容 DeepSeek / 阿里云百炼（通义千问）。

配置来源：
  - LLM_API_KEY / LLM_BASE_URL：对话模型专用（如 DeepSeek：
    https://api.deepseek.com/v1 + deepseek-chat）
  - 两者留空时回退到 DASHSCOPE_API_KEY / OPENAI_COMPATIBLE_BASE_URL（百炼）

兼容策略：优先走 ChatOpenAI；若 endpoint 属于百炼（阿里云），再回退 ChatTongyi
（百炼原生 SDK）。DeepSeek 等第三方 endpoint 不做 ChatTongyi 回退。
"""
from __future__ import annotations

from app.config import settings
from app.core.logger import logger


def _is_dashscope_endpoint(base_url: str) -> bool:
    """判断 endpoint 是否属于阿里云百炼（DashScope）。"""
    url = (base_url or "").lower()
    return "dashscope" in url or "aliyuncs" in url


def get_langchain_chat():
    """返回 LangChain 可用的 Chat Model。优先使用 OpenAI 兼容模式 ChatOpenAI。"""
    api_key = settings.LLM_API_KEY or settings.DASHSCOPE_API_KEY
    if not api_key:
        raise RuntimeError("未配置 LLM_API_KEY（或 DASHSCOPE_API_KEY），请在 backend/.env 中填写")

    base_url = settings.LLM_BASE_URL or settings.OPENAI_COMPATIBLE_BASE_URL

    # 通用参数：两边都要
    # request_timeout：ChatOpenAI 与 ChatTongyi 均支持该字段名；
    # max_retries：仅 ChatOpenAI 支持，ChatTongyi 单独处理（见下）。
    common = dict(
        model=settings.LLM_MODEL_NAME,
        temperature=settings.LLM_TEMPERATURE,
        max_tokens=settings.LLM_MAX_TOKENS,
        request_timeout=settings.LLM_TIMEOUT_SECONDS,
        streaming=True,
    )

    # 1) 优先：ChatOpenAI（OpenAI 兼容协议）
    try:
        from langchain_openai import ChatOpenAI
        llm = ChatOpenAI(
            api_key=api_key,
            base_url=base_url,
            max_retries=settings.LLM_MAX_RETRIES,
            **common,
        )
        logger.info(
            f"[LLM] 使用 ChatOpenAI(OpenAI兼容): model={settings.LLM_MODEL_NAME}"
            f" base_url={base_url}"
        )
        return llm
    except Exception as e:  # noqa
        if not _is_dashscope_endpoint(base_url):
            raise RuntimeError(f"无法初始化 LLM 客户端（ChatOpenAI 失败）: {e}")
        logger.warning(f"ChatOpenAI 初始化失败，尝试 ChatTongyi: {e}")

    # 2) 兜底：ChatTongyi（仅百炼 endpoint；第三方 endpoint 在上一分支已抛错）
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
