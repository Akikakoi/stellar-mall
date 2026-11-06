"""推荐追问（Suggestion Chips）生成：LLM 为主 + 意图模板兜底。

方案：
    - A（主）：回答生成后，用对话模型（deepseek-chat）基于「用户问题 + 已有回答」
      生成 3 条用户最可能的追问。短 max_tokens + 短超时 + 零重试，失败静默降级。
    - B（兜底）：按意图分类的规则模板池随机抽样。用于 LLM 失败/超时、
      FAQ 旁路、闲聊、老缓存条目（无 suggestions 字段）等场景。

缓存：建议随回答一起写入三层缓存（L1 data.suggestions / L2 suggestions_json），
命中时直接返回，仅 L3 真调用产生额外开销。
"""
from __future__ import annotations

import concurrent.futures
import random
from typing import List

from app.config import settings
from app.core.logger import logger

# 单条建议的长度限制（字符），超出视为 LLM 输出异常直接丢弃
_SUGGEST_MIN_LEN = 4
_SUGGEST_MAX_LEN = 30

# 建议条数
_SUGGEST_COUNT = 3

# 硬性总时长闸门（秒）。httpx 的 request_timeout 只是「读间隙」超时，
# DeepSeek 慢速滴流 token 时总时长可拖到 30s+，必须用 future.result(timeout) 兜底，
# 避免 done 事件被建议生成无限拖后。
_SUGGEST_HARD_DEADLINE = 8.0

# ==============================================
# 方案 B：按意图的规则模板池（随机抽样）
# ==============================================
_SUGGESTION_TEMPLATES: dict = {
    "product_search": [
        "销量最高的是哪款？",
        "这两款有什么区别？",
        "最便宜的是哪款？",
        "还有货吗？",
        "有哪些优惠活动？",
    ],
    "product_consult": [
        "支持几天无理由退货？",
        "这个商品有优惠吗？",
        "保修政策是怎样的？",
        "大概什么时候发货？",
        "和同类商品比有什么优势？",
    ],
    "order_query": [
        "怎么查看物流信息？",
        "订单可以取消吗？",
        "大概多久能发货？",
        "怎么确认收货？",
        "怎么查看订单详情？",
    ],
    "after_sales": [
        "退款多久能到账？",
        "退货流程是什么？",
        "退货运费谁承担？",
        "可以换货吗？",
        "售后进度怎么查询？",
    ],
    "small_talk": [
        "帮我推荐几款热门商品",
        "最近有哪些新品上架？",
        "怎么查订单状态？",
        "有哪些优惠活动？",
    ],
}

# 未识别意图 / other 等场景的通用池
_DEFAULT_TEMPLATES = [
    "帮我推荐几款热门商品",
    "平台支持几天无理由退货？",
    "怎么查订单状态？",
    "有哪些优惠活动？",
    "商品保修政策是怎样的？",
]


def rule_suggestions(intent: str = "other", count: int = _SUGGEST_COUNT) -> List[str]:
    """方案 B：按意图模板池随机抽样生成追问建议。零成本零延迟，永不失败。"""
    pool = _SUGGESTION_TEMPLATES.get(intent) or _DEFAULT_TEMPLATES
    n = min(count, len(pool))
    return random.sample(pool, n)


# ==============================================
# 方案 A：LLM 生成（主路径）
# ==============================================
_SUGGEST_SYSTEM_PROMPT = """你是电商客服的追问推荐器。根据【用户问题】和【助手回答】，站在用户角度推测他接下来最可能提出的追问。

要求：
1. 输出恰好 3 条追问，每条不超过 20 个字
2. 口语化、以问号结尾，与当前对话内容强相关（商品名/订单/售后等具体内容）
3. 一行一条，不要编号，不要任何解释或额外文字"""


def _clean_llm_lines(raw: str) -> List[str]:
    """清洗 LLM 输出：去编号/符号、限长、去重。"""
    import re

    seen = set()
    out: List[str] = []
    for line in (raw or "").splitlines():
        s = line.strip()
        if not s:
            continue
        # 去掉常见序号前缀：1. / 1、/ 1） / - / * / ・
        s = re.sub(r"^[\d]+[.、)）]\s*", "", s)
        s = re.sub(r"^[-*•・]\s*", "", s)
        s = s.strip().strip('"“”‘’')
        if not (_SUGGEST_MIN_LEN <= len(s) <= _SUGGEST_MAX_LEN):
            continue
        if s in seen:
            continue
        seen.add(s)
        out.append(s)
        if len(out) >= _SUGGEST_COUNT:
            break
    return out


def _get_suggest_llm():
    """独立的轻量 LLM 实例：短 max_tokens + 短超时 + 零重试，避免影响主链路配置。"""
    api_key = settings.LLM_API_KEY or settings.DASHSCOPE_API_KEY
    if not api_key:
        raise RuntimeError("LLM_API_KEY / DASHSCOPE_API_KEY 均为空，无法生成追问建议")
    base_url = settings.LLM_BASE_URL or settings.OPENAI_COMPATIBLE_BASE_URL
    from langchain_openai import ChatOpenAI
    return ChatOpenAI(
        model=settings.LLM_MODEL_NAME,
        api_key=api_key,
        base_url=base_url,
        temperature=0.8,        # 稍高温度，避免每次推荐一模一样
        max_tokens=150,
        request_timeout=6,
        max_retries=0,          # 失败立即降级，不重试
        streaming=False,
        disable_streaming=True,
    )


def _invoke_suggest_llm(user_msg: str) -> str:
    """执行一次建议生成的 LLM 调用，返回原始文本。"""
    llm = _get_suggest_llm()
    resp = llm.invoke([
        {"role": "system", "content": _SUGGEST_SYSTEM_PROMPT},
        {"role": "user", "content": user_msg},
    ])
    return str(resp.content if hasattr(resp, "content") else resp)


def gen_suggestions_sync(
    query: str,
    answer: str,
    intent: str = "other",
    count: int = _SUGGEST_COUNT,
) -> List[str]:
    """生成追问建议：LLM 为主，任何异常静默降级到意图模板，永不抛错。

    在 LangGraph 工作线程 / asyncio.to_thread 中同步调用，调用方无需再包线程。

    :param query: 用户问题
    :param answer: 助手已生成的完整回答
    :param intent: 意图分类结果（用于兜底模板）
    :param count: 建议条数
    :return: 建议列表（最多 count 条；LLM 失败时返回规则模板）
    """
    user_msg = f"【用户问题】{query}\n\n【助手回答】{(answer or '')[:1500]}"
    executor = concurrent.futures.ThreadPoolExecutor(max_workers=1, thread_name_prefix="suggest-gen")
    try:
        future = executor.submit(_invoke_suggest_llm, user_msg)
        # 硬性总时长闸门：超时立即降级，不陪 DeepSeek 慢流干等
        raw = future.result(timeout=_SUGGEST_HARD_DEADLINE)
        suggestions = _clean_llm_lines(raw)
        if suggestions:
            return suggestions[:count]
        logger.debug("[Suggest] LLM 输出为空或全部被过滤，退回规则模板")
    except concurrent.futures.TimeoutError:
        logger.warning(f"[Suggest] LLM 生成追问超过 {_SUGGEST_HARD_DEADLINE}s 硬闸门，退回规则模板")
    except Exception as e:  # noqa: BLE001
        logger.warning(f"[Suggest] LLM 生成追问失败，退回规则模板: {e}")
    finally:
        # 不等待残留线程（其内部受 request_timeout 约束会自行结束），避免阻塞主链路
        executor.shutdown(wait=False)
    return rule_suggestions(intent, count)
