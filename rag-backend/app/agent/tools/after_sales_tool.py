import datetime
from langchain_core.tools import tool
from typing import Optional
from sqlalchemy import Column, Integer, String, DateTime, Text
from sqlalchemy.orm import Session

from app.core.database import Base, SessionLocal
from app.core.logger import logger


class AfterSalesTicket(Base):
    """售后工单表（模拟）。"""
    __tablename__ = "after_sales_tickets"

    id = Column(Integer, primary_key=True, index=True)
    ticket_no = Column(String(64), unique=True, index=True, comment="工单号")
    order_id = Column(String(64), comment="关联订单号")
    user_id = Column(Integer, index=True, comment="用户ID")
    type = Column(String(32), comment="售后类型：refund退款/return退货/exchange换货/other其他")
    reason = Column(Text, comment="售后原因")
    description = Column(Text, comment="详细描述")
    status = Column(String(32), default="pending", comment="状态：pending待处理/processing处理中/resolved已解决/rejected已拒绝")
    contact_phone = Column(String(32), comment="联系电话")
    create_time = Column(DateTime, default=datetime.datetime.utcnow)
    update_time = Column(DateTime, default=datetime.datetime.utcnow, onupdate=datetime.datetime.utcnow)


def init_after_sales_table():
    """确保售后工单表存在。"""
    try:
        Base.metadata.create_all(bind=SessionLocal.kw['bind'] if hasattr(SessionLocal, 'kw') else None,
                         tables=[AfterSalesTicket.__table__])
    except Exception:
        pass


@tool
def apply_after_sales_tool(
    order_id: str,
    after_sales_type: str,
    reason: str,
    description: Optional[str] = "",
    contact_phone: Optional[str] = "",
    user_id: Optional[int] = None,
) -> dict:
    """提交售后申请工具，用于帮助用户提交退货、换货、退款等售后申请。

    Args:
        order_id: 订单号（必填）
        after_sales_type: 售后类型，可选：refund(退款)/return(退货)/exchange(换货)/other(其他)
        reason: 售后原因（必填），例如：商品质量问题、尺寸不合适、不想要了等
        description: 详细描述（可选），补充说明问题
        contact_phone: 联系电话（可选）
        user_id: 用户ID
    """
    type_map = {
        "refund": "退款",
        "return": "退货",
        "exchange": "换货",
        "other": "其他",
    }

    if after_sales_type not in type_map:
        return {
            "success": False,
            "message": f"不支持的售后类型：{after_sales_type}，请选择 refund/return/exchange/other",
            "ticket_no": None,
        }

    try:
        from app.core.database import engine
        Base.metadata.create_all(bind=engine, tables=[AfterSalesTicket.__table__])

        db = SessionLocal()
        try:
            ticket_no = f"AS{datetime.datetime.now().strftime('%Y%m%d%H%M%S')}{user_id or 0:04d}"

            ticket = AfterSalesTicket(
                ticket_no=ticket_no,
                order_id=order_id,
                user_id=user_id or 0,
                type=after_sales_type,
                reason=reason,
                description=description or "",
                status="pending",
                contact_phone=contact_phone or "",
            )
            db.add(ticket)
            db.commit()
            db.refresh(ticket)

            return {
                "success": True,
                "ticket_no": ticket_no,
                "order_id": order_id,
                "type": after_sales_type,
                "type_text": type_map[after_sales_type],
                "reason": reason,
                "status": "pending",
                "status_text": "待处理",
                "message": f"售后申请已提交，工单号：{ticket_no}，我们会在1-3个工作日内处理",
            }
        finally:
            db.close()
    except Exception as e:
        logger.error(f"售后申请提交失败: {e}")
        return {
            "success": False,
            "ticket_no": None,
            "message": f"售后申请提交失败：{str(e)}",
        }
