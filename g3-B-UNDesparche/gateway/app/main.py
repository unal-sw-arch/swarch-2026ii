import asyncio
import logging
from contextlib import asynccontextmanager, suppress

import httpx
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from .cache import clean_expired_users
from .clients import close_http_client
from .config import CORS_ORIGINS, validate_config
from .firebase import init_firebase
from .routers import events, inventory, users

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    validate_config()
    init_firebase()

    # Tarea de limpieza periódica del caché TTL (cada 60 segundos)
    async def cleanup_task():
        while True:
            await asyncio.sleep(60)
            clean_expired_users()

    task = asyncio.create_task(cleanup_task())
    yield
    task.cancel()
    with suppress(asyncio.CancelledError):
        await task
    await close_http_client()


app = FastAPI(
    title="UNDesparche API Gateway",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# --- Errores al hablar con los microservicios (RNF-7: registrar y responder con control) ---
# Starlette elige el manejador más específico, así que Timeout gana sobre TransportError.

@app.exception_handler(httpx.TimeoutException)
async def upstream_timeout_handler(request: Request, exc: httpx.TimeoutException):
    logger.error("Timeout al llamar a un microservicio desde %s", request.url.path)
    return JSONResponse(
        status_code=504, content={"detail": "El servicio no respondió a tiempo."}
    )


@app.exception_handler(httpx.TransportError)
async def upstream_unreachable_handler(request: Request, exc: httpx.TransportError):
    logger.error("No se pudo conectar a un microservicio desde %s: %r", request.url.path, exc)
    return JSONResponse(
        status_code=503, content={"detail": "El servicio no está disponible."}
    )


@app.exception_handler(httpx.HTTPStatusError)
async def upstream_error_handler(request: Request, exc: httpx.HTTPStatusError):
    # Solo llega aquí desde las llamadas internas del gateway (consultar/crear usuario),
    # porque el proxy normal devuelve la respuesta del servicio tal cual.
    logger.error(
        "Users respondió %s a una llamada interna desde %s",
        exc.response.status_code,
        request.url.path,
    )
    return JSONResponse(
        status_code=502, content={"detail": "Error al comunicarse con el servicio de usuarios."}
    )


app.include_router(users.router, prefix="/users")
app.include_router(events.router, prefix="/events")
app.include_router(inventory.router, prefix="/inventory")


@app.get("/")
async def home():
    return "Bienvenido al api gateway de UNDesparche"
