"""LangChain RAG 链路遗留公共成员。

历史背景：本文件曾是「RAG 直连链路」（查询改写 + Prompt + 流式生成 + 引用来源）的
实现地，该链路已被 /api/chat 的统一 Agent 链路（app/agent/graph.py）取代，
生产上没有任何入口能到达（见 2026-10 排查），连同 rag_chat_service.py 一起删除。

目前仅保留一处仍被其他模块引用的成员：
- _fix_garbled_tags：app/agent/tools/kb_tool.py 修复历史 ES 数据编码错乱
"""
from __future__ import annotations


def _fix_garbled_tags(text) -> str:
    """修复历史数据中可能存在的 GBK→Latin-1 编码错乱。

    P2 后 ES 里 tags 是数组（list[str]），这里统一 join 回逗号字符串，
    保持对外输出与 Chroma 时代的格式一致。
    """
    if isinstance(text, (list, tuple)):
        text = ",".join(str(t) for t in text)
    text = str(text or "")
    if not text or all(ord(c) < 128 or '\u4e00' <= c <= '\u9fff' or c.isspace() for c in text):
        return text
    try:
        fixed = text.encode('latin-1').decode('gbk')
        if any('\u4e00' <= c <= '\u9fff' for c in fixed):
            return fixed
    except (UnicodeEncodeError, UnicodeDecodeError):
        pass
    return text
