"""AI 经营日报生成路由（Mall 内部接口）。

Mall 后端汇总当日经营统计数据后调用本接口，由 LLM（qwen-plus）生成
自然语言经营日报，供管理端仪表盘展示。

仅限 Mall 后端调用，使用共享密钥 X-Stellar-Rag-Sync-Secret 头鉴权，
不走 JWT，不走前端登录鉴权链路（与 internal.py 同一套约定）。
"""
from __future__ import annotations

import json
from typing import List

from fastapi import APIRouter, Depends
from pydantic import BaseModel, Field

from app.api.internal import _require_sync_secret
from app.config import settings
from app.core.exceptions import BizError, ok
from app.core.logger import logger
from app.rag.llm import get_langchain_chat

router = APIRouter()


# ---------- 请求/响应模型 ----------
class TrendItem(BaseModel):
    """近 N 日单日趋势（日期 + 订单量 + 销售额）。"""
    date: str = Field(..., description="日期，如 08-19")
    order_count: int = 0
    sales_amount: float = 0.0


class TopProduct(BaseModel):
    """近 7 日热销商品条目。"""
    name: str = Field(..., min_length=1, max_length=200)
    qty: int = Field(default=0, ge=0)
    sales: float = Field(default=0.0, ge=0)


class DailyReportRequest(BaseModel):
    date: str = Field(..., description="统计日期 YYYY-MM-DD")
    today_orders: int = Field(default=0, ge=0)
    today_sales: float = Field(default=0.0, ge=0)
    yesterday_orders: int = Field(default=0, ge=0)
    yesterday_sales: float = Field(default=0.0, ge=0)
    pending_shipment_orders: int = Field(default=0, ge=0, description="待发货订单数（PAID）")
    pending_after_sales: int = Field(default=0, ge=0, description="待处理售后工单数")
    low_stock_count: int = Field(default=0, ge=0, description="低库存 SKU 数")
    new_users_today: int = Field(default=0, ge=0, description="今日新增用户数")
    order_trend: List[TrendItem] = Field(default_factory=list, description="近 7 日趋势")
    top_products: List[TopProduct] = Field(default_factory=list, description="近 7 日热销 TOP")


# ---------- Prompt ----------
SYSTEM_PROMPT = (
    "你是 stellar-mall 商城的资深经营分析师。你会收到一份当日的经营统计数据（JSON），"
    "请基于数据生成一份简明的中文经营日报，供商城管理员快速了解经营状况。要求：\n"
    "1. 使用固定结构，依次为四个小节标题：【今日概览】【趋势分析】【风险与预警】【建议行动】。\n"
    "2. 每个小节 2~4 条要点，每条独占一行，以「· 」开头，标题行不加编号。\n"
    "3. 计算环比时给出具体百分比；昨日基数为 0 时说明无法计算环比。\n"
    "4. 只依据给定数据分析，绝不编造或脑补数据；某项数据为空或为 0 时如实说明。\n"
    "5. 风险与预警部分重点关注：销售额环比明显下滑、待发货/售后积压、低库存。\n"
    "6. 全文控制在 400 字以内，语言专业、直接，不要客套话和开场白。"
)


def _build_user_prompt(req: DailyReportRequest) -> str:
    stats = req.model_dump()
    return (
        f"以下是 {req.date} 的 stellar-mall 经营统计数据（JSON）：\n"
        f"{json.dumps(stats, ensure_ascii=False, indent=2)}\n"
        "请生成经营日报。"
    )


# ---------- 路由 ----------
@router.post(
    "/daily_report",
    summary="Mall → RAG 生成 AI 经营日报（管理端仪表盘调用）",
)
def generate_daily_report(
    req: DailyReportRequest,
    _auth: None = Depends(_require_sync_secret),
) -> dict:
    from langchain_core.messages import HumanMessage, SystemMessage

    try:
        llm = get_langchain_chat()
        resp = llm.invoke([
            SystemMessage(content=SYSTEM_PROMPT),
            HumanMessage(content=_build_user_prompt(req)),
        ])
        report = (getattr(resp, "content", "") or "").strip()
    except Exception as e:  # noqa: BLE001
        logger.exception("[daily_report] LLM 调用失败")
        raise BizError(
            f"LLM 生成日报失败: {e}",
            code=50001,
            http_status=500,
        ) from e

    if not report:
        raise BizError("LLM 返回了空的日报内容", code=50002, http_status=500)

    logger.info(
        f"[daily_report] {req.date} 日报生成成功 model={settings.LLM_MODEL_NAME} "
        f"len={len(report)}"
    )
    return ok({"report": report, "model": settings.LLM_MODEL_NAME})
