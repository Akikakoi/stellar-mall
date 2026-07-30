"""Agent 工具调用 Mall Java 后端的统一 HTTP 客户端。

提供：
- 统一认证头构建
- 可配置超时
- 简单重试（ConnectError / TimeoutException / 5xx）
- 熔断保护：连续失败达到阈值后，在恢复窗口内快速失败
"""
from __future__ import annotations

import threading
import time
from typing import Optional

import httpx

from app.config import settings
from app.core.logger import logger


class _CircuitBreaker:
    """简易熔断器：CLOSED -> OPEN -> HALF_OPEN -> CLOSED。"""

    CLOSED = "closed"
    OPEN = "open"
    HALF_OPEN = "half_open"

    def __init__(
        self,
        failure_threshold: int,
        recovery_seconds: float,
    ):
        self._failure_threshold = max(1, failure_threshold)
        self._recovery_seconds = recovery_seconds
        self._lock = threading.Lock()
        self._state = self.CLOSED
        self._failure_count = 0
        self._last_failure_time: Optional[float] = None

    def before_request(self) -> Optional[str]:
        """请求前检查，返回 None 表示允许请求，否则返回熔断提示。"""
        with self._lock:
            if self._state == self.OPEN:
                if self._last_failure_time is None:
                    self._state = self.HALF_OPEN
                    return None
                elapsed = time.time() - self._last_failure_time
                if elapsed >= self._recovery_seconds:
                    self._state = self.HALF_OPEN
                    return None
                return (
                    f"商城服务熔断中，已持续 {elapsed:.0f}s，"
                    f"预计 {self._recovery_seconds - elapsed:.0f}s 后恢复。请稍后重试。"
                )
            return None

    def on_success(self) -> None:
        with self._lock:
            self._failure_count = 0
            self._last_failure_time = None
            self._state = self.CLOSED

    def on_failure(self) -> None:
        with self._lock:
            self._failure_count += 1
            self._last_failure_time = time.time()
            if self._state == self.HALF_OPEN:
                self._state = self.OPEN
            elif self._failure_count >= self._failure_threshold:
                self._state = self.OPEN
                logger.warning(
                    f"[mall_client] 熔断器打开，连续失败 {self._failure_count} 次"
                )


_circuit_breaker = _CircuitBreaker(
    failure_threshold=settings.MALL_API_CIRCUIT_BREAKER_FAILURE_THRESHOLD,
    recovery_seconds=settings.MALL_API_CIRCUIT_BREAKER_RECOVERY_SECONDS,
)


def build_mall_headers(mall_token: Optional[str] = None) -> dict:
    """构建调用 Mall Java 后端需要的认证头。"""
    headers = {"Content-Type": "application/json"}
    if mall_token:
        headers["authentication"] = mall_token
        headers["Authorization"] = f"Bearer {mall_token}"
        headers["stellar-token"] = mall_token
    return headers


def _is_retryable(exc: Exception, status_code: Optional[int]) -> bool:
    """判断异常/状态码是否值得重试。"""
    if status_code is not None and 500 <= status_code < 600:
        return True
    if isinstance(exc, httpx.TimeoutException):
        return True
    if isinstance(exc, (httpx.ConnectError, httpx.NetworkError)):
        return True
    return False


def call_mall(
    method: str,
    path: str,
    mall_token: Optional[str] = None,
    params: Optional[dict] = None,
    json: Optional[dict] = None,
    timeout: Optional[float] = None,
) -> dict:
    """统一调用 Mall Java 后端。

    Args:
        method: HTTP 方法，如 GET/POST/PUT/DELETE
        path: API 路径，如 /user/cart
        mall_token: 商城用户 token
        params: URL 查询参数
        json: JSON 请求体
        timeout: 单次请求超时（秒），默认读取 settings

    Returns:
        {
            "ok": True/False,
            "status_code": int or None,
            "data": dict or None,        # 后端返回的完整 JSON
            "message": str,              # 失败原因或后端 msg
        }
    """
    base_url = settings.MALL_API_BASE_URL.rstrip("/")
    url = f"{base_url}{path}"
    headers = build_mall_headers(mall_token)
    timeout = timeout if timeout is not None else settings.MALL_API_TIMEOUT_SECONDS
    max_retries = max(0, settings.MALL_API_MAX_RETRIES)

    cb_msg = _circuit_breaker.before_request()
    if cb_msg:
        logger.warning(f"[mall_client] {method} {path} 熔断拦截")
        return {"ok": False, "status_code": None, "data": None, "message": cb_msg}

    last_exc: Optional[Exception] = None
    last_status: Optional[int] = None
    retries = 0

    while retries <= max_retries:
        try:
            with httpx.Client(timeout=timeout, headers=headers) as client:
                resp = client.request(method, url, params=params, json=json)
                last_status = resp.status_code
                resp.raise_for_status()
                data = resp.json()
                _circuit_breaker.on_success()
                return {"ok": True, "status_code": resp.status_code, "data": data, "message": ""}
        except httpx.HTTPStatusError as e:
            last_exc = e
            last_status = e.response.status_code
            body = _safe_read_text(e.response)
            logger.warning(
                f"[mall_client] {method} {path} HTTP {last_status}: {body[:200]}"
            )
            if not _is_retryable(e, last_status):
                break
        except (httpx.TimeoutException, httpx.ConnectError, httpx.NetworkError) as e:
            last_exc = e
            logger.warning(f"[mall_client] {method} {path} 网络异常（重试 {retries}/{max_retries}）: {e}")
        except Exception as e:
            last_exc = e
            logger.warning(f"[mall_client] {method} {path} 请求异常: {e}")
            break

        retries += 1
        if retries <= max_retries:
            time.sleep(0.2 * retries)

    _circuit_breaker.on_failure()
    err_msg = _format_error(last_exc, last_status)
    logger.error(f"[mall_client] {method} {path} 最终失败: {err_msg}")
    return {"ok": False, "status_code": last_status, "data": None, "message": err_msg}


def _safe_read_text(response: httpx.Response) -> str:
    try:
        return response.text
    except Exception:
        return ""


def _format_error(exc: Optional[Exception], status_code: Optional[int]) -> str:
    if status_code is not None and 500 <= status_code < 600:
        return f"商城服务暂时不可用（HTTP {status_code}），请稍后重试。"
    if isinstance(exc, httpx.TimeoutException):
        return "商城服务响应超时，请稍后重试。"
    if isinstance(exc, (httpx.ConnectError, httpx.NetworkError)):
        return "网络异常，无法连接到商城系统。"
    if exc:
        return f"网络异常，无法连接到商城系统：{exc}"
    return "网络异常，无法连接到商城系统。"
