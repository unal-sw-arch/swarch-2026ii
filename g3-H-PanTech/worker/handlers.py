"""Lógica de los consumidores. Sin Celery ni clientes concretos: las dependencias se inyectan (`Deps`),
así se prueba sin infraestructura.

Idempotencia: cada handler registra `event_id` en `processed_events` al terminar. Un evento repetido
se ignora; si el handler falló a medias se repite entero, por eso cada paso es idempotente
(Kitchen lo es por `order_id`, los read models son upserts, el comprobante se genera una vez por pedido
y la notificación es única por evento y tipo).
"""
import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Callable, Protocol

from errors import InvalidEvent
from receipt_pdf import build_receipt_pdf

logger = logging.getLogger("worker.handlers")


class KitchenPort(Protocol):
    def enqueue(self, order: dict, request_id: str | None = None) -> None: ...


class OrderPort(Protocol):
    def set_status(self, order_id: str, status: str) -> bool: ...


class StorePort(Protocol):
    def is_done(self, event_id: str, handler: str) -> bool: ...
    def mark_done(self, event_id: str, handler: str) -> None: ...
    def upsert_order_paid(self, order: dict, paid_at: datetime) -> None: ...
    def mark_order_ready(self, order_id: str, ready_at: datetime) -> None: ...
    def set_receipt(self, order_id: str, object_name: str) -> None: ...
    def upsert_reservation(self, event: dict, *, status: str, reason: str | None = None) -> None: ...
    def add_notification(self, event_id: str, kind: str, customer_id: str | None, message: str) -> bool: ...


class ReceiptsPort(Protocol):
    def exists(self, object_name: str) -> bool: ...
    def put(self, object_name: str, data: bytes, content_type: str = "application/pdf") -> None: ...


@dataclass
class Deps:
    kitchen: KitchenPort
    orders: OrderPort
    store: StorePort
    receipts: ReceiptsPort
    dispatch_receipt: Callable[[dict], None]  # encola la tarea `generate_receipt`


def normalize_event(event: object) -> object:
    """Acepta el sobre canónico de eventos y el formato plano legado.

    Contrato canónico (Kitchen Service):
        {"event_id": ..., "event_type": ..., "version": 1, "occurred_at": ...,
         "producer": ..., "payload": {...}}

    Order Service publicó históricamente el payload plano con `event_id` y
    `occurred_at` en el nivel superior. El Worker acepta ambos para no romper
    los eventos que ya están en el outbox ni los mensajes en vuelo.
    """
    if not isinstance(event, dict):
        return event
    payload = event.get("payload")
    if not isinstance(payload, dict):
        return event
    merged = dict(payload)
    merged.update({key: value for key, value in event.items() if key != "payload" and value is not None})
    return merged


def require(event: object, *fields: str) -> dict:
    event = normalize_event(event)
    if not isinstance(event, dict):
        raise InvalidEvent("El evento no es un objeto JSON")
    missing = [f for f in fields if event.get(f) in (None, "")]
    if missing:
        raise InvalidEvent(f"Faltan campos obligatorios: {', '.join(missing)}")
    return event


def parse_dt(value: str, field: str) -> datetime:
    try:
        parsed = datetime.fromisoformat(str(value).replace("Z", "+00:00"))
    except ValueError as exc:
        raise InvalidEvent(f"{field} no es una fecha ISO válida: {value!r}") from exc
    return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)


# ---------------------------------------------------------------------------------------------
def handle_order_paid(event: dict, deps: Deps) -> str:
    """Encola el pedido en Kitchen (solo tras el pago), actualiza el read model, notifica y pide el comprobante."""
    event = require(event, "event_id", "order_id", "total", "paid_at", "order_type", "items")
    if not event["items"]:
        raise InvalidEvent("El pedido pagado no tiene ítems")
    handler = "order.paid"
    if deps.store.is_done(event["event_id"], handler):
        logger.info("order.paid %s ya procesado; se ignora", event["event_id"])
        return "duplicate"

    paid_at = parse_dt(event["paid_at"], "paid_at")
    deps.kitchen.enqueue(
        {
            "order_id": event["order_id"],
            "order_type": event["order_type"],
            "table_number": event.get("table_number"),
            "customer_id": event.get("customer_id"),
            "paid_at": event["paid_at"],
            "items": [
                {"product_id": i["product_id"], "name": i["name"], "quantity": i["quantity"],
                 "prep_minutes": i["prep_minutes"]}
                for i in event["items"]
            ],
        },
        request_id=event["event_id"],
    )
    deps.store.upsert_order_paid(event, paid_at)
    deps.store.add_notification(
        event["event_id"], "order_paid", event.get("customer_id"),
        f"Pago confirmado del pedido {event['order_id']} por {event['total']}. Tu pedido pasó a cocina.",
    )
    deps.dispatch_receipt(event)
    deps.store.mark_done(event["event_id"], handler)
    return "processed"


def handle_order_ready(event: dict, deps: Deps) -> str:
    """Marca el pedido como listo en Order Service y en el read model."""
    event = require(event, "event_id", "order_id", "ready_at")
    handler = "order.ready"
    if deps.store.is_done(event["event_id"], handler):
        return "duplicate"
    ready_at = parse_dt(event["ready_at"], "ready_at")
    deps.orders.set_status(event["order_id"], "listo")
    deps.store.mark_order_ready(event["order_id"], ready_at)
    deps.store.add_notification(
        event["event_id"], "order_ready", event.get("customer_id"),
        f"Tu pedido {event['order_id']} está listo para entregar.",
    )
    deps.store.mark_done(event["event_id"], handler)
    return "processed"


_RESERVATION = {
    "reservation.created": ("pendiente", "reservation_created", "Reserva registrada para el {date} a las {time}."),
    "reservation.confirmed": ("confirmada", "reservation_confirmed", "Tu reserva del {date} a las {time} fue confirmada."),
    "reservation.cancelled": ("cancelada", "reservation_cancelled", "Tu reserva fue cancelada. Motivo: {reason}."),
}


def handle_reservation_event(event_name: str, event: dict, deps: Deps) -> str:
    event = require(event, "event_id", "reservation_id", "customer_id")
    status, kind, template = _RESERVATION[event_name]
    if deps.store.is_done(event["event_id"], event_name):
        return "duplicate"
    reason = event.get("reason") if status == "cancelada" else None
    deps.store.upsert_reservation(event, status=status, reason=reason)
    message = template.format(
        date=event.get("date", "?"), time=event.get("time", "?"), reason=reason or "sin motivo"
    )
    deps.store.add_notification(event["event_id"], kind, event["customer_id"], message)
    deps.store.mark_done(event["event_id"], event_name)
    return "processed"


def generate_receipt(event: dict, deps: Deps) -> str:
    """Una vez por pedido pagado: si el objeto ya existe en MinIO no se vuelve a generar."""
    event = require(event, "order_id", "total", "paid_at", "items")
    object_name = f"{event['order_id']}.pdf"
    if deps.receipts.exists(object_name):
        return "exists"
    deps.receipts.put(object_name, build_receipt_pdf(event))
    deps.store.set_receipt(event["order_id"], object_name)
    return "created"
