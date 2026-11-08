import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from sqlalchemy import text

from app import errors, seed
from app.config import settings
from app.database import SessionLocal, engine, get_mongo_db
from app.events.outbox import OutboxRelay
from app.events.publisher import RabbitPublisher
from app.routers import internal, menu, orders, payments, products, reservations

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger("order-service")


@asynccontextmanager
async def lifespan(app: FastAPI):
    if settings.seed_menu:
        seed.bootstrap(get_mongo_db(), seed=True)
    relay = None
    if settings.outbox_relay_enabled:
        relay = OutboxRelay(
            SessionLocal,
            RabbitPublisher(),
            interval=settings.outbox_poll_seconds,
            batch_size=settings.outbox_batch_size,
        )
        relay.start()
        app.state.outbox_relay = relay
        logger.info("Relay del outbox iniciado")
    yield
    if relay is not None:
        relay.stop()


app = FastAPI(
    title="Panadería Express — Order Service",
    version="0.2.0",
    description=(
        "Menú y catálogo, pedidos, pagos simulados e idempotentes y reservas. "
        "Publica `order.paid`, `reservation.created`, `reservation.confirmed` y `reservation.cancelled` "
        "en RabbitMQ mediante outbox. Todo el tráfico de usuarios llega por el API Gateway."
    ),
    lifespan=lifespan,
)
errors.install(app)

app.include_router(menu.router)
app.include_router(products.router)
app.include_router(orders.router)
app.include_router(payments.router)
app.include_router(reservations.router)
app.include_router(internal.router)


@app.get("/health", tags=["health"])
def health():
    try:
        with engine.connect() as conn:
            conn.execute(text("SELECT 1"))
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=503, detail=f"Base de datos no disponible: {exc}")
    return {"status": "ok", "service": "order-service"}
