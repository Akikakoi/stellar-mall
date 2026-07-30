"""统一异常定义与全局错误处理。"""
from typing import Any
from fastapi import Request, status
from fastapi.responses import JSONResponse
from fastapi.exceptions import RequestValidationError
from starlette.exceptions import HTTPException as StarletteHTTPException
from app.core.logger import logger


class BizError(Exception):
    """业务逻辑异常，HTTP 状态码默认 400，由全局处理成统一响应。"""

    def __init__(self, message: str, code: int = 40000, http_status: int = 400, data: Any = None):
        self.message = message
        self.code = code
        self.http_status = http_status
        self.data = data
        super().__init__(message)


def ok(data: Any = None, message: str = "success", code: int = 0) -> dict:
    return {"code": code, "message": message, "data": data}


def register_exception_handlers(app) -> None:
    @app.exception_handler(BizError)
    async def biz_error_handler(_: Request, exc: BizError):
        logger.warning(f"BizError code={exc.code} msg={exc.message}")
        return JSONResponse(
            status_code=exc.http_status,
            content={"code": exc.code, "message": exc.message, "data": exc.data},
        )

    @app.exception_handler(StarletteHTTPException)
    async def http_handler(_: Request, exc: StarletteHTTPException):
        logger.warning(f"HTTPException {exc.status_code} {exc.detail}")
        return JSONResponse(
            status_code=exc.status_code,
            content={"code": exc.status_code * 100, "message": str(exc.detail), "data": None},
        )

    @app.exception_handler(RequestValidationError)
    async def validation_handler(_: Request, exc: RequestValidationError):
        logger.warning(f"ValidationError {exc.errors()}")
        msg_list = []
        for err in exc.errors():
            loc = ".".join(str(x) for x in err.get("loc", []))
            msg_list.append(f"{loc}: {err.get('msg')}")
        return JSONResponse(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            content={"code": 42200, "message": "; ".join(msg_list) or "参数校验失败", "data": None},
        )

    @app.exception_handler(Exception)
    async def global_handler(_: Request, exc: Exception):
        logger.exception(f"Unhandled exception: {exc}")
        return JSONResponse(
            status_code=500,
            content={"code": 50000, "message": "服务器内部错误，请联系管理员", "data": None},
        )
