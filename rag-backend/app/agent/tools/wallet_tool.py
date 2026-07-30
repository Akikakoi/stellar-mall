from langchain_core.tools import tool
from typing import Optional

from app.agent.tools.mall_client import call_mall


@tool
def query_wallet_tool(
    mall_token: Optional[str] = None,
    user_id: Optional[int] = None,
) -> dict:
    """钱包查询工具，用于查询用户的钱包余额、累计充值和消费等信息。

    Args:
        mall_token: 商城用户token，用于身份验证
        user_id: 用户ID
    """
    result = call_mall("GET", "/user/wallet", mall_token=mall_token)
    if not result["ok"]:
        return {"success": False, "message": result["message"]}

    data = result["data"] or {}
    if data.get("code") in (1, 200):
        wallet = data.get("data", {})
        return {
            "success": True,
            "balance": str(wallet.get("balance", "0")),
            "frozen": str(wallet.get("frozen", "0")),
            "total_recharge": str(wallet.get("totalRecharge", "0")),
            "total_consume": str(wallet.get("totalConsume", "0")),
            "create_time": wallet.get("createTime", ""),
            "message": f"当前余额 ¥{wallet.get('balance', '0')}",
        }
    else:
        return {
            "success": False,
            "message": data.get("msg", "钱包查询失败"),
        }
