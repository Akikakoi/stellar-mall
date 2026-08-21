"""AI 智能查数（ChatBI）路由（Mall 内部接口）。

流程：
  1. Mall 后端把"管理员自然语言问题 + 数据库 Schema"发到 /chat_bi/sql，
     由 LLM 生成安全的单条 SELECT 聚合查询 + 图表可视化配置；
  2. Mall 后端本地做 SQL 白名单校验并执行；
  3. Mall 后端把查询结果发到 /chat_bi/summary，由 LLM 生成自然语言回答。

仅限 Mall 后端调用，使用共享密钥 X-Stellar-Rag-Sync-Secret 头鉴权，
不走 JWT，不走前端登录鉴权链路（与 internal.py 同一套约定）。
"""
from __future__ import annotations

import json
import re
from typing import Any, Dict, List, Optional

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field

from app.api.internal import _require_sync_secret
from app.core.exceptions import BizError, ok
from app.core.logger import logger
from app.rag.llm import get_langchain_chat

router = APIRouter()


# ---------- 请求/响应模型 ----------
class ChatBiSqlRequest(BaseModel):
    question: str = Field(..., min_length=1, max_length=500, description="管理员自然语言问题")
    schema_ddl: str = Field(..., min_length=1, description="白名单表的结构说明（由 Mall 端提供）")


class ChatBiSummaryRequest(BaseModel):
    question: str = Field(..., min_length=1, max_length=500)
    result_json: str = Field(..., min_length=1, max_length=20000,
                             description="SQL 查询结果 JSON（Mall 端已截断）")


# ---------- Prompt ----------
SQL_SYSTEM_PROMPT = (
    "你是 stellar-mall 商城的 MySQL 数据分析专家。用户会用中文提出经营分析问题，"
    "你需要根据给定的表结构生成一条安全的分析 SQL。硬性规则：\n"
    "1. 只能生成【单条 SELECT 查询】，禁止 INSERT/UPDATE/DELETE/DDL/存储过程/变量/分号。\n"
    "2. 统计口径必须遵循：有效销售额 = stellar_mall_order.status IN ('PAID','SHIPPED','COMPLETED') "
    "AND is_refunded = 0；统计订单量时排除已取消订单（status <> 'CANCELLED'）。\n"
    "3. 时间理解：「今天」= DATE(时间列) = CURDATE()；「昨天」= DATE_SUB(CURDATE(), INTERVAL 1 DAY)；"
    "「近7天/最近一周」= 时间列 >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)；"
    "「上周」= 上一个自然周（YEARWEEK(时间列, 1) = YEARWEEK(CURDATE(), 1) - 1）。\n"
    "4. SELECT 列必须用中文别名，写法 AS '中文别名'（单引号字符串别名）。\n"
    "5. 聚合结果默认加 LIMIT 100；明细类查询 LIMIT 20。\n"
    "6. 只使用给定表结构中存在的表和列，禁止猜测不存在的列。\n"
    "你只能输出一个 JSON 对象（不要 markdown 代码块、不要多余文字），格式：\n"
    '{"sql": "SELECT ...", "title": "图表标题(中文)", '
    '"chart_type": "bar|line|pie|table", "x_field": "维度列中文别名", "y_field": "数值列中文别名"}\n'
    "chart_type 选择：时间趋势用 line；类别对比用 bar；两列数据的占比构成用 pie；"
    "三列及以上或明细列表用 table（此时 x_field/y_field 填空字符串）。"
)

SUMMARY_SYSTEM_PROMPT = (
    "你是 stellar-mall 商城的数据分析师。用户提了一个经营分析问题，"
    "你已经查询到了结果数据（JSON）。请用中文直接回答用户问题：\n"
    "1. 先给一句话结论，引用具体数字（含单位，金额单位为元）。\n"
    "2. 如有多行数据，点出最值或前几名。\n"
    "3. 2~4 句话以内，不要列表、不要客套话，禁止编造数据中没有的数字。\n"
    "4. 如果结果为空数组，就如实说明该条件下暂无数据。"
)


# ---------- 辅助 ----------
_FENCE_RE = re.compile(r"^```(?:json)?\s*|\s*```$", re.MULTILINE)


def _extract_json(text: str) -> Dict[str, Any]:
    """从 LLM 输出中提取 JSON 对象（容忍 markdown 代码块和前后杂讯）。"""
    cleaned = _FENCE_RE.sub("", text.strip())
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start < 0 or end <= start:
        raise ValueError(f"LLM 输出中找不到 JSON 对象: {text[:200]}")
    try:
        obj = json.loads(cleaned[start:end + 1])
    except json.JSONDecodeError as e:
        raise ValueError(f"LLM 输出的 JSON 解析失败: {e}; 原文: {cleaned[:300]}") from e
    if not isinstance(obj, dict):
        raise ValueError("LLM 输出的 JSON 不是对象")
    return obj


def _invoke_llm(system_prompt: str, user_prompt: str) -> str:
    from langchain_core.messages import HumanMessage, SystemMessage

    try:
        llm = get_langchain_chat()
        resp = llm.invoke([
            SystemMessage(content=system_prompt),
            HumanMessage(content=user_prompt),
        ])
        return (getattr(resp, "content", "") or "").strip()
    except BizError:
        raise
    except Exception as e:  # noqa: BLE001
        logger.exception("[chat_bi] LLM 调用失败")
        raise BizError(f"LLM 调用失败: {e}", code=50001, http_status=500) from e


# ---------- 路由 ----------
@router.post("/chat_bi/sql", summary="ChatBI 第一步：问题 + Schema → SELECT SQL + 图表配置")
def generate_sql(
    req: ChatBiSqlRequest,
    _auth: None = Depends(_require_sync_secret),
) -> dict:
    user_prompt = (
        f"数据库表结构如下：\n{req.schema_ddl}\n\n"
        f"用户问题：{req.question}\n"
        "请输出 JSON。"
    )
    text = _invoke_llm(SQL_SYSTEM_PROMPT, user_prompt)
    try:
        plan = _extract_json(text)
    except ValueError as e:
        raise BizError(f"LLM 返回的 SQL 计划不可解析: {e}", code=50002, http_status=500) from e

    sql = str(plan.get("sql") or "").strip()
    if not sql:
        raise BizError("LLM 未生成 SQL", code=50002, http_status=500)
    # 规范化 chart_type，未知值一律降级为 table
    chart_type = str(plan.get("chart_type") or "table").strip().lower()
    if chart_type not in ("bar", "line", "pie", "table"):
        chart_type = "table"

    logger.info(f"[chat_bi] 问题「{req.question[:50]}」→ SQL({len(sql)} 字符) chart={chart_type}")
    return ok({
        "sql": sql,
        "title": str(plan.get("title") or "查询结果"),
        "chart_type": chart_type,
        "x_field": str(plan.get("x_field") or ""),
        "y_field": str(plan.get("y_field") or ""),
    })


@router.post("/chat_bi/summary", summary="ChatBI 第三步：问题 + 查询结果 → 自然语言回答")
def generate_summary(
    req: ChatBiSummaryRequest,
    _auth: None = Depends(_require_sync_secret),
) -> dict:
    user_prompt = f"用户问题：{req.question}\n\n查询结果数据：\n{req.result_json}"
    text = _invoke_llm(SUMMARY_SYSTEM_PROMPT, user_prompt)
    if not text:
        raise BizError("LLM 返回了空的总结", code=50003, http_status=500)
    return ok({"summary": text})
