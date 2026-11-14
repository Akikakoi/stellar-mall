"""MCP (Model Context Protocol) Server — 把商城**只读**查询能力暴露给 IDE。

定位：开发者/运营在 TRAE、Claude Desktop 里用自然语言查商城数据
（商品、订单、购物车、收藏、钱包、评价、知识库）。

刻意不开放任何写操作：取消订单、确认收货、加购、清空购物车、提交售后
这类有副作用的调用，统一走 C 端 Agent 的 HTTP 接口，由 graph 侧
`react_safety` 的服务端确认机制（用户确认词 / user_confirmed）把关。
MCP 侧的调用方是 IDE 里的模型，"confirm=true" 由模型自己填，
不构成真实的人类确认，因此这里干脆不提供写工具。

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

import sys
from typing import Optional

# stdio 传输下 stdout 只允许承载 JSON-RPC，任何日志混进去都会让客户端解析失败。
# app/core/logger.py 默认把日志打到 stdout（为 FastAPI 场景设计），这里在导入期就切到 stderr。
from loguru import logger as _loguru

_loguru.remove()
_loguru.add(sys.stderr, level="INFO")

from mcp.server.fastmcp import FastMCP  # noqa: E402

from app.config import settings  # noqa: E402
from app.core.logger import logger  # noqa: E402

mcp = FastMCP(settings.MCP_SERVER_NAME or "stellar-mall")


# ──────────────────────────────────────────────────────────────
# 商品
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


@mcp.tool()
def resolve_sku(
    product_name: str,
    spec_description: Optional[str] = None,
    mall_token: Optional[str] = None,
) -> dict:
    """根据商品名称和规格描述解析出 SKU ID，例如「星耀 X100 Pro Max」+「16+512」。"""
    from app.agent.tools.resolve_sku_tool import resolve_sku_tool

    return resolve_sku_tool.invoke({
        "product_name": product_name,
        "spec_description": spec_description,
        "mall_token": mall_token,
    })


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
# 订单 / 购物车 / 收藏 / 钱包（均为只读查询）
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


@mcp.tool()
def view_cart(
    mall_token: Optional[str] = None,
) -> dict:
    """查看当前用户的购物车商品列表。"""
    from app.agent.tools.cart_tool import query_cart_tool

    return query_cart_tool.invoke({
        "mall_token": mall_token,
    })


@mcp.tool()
def query_favorites(
    mall_token: Optional[str] = None,
) -> dict:
    """查看当前用户的收藏夹商品列表。"""
    from app.agent.tools.favorite_tool import query_favorite_tool

    return query_favorite_tool.invoke({
        "mall_token": mall_token,
    })


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
# 知识库
# ──────────────────────────────────────────────────────────────
@mcp.tool()
def search_knowledge_base(
    query: str,
    top_k: int = 5,
) -> dict:
    """检索商品知识库，查询商品信息、售后政策、使用说明等静态知识。"""
    from app.agent.tools.kb_tool import _kb_search_impl

    # 跳过本地 BGE 精排：MCP 是 stdio 短进程，reranker 冷加载在 CPU 上约 5 分钟，
    # 而 ES 的 RRF 已融合语义 + 关键词两路，IDE 查询场景够用
    return _kb_search_impl(query, top_k=top_k, use_rerank=False)


# ──────────────────────────────────────────────────────────────
# 入口
# ──────────────────────────────────────────────────────────────
def main():
    """MCP Server 入口（stdio 传输）。"""
    if not settings.MCP_ENABLED:
        logger.warning("[MCP] MCP_ENABLED=false，跳过启动")
        return
    # 预热 C 扩展：numpy 等首次导入若发生在 anyio 事件循环运行期间，Windows 上会
    # 卡死在 DLL 加载（实测 >80s 无返回，faulthandler 栈停在 numpy._core.multiarray）。
    # 必须在 mcp.run() 建立事件循环之前预导入。
    import numpy  # noqa: F401
    # config 初始化时 setup_logger 会重新挂上 stdout sink，启动前再清一次
    _loguru.remove()
    _loguru.add(sys.stderr, level="INFO")
    logger.info(f"[MCP] 启动 MCP Server: {settings.MCP_SERVER_NAME}（只读模式）")
    mcp.run(transport="stdio")


if __name__ == "__main__":
    main()
