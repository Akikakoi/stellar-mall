"""MCP (Model Context Protocol) Server — 将商城 Agent 工具暴露为 MCP 工具。

启动方式：
    python -m app.mcp_server          # stdio 模式（TRAE IDE / Claude Desktop）
    mcp run app/mcp_server.py         # 使用 mcp CLI

在 TRAE IDE 中配置（.trae/mcp.json）：
    {
      "mcpServers": {
        "stellar-mall": {
          "command": "python",
          "args": ["-m", "app.mcp_server"],
          "cwd": "${workspaceFolder}/rag-backend"
        }
      }
    }
"""
from __future__ import annotations

from typing import Optional

from mcp.server.fastmcp import FastMCP

from app.config import settings
from app.core.logger import logger

mcp = FastMCP(settings.MCP_SERVER_NAME or "stellar-mall")


# ──────────────────────────────────────────────────────────────
# 商品搜索
# ──────────────────────────────────────────────────────────────
@mcp.tool()
def search_products(
    query: str,
    page: int = 1,
    page_size: int = 10,
    category: Optional[str] = None,
) -> dict:
    """搜索商城商品，支持按名称、分类搜索。"""
    from app.agent.tools.product_search_tool import product_search_tool

    return product_search_tool.invoke({
        "query": query,
        "page": page,
        "page_size": page_size,
        "category": category,
    })


# ──────────────────────────────────────────────────────────────
# 订单查询
# ──────────────────────────────────────────────────────────────
@mcp.tool()
def query_orders(
    order_id: Optional[str] = None,
    status: Optional[int] = None,
    mall_token: Optional[str] = None,
) -> dict:
    """查询订单状态、物流信息、订单详情或订单列表。

    status 取值：1待付款 / 3待收货 / 5已完成 / 0已取消，不填则查询全部。
    """
    from app.agent.tools.order_tool import query_order_tool

    return query_order_tool.invoke({
        "order_id": order_id,
        "status": status,
        "mall_token": mall_token,
    })


# ──────────────────────────────────────────────────────────────
# 取消订单
# ──────────────────────────────────────────────────────────────
@mcp.tool()
def cancel_order(
    order_id: str,
    mall_token: Optional[str] = None,
) -> dict:
    """取消待付款或待发货的订单。"""
    from app.agent.tools.cancel_order_tool import cancel_order_tool

    return cancel_order_tool.invoke({
        "order_id": order_id,
        "mall_token": mall_token,
    })


# ──────────────────────────────────────────────────────────────
# 确认收货
# ──────────────────────────────────────────────────────────────
@mcp.tool()
def confirm_receipt(
    order_id: str,
    mall_token: Optional[str] = None,
) -> dict:
    """确认已收到商品，将订单从待收货变为已完成。"""
    from app.agent.tools.confirm_receipt_tool import confirm_receipt_tool

    return confirm_receipt_tool.invoke({
        "order_id": order_id,
        "mall_token": mall_token,
    })


# ──────────────────────────────────────────────────────────────
# 购物车 - 查看
# ──────────────────────────────────────────────────────────────
@mcp.tool()
def view_cart(
    mall_token: Optional[str] = None,
) -> dict:
    """查看当前用户的购物车商品列表。"""
    from app.agent.tools.cart_tool import query_cart_tool

    return query_cart_tool.invoke({
        "mall_token": mall_token,
    })


# ──────────────────────────────────────────────────────────────
# 购物车 - 加入
# ──────────────────────────────────────────────────────────────
@mcp.tool()
def add_to_cart(
    sku_id: str,
    qty: int = 1,
    mall_token: Optional[str] = None,
) -> dict:
    """将指定 SKU 商品加入购物车。"""
    from app.agent.tools.add_cart_tool import add_to_cart_tool

    return add_to_cart_tool.invoke({
        "sku_id": sku_id,
        "qty": qty,
        "mall_token": mall_token,
    })


