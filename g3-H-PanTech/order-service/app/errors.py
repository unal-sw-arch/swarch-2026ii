"""Errores de dominio y formato uniforme de error: {code, message, request_id, details?}."""
import logging
import uuid

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

logger = logging.getLogger("order-service")

_CODES = {
    400: "bad_request",
    401: "unauthorized",
    403: "forbidden",
    404: "not_found",
    409: "conflict",
    422: "validation_error",
    429: "too_many_requests",
    503: "service_unavailable",
}


class DomainError(Exception):
    status_code = 400

    def __init__(self, message: str, details: dict | None = None):
        super().__init__(message)
        self.message = message
        self.details = details


class NotFound(DomainError):
    status_code = 404


class Forbidden(DomainError):
    status_code = 403


class Conflict(DomainError):
    status_code = 409


class Unprocessable(DomainError):
    status_code = 422


def _body(request: Request, status: int, message: str, details=None) -> dict:
    body = {
        "code": _CODES.get(status, f"http_{status}"),
        "message": message,
        "request_id": getattr(request.state, "request_id", None),
    }
    if details:
        body["details"] = details
    return body


def install(app: FastAPI) -> None:
    @app.middleware("http")
    async def request_id_middleware(request: Request, call_next):
        request.state.request_id = request.headers.get("X-Request-Id") or uuid.uuid4().hex
        response = await call_next(request)
        response.headers["X-Request-Id"] = request.state.request_id
        return response

    @app.exception_handler(DomainError)
    async def domain_handler(request: Request, exc: DomainError):
        return JSONResponse(
            _body(request, exc.status_code, exc.message, exc.details), status_code=exc.status_code
        )

    @app.exception_handler(StarletteHTTPException)
    async def http_handler(request: Request, exc: StarletteHTTPException):
        return JSONResponse(
            _body(request, exc.status_code, str(exc.detail)),
            status_code=exc.status_code,
            headers=getattr(exc, "headers", None),
        )

    @app.exception_handler(RequestValidationError)
    async def validation_handler(request: Request, exc: RequestValidationError):
        details = [
            {"loc": list(e.get("loc", [])), "msg": e.get("msg"), "type": e.get("type")}
            for e in exc.errors()
        ]
        return JSONResponse(
            _body(request, 422, "Solicitud inválida", {"errors": details}), status_code=422
        )

    @app.exception_handler(Exception)
    async def unhandled_handler(request: Request, exc: Exception):
        logger.exception("Error no controlado request_id=%s", getattr(request.state, "request_id", None))
        return JSONResponse(_body(request, 500, "Error interno del servidor"), status_code=500)
