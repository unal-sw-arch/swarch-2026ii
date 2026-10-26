"""Order Manager: catálogo, pedidos y reservas (REST para la Web App)."""
import logging
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text

from .database import Base, SessionLocal, engine
from .routers import orders, products, reservations
from .seed import seed_products

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("order-manager")


def wait_for_db(retries: int = 30, delay: float = 2.0) -> None:
    """Reintenta la conexión a PostgreSQL hasta que esté disponible."""
    last_error = None
    for attempt in range(1, retries + 1):
        try:
            with engine.connect() as conn:
                conn.execute(text("SELECT 1"))
            logger.info("Conectado a PostgreSQL (intento %s)", attempt)
            return
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            logger.warning("PostgreSQL no disponible (intento %s/%s): %s", attempt, retries, exc)
            time.sleep(delay)
    raise RuntimeError("No se pudo conectar a PostgreSQL") from last_error


@asynccontextmanager
async def lifespan(app: FastAPI):
    wait_for_db()
    Base.metadata.create_all(bind=engine)
    with SessionLocal() as db:
        inserted = seed_products(db)
        if inserted:
            logger.info("Datos semilla insertados: %s productos", inserted)
    yield


app = FastAPI(
    title="Panadería Express - Order Manager",
    description="Gestión de productos, pedidos y reservas de la panadería.",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(products.router)
app.include_router(orders.router)
app.include_router(reservations.router)


@app.get("/health", tags=["salud"])
def health():
    try:
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=503, detail=f"Base de datos no disponible: {exc}")
    return {"status": "ok", "service": "order-manager"}
