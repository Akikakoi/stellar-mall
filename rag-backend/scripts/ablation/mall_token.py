"""消融实验共用工具：自签商城 C 端 JWT（与 JwtUtil.java 互通约定一致）。

约定（stellar-common JwtUtil 注释）：
  HS256；密钥为 UTF-8 原始字符串；claim: USER_ID、type=access；jti 唯一。
"""
import os
import sys
import uuid
from pathlib import Path

import jwt  # PyJWT

ROOT = Path(__file__).resolve().parent.parent.parent


def make_mall_user_token(user_id: int = 1, ttl_seconds: int = 7200) -> str:
    """按后端 JwtUtil 约定签发 C 端用户 token（header 名 authentication）。"""
    import datetime
    secret = os.environ.get("STELLAR_USER_SECRET_KEY")
    if not secret:
        # 从 rag-backend/.env 读取（脚本独立运行时不加载 pydantic settings）
        for line in (ROOT / ".env").read_text(encoding="utf-8").splitlines():
            if line.startswith("STELLAR_USER_SECRET_KEY="):
                secret = line.split("=", 1)[1].strip()
                break
    now = datetime.datetime.now(datetime.timezone.utc)
    payload = {
        "USER_ID": user_id,
        "type": "access",
        "jti": str(uuid.uuid4()),
        "exp": now + datetime.timedelta(seconds=ttl_seconds),
    }
    return jwt.encode(payload, secret, algorithm="HS256")


if __name__ == "__main__":
    token = make_mall_user_token()
    print(token)
