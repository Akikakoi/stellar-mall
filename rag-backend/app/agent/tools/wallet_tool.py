from langchain_core.tools import tool
from typing import Optional

from app.agent.tools.mall_client import call_mall
from app.agent.tools._mall_response import unwrap_mall


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
    data, err = unwrap_mall(result, "钱包查询失败")
    if err:
        return {"success": False, "message": err}

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
