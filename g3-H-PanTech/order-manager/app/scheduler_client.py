"""Cliente HTTP del Order Manager hacia el Queue Scheduler."""
import os

import httpx

SCHEDULER_URL = os.getenv("SCHEDULER_URL", "http://queue-scheduler:8001")


class SchedulerError(Exception):
    """Error al comunicarse con el Queue Scheduler."""


async def enqueue_order(order_id: int, order_type: str, table_number, items) -> dict:
    """Envía un pedido nuevo al Queue Scheduler.

    ``items`` es una lista de diccionarios con ``product_id`` y ``quantity``.
    Lanza ``SchedulerError`` si el servicio no responde o devuelve un error.
    """
    payload = {
        "order_id": order_id,
        "type": order_type,
        "table_number": table_number,
        "items": [
            {"product_id": item["product_id"], "quantity": item["quantity"]}
            for item in items
        ],
    }
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            response = await client.post(f"{SCHEDULER_URL}/queue/orders", json=payload)
            response.raise_for_status()
            return response.json()
    except httpx.HTTPError as exc:
        raise SchedulerError(f"No se pudo enviar el pedido a cocina: {exc}") from exc
