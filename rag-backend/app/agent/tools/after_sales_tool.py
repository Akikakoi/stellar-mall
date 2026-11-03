"""售后申请工具：直接调用商城 /user/aftersale，与管理端售后页共用同一套数据。

历史实现把工单写进 rag-backend 自己的 after_sales_tickets 表，商城后台完全看不到，
属于「AI 说已提交、实际无人处理」的幽灵工单。现改为走商城真实接口。
"""
from typing import Optional

from langchain_core.tools import tool

from app.agent.tools._mall_response import unwrap_mall
from app.agent.tools.mall_client import call_mall
from app.core.logger import logger

# 商城 AfterSaleSubmitDTO.type：1 仅退款 / 2 退货退款 / 3 换货
_TYPE_CODE = {"refund": 1, "return": 2, "exchange": 3}
_TYPE_TEXT = {1: "仅退款", 2: "退货退款", 3: "换货"}


def _failure(message: str, **extra) -> dict:
    data = {"success": False, "ticket_no": None, "message": message}
    data.update(extra)
    return data


def _load_order(order_id: str, mall_token: str) -> tuple[dict | None, Optional[str]]:
    """按订单 ID 或订单号查询订单，返回 (订单, 错误信息)。"""
    raw = str(order_id or "").strip()
    if not raw:
        return None, "请提供订单ID或订单号"

    # 纯数字按订单 ID 查详情；否则（或详情未命中）从「我的订单」按订单号匹配
    if raw.isdigit():
        result = call_mall("GET", f"/user/order/{raw}", mall_token=mall_token)
        data, err = unwrap_mall(result, "订单查询失败或订单不存在")
        if not err and isinstance(data.get("data"), dict):
            return data["data"], None

    result = call_mall("GET", "/user/order/list", mall_token=mall_token)
    data, err = unwrap_mall(result, "订单查询失败")
    if err:
        return None, err
    for order in data.get("data") or []:
        if isinstance(order, dict) and str(order.get("orderNo") or "").strip() == raw:
            return order, None
    return None, "未找到对应的订单，请确认订单ID/订单号是否正确"


def _item_label(item: dict) -> str:
    name = item.get("spuName") or item.get("name") or "未命名商品"
    spec = item.get("skuSpecs") or item.get("skus") or ""
    sku = item.get("skuId")
    return f"{name}{(' ' + str(spec)) if spec else ''}（SKU {sku}）"


@tool
def apply_after_sales_tool(
    order_id: str,
    after_sales_type: str,
    reason: str,
    sku_id: Optional[str] = None,
    description: Optional[str] = "",
    contact_phone: Optional[str] = "",
    mall_token: Optional[str] = None,
) -> dict:
    """提交售后申请，直接写入商城真实售后系统（与管理端售后页同一张表）。

    Args:
        order_id: 订单ID或订单号（必填）
        after_sales_type: 售后类型：refund(仅退款)/return(退货退款)/exchange(换货)
        reason: 售后原因（必填）
        sku_id: 要售后的商品 SKU ID（订单只有一件商品时可省略）
        description: 详细描述（可选）
        contact_phone: 联系电话（可选，会并入售后描述）
        mall_token: 商城用户 token，用于身份验证
    """
    type_code = _TYPE_CODE.get(str(after_sales_type or "").strip().lower())
    if type_code is None:
        return _failure(
            f"不支持的售后类型：{after_sales_type}，请选择 refund(仅退款)/return(退货退款)/exchange(换货)"
        )
    if not mall_token:
        return _failure("请先登录后再申请售后")

    order, err = _load_order(order_id, mall_token)
    if err:
        return _failure(err)
    items = [it for it in (order.get("items") or []) if isinstance(it, dict)]
    if not items:
        return _failure("该订单没有商品明细，无法申请售后")

    selected = None
    if sku_id:
        selected = next((it for it in items if str(it.get("skuId")) == str(sku_id)), None)
        if selected is None:
            return _failure(f"该订单中不存在 SKU {sku_id}，请确认要申请售后的商品")
    elif len(items) == 1:
        selected = items[0]
    else:
        listing = "、".join(_item_label(it) for it in items)
        return _failure(
            f"该订单包含多件商品（{listing}），请指定要申请售后的商品 SKU；"
            "也可以直接到「我的售后」页面按商品提交。",
            need_sku=True,
            items=[{"sku_id": it.get("skuId"), "label": _item_label(it)} for it in items],
        )

    if selected.get("refunded"):
        return _failure("该商品已完成退款，请勿重复申请")
    sku = selected.get("skuId")
    order_pk = order.get("id")
    if sku is None or order_pk is None:
        return _failure("该订单缺少订单或 SKU 信息，无法申请售后")

    detail = str(description or "").strip()
    phone = str(contact_phone or "").strip()
    if phone:
        detail = f"{detail}\n联系电话：{phone}".strip()

    payload = {
        "orderId": int(order.get("id")),
        "skuId": int(sku),
        "type": type_code,
        "reason": str(reason or "").strip(),
        "detail": detail,
    }
    result = call_mall("POST", "/user/aftersale", mall_token=mall_token, json=payload)
    data, err = unwrap_mall(result, "售后申请提交失败")
    if err:
        return _failure(err)

    created = data.get("data") if isinstance(data.get("data"), dict) else {}
    after_sale_id = created.get("id")
    if after_sale_id is None:
        logger.warning("[apply_after_sales] 商城返回成功但缺少售后单 ID: {}", data)
    return {
        "success": True,
        "ticket_no": str(after_sale_id) if after_sale_id is not None else None,
        "after_sale_id": after_sale_id,
        "order_id": order.get("id"),
        "sku_id": sku,
        "type": after_sales_type,
        "type_text": _TYPE_TEXT[type_code],
        "reason": reason,
        "status": created.get("status"),
        "status_text": "申请中",
        "message": f"售后申请已提交，售后单号：{after_sale_id}，可在「我的售后」中查看处理进度",
    }
