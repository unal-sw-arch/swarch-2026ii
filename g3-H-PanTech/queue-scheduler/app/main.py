"""Queue Scheduler: cola de cocina priorizada y estados de las comandas.

Expone la API que consume la Web App (HTTP Polling) y el Order Manager (REST).
El estado de la cola vive en Redis; los tiempos base se leen de PostgreSQL.
"""
import asyncio
import datetime as dt
import logging
import time
from contextlib import asynccontextmanager

import redis.asyncio as aioredis
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware

from . import config, db, store
from .priority import estimate_minutes, is_valid_transition, kitchen_sort_key
from .schemas import DispatchOut, HistoryRowOut, OrderHistoryOut, OrderIn, OrderOut, StatusUpdate

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("queue-scheduler")

state: dict = {}


async def _connect_redis(retries: int = 30, delay: float = 2.0):
    last_error = None
    for attempt in range(1, retries + 1):
        try:
            client = aioredis.from_url(config.REDIS_URL, decode_responses=True)
            await client.ping()
            logger.info("Conectado a Redis (intento %s)", attempt)
            return client
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            logger.warning("Redis no disponible (intento %s/%s): %s", attempt, retries, exc)
            await asyncio.sleep(delay)
    raise RuntimeError("No se pudo conectar a Redis") from last_error


@asynccontextmanager
async def lifespan(app: FastAPI):
    state["pg"] = await db.create_pool_with_retry()
    state["redis"] = await _connect_redis()
    await db.ensure_schema(state["pg"])
    yield
    await state["redis"].aclose()
    await state["pg"].close()


app = FastAPI(
    title="Panadería Express - Queue Scheduler",
    description="Cola de cocina priorizada por tiempo estimado de preparación.",
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


@app.get("/health")
async def health():
    """Verifica la conectividad con Redis y PostgreSQL."""
    try:
        await state["redis"].ping()
        await db.ping(state["pg"])
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=503, detail=f"Dependencia no disponible: {exc}")
    return {"status": "ok", "service": "queue-scheduler"}


@app.post("/queue/orders", response_model=OrderOut, status_code=201)
async def enqueue_order(payload: OrderIn):
    """Encola un pedido nuevo (lo llama el Order Manager)."""
    product_ids = list({item.product_id for item in payload.items})
    products = await db.fetch_products(state["pg"], product_ids)

    missing = [pid for pid in product_ids if pid not in products]
    if missing:
        raise HTTPException(
            status_code=400, detail=f"Productos inexistentes: {missing}"
        )

    items = [
        {
            "product_id": item.product_id,
            "name": products[item.product_id]["name"],
            "quantity": item.quantity,
            "prep_minutes": products[item.product_id]["prep_minutes"],
        }
        for item in payload.items
    ]

    # Tiempo estimado = suma de (prep_minutes x cantidad); vitrina cuenta mínimo 1 min.
    estimated = estimate_minutes(payload.items, products)

    order = {
        "order_id": payload.order_id,
        "type": payload.type,
        "table_number": payload.table_number,
        "status": "pendiente",
        "estimated_minutes": estimated,
        "total_items": sum(item.quantity for item in payload.items),
        "created_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "arrival_ts": time.time(),
        "items": items,
    }
    await store.save_order(state["redis"], order)
    await db.record_status_event(state["pg"], order["order_id"], "pendiente")
    logger.info("Pedido %s encolado (estimado %s min)", order["order_id"], estimated)
    return order


@app.get("/queue", response_model=list[OrderOut])
async def get_queue():
    """Cola de cocina: comandas pendiente y en_preparacion, priorizadas.

    Los estados ``en_preparacion`` van arriba; dentro del mismo grupo se ordena
    por tiempo estimado (menor primero) y, como desempate, por hora de llegada.
    """
    orders = await store.list_orders(state["redis"], statuses=("pendiente", "en_preparacion"))
    orders.sort(key=kitchen_sort_key)
    return orders


@app.patch("/queue/orders/{order_id}/status", response_model=OrderOut)
async def update_status(order_id: int, payload: StatusUpdate):
    """Cambia el estado de una comanda validando la transición."""
    order = await store.get_order(state["redis"], order_id)
    if order is None:
        raise HTTPException(status_code=404, detail="Comanda no encontrada")

    if not is_valid_transition(order["status"], payload.status):
        raise HTTPException(
            status_code=409,
            detail=(
                f"Transición inválida: {order['status']} -> {payload.status}"
            ),
        )

    await store.set_status(state["redis"], order_id, payload.status)
    await db.record_status_event(state["pg"], order_id, payload.status)
    order["status"] = payload.status
    return order


@app.get("/dispatch", response_model=DispatchOut)
async def get_dispatch():
    """Pedidos listos para entregar, separados por tipo."""
    orders = await store.list_orders(state["redis"], statuses=("listo",))
    orders.sort(key=lambda o: o["arrival_ts"])
    return {
        "para_llevar": [o for o in orders if o["type"] == "para_llevar"],
        "en_mesa": [o for o in orders if o["type"] == "en_mesa"],
    }


@app.post("/queue/orders/{order_id}/deliver", response_model=OrderOut)
async def deliver_order(order_id: int):
    """Confirma la entrega: el pedido sale de las pantallas y se conserva con TTL."""
    order = await store.get_order(state["redis"], order_id)
    if order is None:
        raise HTTPException(status_code=404, detail="Comanda no encontrada")

    if not is_valid_transition(order["status"], "entregado"):
        raise HTTPException(
            status_code=409,
            detail=f"Solo se pueden entregar comandas en estado 'listo' (actual: {order['status']})",
        )

    await store.deliver(state["redis"], order_id, config.DELIVERED_TTL_SECONDS)
    await db.record_status_event(state["pg"], order_id, "entregado")
    order["status"] = "entregado"
    return order


def _build_history(order_id: int, events: list) -> dict:
    """Arma la respuesta del historial de una comanda con sus marcas de tiempo."""
    started = events[0]["changed_at"] if events else None
    ready = next(
        (e["changed_at"] for e in reversed(events) if e["status"] == "listo"), None
    )
    delivered = next(
        (e["changed_at"] for e in reversed(events) if e["status"] == "entregado"),
        None,
    )
    end = delivered or dt.datetime.now(dt.timezone.utc)
    duration = int((end - started).total_seconds()) if started else None
    return {
        "order_id": order_id,
        "events": events,
        "started_at": started,
        "ready_at": ready,
        "delivered_at": delivered,
        "duration_seconds": duration,
    }


@app.get("/history", response_model=list[HistoryRowOut])
async def get_history(limit: int = 50):
    """Reporte de comandas con inicio, hora de listo, entrega y duración."""
    rows = await db.fetch_history(state["pg"], limit)
    now = dt.datetime.now(dt.timezone.utc)
    result = []
    for row in rows:
        started = row["started_at"]
        end = row["delivered_at"] or now
        result.append(
            {
                "order_id": row["order_id"],
                "status": row["last_status"],
                "started_at": started,
                "ready_at": row["ready_at"],
                "delivered_at": row["delivered_at"],
                "duration_seconds": int((end - started).total_seconds())
                if started
                else None,
            }
        )
    return result


@app.get("/history/orders/{order_id}", response_model=OrderHistoryOut)
async def get_order_history(order_id: int):
    """Historial de estados de una comanda en orden cronológico."""
    rows = await db.fetch_order_events(state["pg"], order_id)
    if not rows:
        raise HTTPException(status_code=404, detail="Sin historial para esa comanda")
    events = [{"status": r["status"], "changed_at": r["changed_at"]} for r in rows]
    return _build_history(order_id, events)