# ──────────────────────────────────────────────────────────────
# 购物车 - 更新
# ──────────────────────────────────────────────────────────────
@mcp.tool()
def update_cart_item(
    cart_item_id: str,
    qty: Optional[int] = None,
    checked: Optional[int] = None,
    mall_token: Optional[str] = None,
) -> dict:
    """修改购物车中商品的数量或勾选状态。"""
    from app.agent.tools.cart_tool import update_cart_item_tool

    return update_cart_item_tool.invoke({
        "cart_item_id": cart_item_id,
        "qty": qty,
        "checked": checked,
        "mall_token": mall_token,
    })


# ──────────────────────────────────────────────────────────────
# 购物车 - 删除单项
# ──────────────────────────────────────────────────────────────
@mcp.tool()
def delete_cart_item(
    cart_item_id: str,
    mall_token: Optional[str] = None,
) -> dict:
    """从购物车中移除指定商品。"""
    from app.agent.tools.cart_tool import delete_cart_item_tool

    return delete_cart_item_tool.invoke({
        "cart_item_id": cart_item_id,
        "mall_token": mall_token,
    })


# ──────────────────────────────────────────────────────────────
# 购物车 - 清空
# ──────────────────────────────────────────────────────────────
@mcp.tool()
def clear_cart(
    mall_token: Optional[str] = None,
) -> dict:
    """清空购物车中的所有商品。"""
    from app.agent.tools.cart_tool import clear_cart_tool

    return clear_cart_tool.invoke({
        "mall_token": mall_token,
    })


# ──────────────────────────────────────────────────────────────
# 售后
# ──────────────────────────────────────────────────────────────
@mcp.tool()
def apply_after_sales(
    order_id: str,
    after_sales_type: str,
    reason: str,
    description: Optional[str] = "",
    contact_phone: Optional[str] = "",
    user_id: Optional[int] = None,
) -> dict:
    """提交售后申请（退货/换货/退款）。

    after_sales_type 可选值：refund(退款) / return(退货) / exchange(换货) / other(其他)
    """
    from app.agent.tools.after_sales_tool import apply_after_sales_tool

    return apply_after_sales_tool.invoke({
        "order_id": order_id,
        "after_sales_type": after_sales_type,
        "reason": reason,
        "description": description,
        "contact_phone": contact_phone,
        "user_id": user_id,
    })


# ──────────────────────────────────────────────────────────────
# 收藏夹
# ──────────────────────────────────────────────────────────────
@mcp.tool()
def query_favorites(
    mall_token: Optional[str] = None,
) -> dict:
    """查看当前用户的收藏夹商品列表。"""
    from app.agent.tools.favorite_tool import query_favorite_tool

    return query_favorite_tool.invoke({
        "mall_token": mall_token,
    })


# ──────────────────────────────────────────────────────────────
# 钱包
# ──────────────────────────────────────────────────────────────
@mcp.tool()
def query_wallet(
    mall_token: Optional[str] = None,
) -> dict:
    """查询用户钱包余额、累计充值和消费信息。"""
    from app.agent.tools.wallet_tool import query_wallet_tool

    return query_wallet_tool.invoke({
        "mall_token": mall_token,
    })


# ──────────────────────────────────────────────────────────────
# 商品评价
# ──────────────────────────────────────────────────────────────
@mcp.tool()
def query_reviews(
    spu_id: str,
    page: int = 1,
    page_size: int = 10,
) -> dict:
    """查询指定商品的用户评价，包括评分、内容、回复等。"""
    from app.agent.tools.review_tool import query_reviews_tool

    return query_reviews_tool.invoke({
        "spu_id": spu_id,
        "page": page,
        "page_size": page_size,
    })


# ──────────────────────────────────────────────────────────────
# 知识库检索
# ──────────────────────────────────────────────────────────────
@mcp.tool()
def search_knowledge_base(
    query: str,
    top_k: int = 5,
) -> dict:
    """检索商品知识库，查询商品信息、售后政策、使用说明等静态知识。"""
    from app.agent.tools.kb_tool import kb_search_tool

    return kb_search_tool.invoke({
        "query": query,
        "top_k": top_k,
    })


# ──────────────────────────────────────────────────────────────
# 入口
# ──────────────────────────────────────────────────────────────
def main():
    """MCP Server 入口（stdio 传输）。"""
    logger.info(f"[MCP] 启动 MCP Server: {settings.MCP_SERVER_NAME}")
    mcp.run(transport="stdio")


if __name__ == "__main__":
    main()