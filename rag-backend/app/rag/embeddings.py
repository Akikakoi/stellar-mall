"""Embedding 封装：优先 DashScope 云端 API（用户要求），本地 BGE 仅作兜底。

百炼平台有两类 API Key，对应不同的 Embedding 接入方式：
  1) 公共账号 Key（sk-xxx）：可以用 DashScopeEmbeddings，走
     https://dashscope.aliyuncs.com/api/v1/services/embeddings/...
  2) 专属业务空间 Key（sk-ws-xxx）：业务空间域名未开放百炼原生 services/embeddings
     接口，必须走 OpenAI 兼容协议的 `/v1/embeddings`（与 LLM 同 base_url，即
     settings.OPENAI_COMPATIBLE_BASE_URL），模型名仍然是 text-embedding-v2。
"""
from __future__ import annotations
from app.config import settings
from app.core.logger import logger

_embedding_instance = None


def get_embeddings():
    """返回 LangChain Embeddings 接口的实例（单例）。"""
    global _embedding_instance
    if _embedding_instance is not None:
        return _embedding_instance

    api_key = settings.DASHSCOPE_API_KEY
    is_workspace = isinstance(api_key, str) and api_key.startswith("sk-ws-")

    # 1) 优先：DashScope 云端 Embedding（用户要求走云端）
    if api_key:
        # 1a) 专属业务空间 Key：走 OpenAIEmbeddings + workspace 兼容 base_url
        if is_workspace:
            try:
                from langchain_openai import OpenAIEmbeddings
                # text-embedding-v2 的服务端只支持 input=字符串/字符串数组；
                # langchain_openai 默认会用 tiktoken 切片后把 tokens 数组传进去
                # 导致 `input.contents is neither str nor list of str`。
                # 用 check_embedding_ctx_length=False 跳过 tokenizer ctx 分片；
                # 并用 embedding_ctx_length 设大，避免在 Python 侧拆分 tokens。
                emb = OpenAIEmbeddings(
                    model=settings.DASHSCOPE_EMBEDDING_MODEL,
                    api_key=api_key,
                    base_url=settings.OPENAI_COMPATIBLE_BASE_URL,
                    check_embedding_ctx_length=False,
                    embedding_ctx_length=8191,
                    # dimensions / encoding_format 对 text-embedding-v2 可选，
                    # 不传避免服务端忽略/报错
                )
                logger.info(
                    f"[Embedding] 使用 OpenAIEmbeddings(workspace兼容): "
                    f"{settings.DASHSCOPE_EMBEDDING_MODEL} "
                    f"base_url={settings.OPENAI_COMPATIBLE_BASE_URL}"
                )
                _embedding_instance = emb
                return emb
            except Exception as e:  # noqa
                logger.warning(f"workspace OpenAIEmbedding 初始化失败: {e}")

        # 1b) 公共 Key 或 workspace Key 兜底：DashScopeEmbeddings
        try:
            from langchain_community.embeddings import DashScopeEmbeddings
            kwargs = dict(
                model=settings.DASHSCOPE_EMBEDDING_MODEL,
                dashscope_api_key=api_key,
            )
            # 只有公共 Key 场景（或无法判断）才把自定义 dashscope_base_url 传进去，
            # 避免 workspace 模式下又把请求打到 services/embeddings
            try:
                emb = DashScopeEmbeddings(**kwargs)
            except TypeError:
                # 老版本 DashScopeEmbeddings 不认额外参数，保守构造
                safe_kwargs = {k: v for k, v in kwargs.items()
                               if k in ("model", "dashscope_api_key")}
                emb = DashScopeEmbeddings(**safe_kwargs)
                # 兜底：通过环境变量让 dashscope SDK 读到自定义 host / workspace
                import os as _os
                if settings.DASHSCOPE_BASE_URL:
                    _os.environ.setdefault("DASHSCOPE_BASE_URL", settings.DASHSCOPE_BASE_URL)
                if settings.DASHSCOPE_WORKSPACE_ID:
                    _os.environ.setdefault(
                        "DASHSCOPE_WORKSPACE_ID", settings.DASHSCOPE_WORKSPACE_ID)
            logger.info(
                f"[Embedding] 使用 DashScopeEmbeddings: {settings.DASHSCOPE_EMBEDDING_MODEL}"
            )
            _embedding_instance = emb
            return emb
        except Exception as e:  # noqa
            logger.warning(f"DashScope Embedding 初始化失败，尝试本地: {e}")

    # 2) 兜底：本地 BGE（需要能连 huggingface，失败则直接抛错）
    try:
        from langchain_community.embeddings import HuggingFaceBgeEmbeddings
        emb = HuggingFaceBgeEmbeddings(
            model_name=settings.EMBEDDING_MODEL_NAME,
            model_kwargs={"device": "cpu"},
            encode_kwargs={"normalize_embeddings": True, "show_progress_bar": False},
        )
        logger.info(f"[Embedding] 使用本地 BGE: {settings.EMBEDDING_MODEL_NAME}")
        _embedding_instance = emb
        return emb
    except Exception as e:  # noqa
        raise RuntimeError(f"Embedding 初始化失败（DashScope+本地都不可用）: {e}")
