"""健全性实验共用工具：自签 JWT、HTTP 探针、docker 容器控制。"""
import json
import socket
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path

import jwt as pyjwt
import pymysql

MALL_BASE = "http://127.0.0.1:8082"
ENV_FILE = Path(__file__).resolve().parent.parent.parent / ".env"


def _env_secret(name: str) -> str:
    for line in ENV_FILE.read_text(encoding="utf-8").splitlines():
        if line.startswith(f"{name}="):
            return line.split("=", 1)[1].strip()
    raise KeyError(name)


def make_token(user_id: int, ttl_seconds: int = 3600, secret: str | None = None,
               expired: bool = False) -> str:
    """按后端 JwtUtil 约定签发 C 端 token。expired=True 签发已过期 token。"""
    import datetime
    secret = secret or _env_secret("STELLAR_USER_SECRET_KEY")
    now = datetime.datetime.now(datetime.timezone.utc)
    exp = now - datetime.timedelta(hours=1) if expired else now + datetime.timedelta(seconds=ttl_seconds)
    payload = {"USER_ID": user_id, "type": "access", "jti": f"health-{time.time()}",
               "exp": exp}
    return pyjwt.encode(payload, secret, algorithm="HS256")


def http(method: str, path: str, token: str | None = None, body: dict | None = None,
         base: str = MALL_BASE, timeout: float = 15.0):
    """返回 (status_code, body_text)。连接失败时 status 为 0。"""
    url = base + path
    headers = {"Content-Type": "application/json"}
    if token:
        headers["authentication"] = token
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception:
        return 0, "<connection failed>"


def docker(action: str, container: str) -> bool:
    r = subprocess.run(["docker", action, container], capture_output=True, text=True)
    return r.returncode == 0


def wait_port(port: int, up: bool = True, max_seconds: int = 120) -> bool:
    deadline = time.time() + max_seconds
    while time.time() < deadline:
        s = socket.socket()
        s.settimeout(1)
        try:
            s.connect(("127.0.0.1", port))
            s.close()
            if up:
                return True
        except Exception:
            s.close()
            if not up:
                return True
        time.sleep(2)
    return False


def db_query(sql: str):
    conn = pymysql.connect(host="127.0.0.1", user="stellar", password="123456",
                           database="stellar_mall", port=3306)
    try:
        cur = conn.cursor()
        cur.execute(sql)
        return cur.fetchall()
    finally:
        conn.close()


def save_result(name: str, data):
    out = Path(__file__).resolve().parent / name
    out.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"结果已写入 {out}")
