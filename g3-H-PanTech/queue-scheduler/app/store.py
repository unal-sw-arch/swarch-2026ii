"""Operaciones sobre Redis: cola priorizada (ZSET) y hash por comanda.

Estructura en Redis:
  - ``queue:orders``  -> ZSET, miembro = order_id, score = tiempo estimado (menor primero).
  - ``order:<id>``    -> HASH con el detalle de la comanda.
  - ``queue:seq``     -> contador para desempatar por hora de llegada.

Las comandas activas (pendiente, en_preparacion, listo) permanecen en el ZSET.
Al confirmar la entrega se eliminan del ZSET y el hash se conserva con TTL.
"""
import json
import time

ACTIVE_KEY = "queue:orders"
SEQ_KEY = "queue:seq"
ORDER_PREFIX = "order:"

ACTIVE_STATUSES = ("pendiente", "en_preparacion", "listo")


def order_key(order_id) -> str:
    return f"{ORDER_PREFIX}{order_id}"


def _serialize(order: dict) -> dict:
    return {
        "order_id": str(order["order_id"]),
        "type": order["type"],
        "table_number": "" if order.get("table_number") is None else str(order["table_number"]),
        "status": order["status"],
        "estimated_minutes": str(order["estimated_minutes"]),
        "total_items": str(order.get("total_items", 0)),
        "created_at": order.get("created_at") or "",
        "arrival_ts": str(order["arrival_ts"]),
        "items": json.dumps(order.get("items", []), ensure_ascii=False),
    }


def deserialize(data: dict) -> dict:
    table_number = data.get("table_number")
    return {
        "order_id": int(data["order_id"]),
        "type": data["type"],
        "table_number": int(table_number) if table_number not in (None, "", "None") else None,
        "status": data["status"],
        "estimated_minutes": int(data["estimated_minutes"]),
        "total_items": int(data.get("total_items", 0)),
        "created_at": data.get("created_at") or None,
        "arrival_ts": float(data["arrival_ts"]),
        "items": json.loads(data.get("items") or "[]"),
    }


async def save_order(redis, order: dict) -> None:
    """Guarda la comanda en su hash y la inserta en el ZSET con score = tiempo estimado."""
    seq = await redis.incr(SEQ_KEY)
    order = {**order, "arrival_seq": seq}
    # El desempate por hora de llegada se guarda en el hash (arrival_ts).
    await redis.hset(order_key(order["order_id"]), mapping=_serialize(order))
    await redis.zadd(ACTIVE_KEY, {str(order["order_id"]): float(order["estimated_minutes"])})


async def get_order(redis, order_id) -> dict | None:
    data = await redis.hgetall(order_key(order_id))
    if not data:
        return None
    return deserialize(data)


async def list_orders(redis, statuses=None) -> list:
    """Devuelve las comandas activas (opcionalmente filtradas por estado)."""
    ids = await redis.zrange(ACTIVE_KEY, 0, -1)
    orders = []
    for raw_id in ids:
        order = await get_order(redis, raw_id)
        if order is None:
            # Hash expirado pero todavía en el ZSET: limpiar la referencia.
            await redis.zrem(ACTIVE_KEY, raw_id)
            continue
        if statuses is None or order["status"] in statuses:
            orders.append(order)
    return orders


async def set_status(redis, order_id, status: str) -> None:
    await redis.hset(order_key(order_id), "status", status)
    await redis.hset(order_key(order_id), "updated_at", str(time.time()))


async def deliver(redis, order_id, ttl_seconds: int) -> None:
    """Marca la comanda como entregada: sale del ZSET y se conserva con TTL."""
    await set_status(redis, order_id, "entregado")
    await redis.zrem(ACTIVE_KEY, str(order_id))
    await redis.expire(order_key(order_id), ttl_seconds)
