"""简易内存滑动窗口限流器（替代 Redis，单机可用）。"""
from __future__ import annotations
import time
import threading
from collections import defaultdict, deque
from functools import wraps
from typing import Callable, Any

from fastapi import Request

from app.core.exceptions import BizError


class MemoryRateLimiter:
    def __init__(self):
        self._buckets: dict[str, deque] = defaultdict(deque)
        self._lock = threading.Lock()

    def is_allowed(self, key: str, max_calls: int, window_sec: int = 60) -> bool:
        now = time.time()
        with self._lock:
            q = self._buckets[key]
            # 清理窗口外的旧记录
            while q and q[0] <= now - window_sec:
                q.popleft()
            if len(q) >= max_calls:
                return False
            q.append(now)
            return True


limiter = MemoryRateLimiter()


def rate_limit(max_calls: int, window_sec: int = 60, key_builder: Callable[[Request, Any], str] | None = None):
    """装饰器：对接口限流。默认 key = client_ip + endpoint。"""

    def decorator(func):
        @wraps(func)
        async def wrapper(*args, **kwargs):
            request: Request | None = kwargs.get("request")
            # 也可能在 args 里找 Request
            if request is None:
                for a in args:
                    if isinstance(a, Request):
                        request = a
                        break
            if request is None:
                return await func(*args, **kwargs)
            client_host = request.client.host if request.client else "unknown"
            key = f"{client_host}:{request.url.path}"
            if key_builder:
                key = key_builder(request, kwargs)
            if not limiter.is_allowed(key, max_calls, window_sec):
                raise BizError(f"请求过于频繁，请稍后重试（{max_calls}次/{window_sec}秒）",
                               code=42900, http_status=429)
            return await func(*args, **kwargs)

        return wrapper

    return decorator
