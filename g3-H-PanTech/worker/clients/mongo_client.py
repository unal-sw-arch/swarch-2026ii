"""Read models, idempotencia y notificaciones en MongoDB.

Misma base que usa el Order Service. Colecciones: `order_history`, `reservation_read` (read models), `processed_events` (idempotencia por
event_id + handler) y `notifications` (registro de notificaciones simuladas).
"""
import logging
from datetime import datetime, timezone
from decimal import Decimal

from bson.decimal128 import Decimal128
from pymongo import MongoClient
from pymongo.errors import DuplicateKeyError

import config

logger = logging.getLogger("worker.mongo")


def _now() -> datetime:
    return datetime.now(timezone.utc)


class MongoStore:
    def __init__(self, client: MongoClient | None = None, db_name: str | None = None):
        client = client or MongoClient(config.MONGODB_URL, tz_aware=True, serverSelectionTimeoutMS=5000)
        self._db = client[db_name] if db_name else client.get_default_database(default=config.MONGO_DB_DEFAULT)
        self._indexes_ready = False

    def ensure_indexes(self) -> None:
        if self._indexes_ready:
            return
        self._db["processed_events"].create_index([("event_id", 1), ("handler", 1)], unique=True)
        self._db["notifications"].create_index([("event_id", 1), ("kind", 1)], unique=True)
        self._db["order_history"].create_index("order_id", unique=True)
        self._db["order_history"].create_index([("customer_id", 1), ("paid_at", -1)])
        self._db["reservation_read"].create_index("reservation_id", unique=True)
        self._db["reservation_read"].create_index([("customer_id", 1), ("date", -1)])
        self._indexes_ready = True

    # -- idempotencia ---------------------------------------------------------------------------
    def is_done(self, event_id: str, handler: str) -> bool:
        return self._db["processed_events"].find_one({"event_id": event_id, "handler": handler}) is not None

    def mark_done(self, event_id: str, handler: str) -> None:
        try:
            self._db["processed_events"].insert_one(
                {"event_id": event_id, "handler": handler, "processed_at": _now()}
            )
        except DuplicateKeyError:
            pass

    # -- read model de pedidos -------------------------------------------------------------------
    def upsert_order_paid(self, order: dict, paid_at: datetime) -> None:
        """El estado solo se fija al insertar: un `order.paid` tardío no retrocede un `listo`."""
        self._db["order_history"].update_one(
            {"order_id": order["order_id"]},
            {
                "$set": {
                    "customer_id": order.get("customer_id"),
                    "total": Decimal128(Decimal(str(order["total"]))),
                    "order_type": order.get("order_type"),
                    "table_number": order.get("table_number"),
                    "items": order.get("items", []),
                    "paid_at": paid_at,
                    "updated_at": _now(),
                },
                "$setOnInsert": {"status": "pagado", "created_at": paid_at},  # el evento no trae la fecha de creación
                "$addToSet": {"timeline": {"status": "pagado", "at": paid_at}},
            },
            upsert=True,
        )

    def mark_order_ready(self, order_id: str, ready_at: datetime) -> None:
        col = self._db["order_history"]
        entry = {"$addToSet": {"timeline": {"status": "listo", "at": ready_at}}}
        # No pisar un pedido ya entregado.
        result = col.update_one(
            {"order_id": order_id, "status": {"$ne": "entregado"}},
            {"$set": {"status": "listo", "ready_at": ready_at, "updated_at": _now()}, **entry},
        )
        if result.matched_count == 0:
            # O no existe aún (listo llegó antes que pagado) o ya está entregado.
            col.update_one(
                {"order_id": order_id},
                {"$set": {"ready_at": ready_at, "updated_at": _now()},
                 "$setOnInsert": {"status": "listo"}, **entry},
                upsert=True,
            )

    def set_receipt(self, order_id: str, object_name: str) -> None:
        self._db["order_history"].update_one(
            {"order_id": order_id},
            {"$set": {"receipt_object": object_name, "updated_at": _now()}},
            upsert=True,
        )

    # -- read model de reservas ------------------------------------------------------------------
    def upsert_reservation(self, event: dict, *, status: str, reason: str | None = None) -> None:
        """`created` solo fija el estado al insertar; `confirmed` no revive una reserva cancelada."""
        reservation_id = event["reservation_id"]
        base = {
            "customer_id": event.get("customer_id"),
            "date": event.get("date"),
            "time": event.get("time"),
            "people": event.get("people"),
            "updated_at": _now(),
        }
        col = self._db["reservation_read"]
        if status == "pendiente":
            col.update_one(
                {"reservation_id": reservation_id},
                {"$set": base, "$setOnInsert": {"status": "pendiente", "created_at": _now()}},
                upsert=True,
            )
            return
        fields = {k: v for k, v in base.items() if v is not None}
        if status == "confirmada":
            result = col.update_one(
                {"reservation_id": reservation_id, "status": {"$ne": "cancelada"}},
                {"$set": {**fields, "status": "confirmada"}},
            )
            if result.matched_count == 0:
                col.update_one(
                    {"reservation_id": reservation_id},
                    {"$set": fields, "$setOnInsert": {"status": "confirmada", "created_at": _now()}},
                    upsert=True,
                )
        else:  # cancelada: estado terminal
            col.update_one(
                {"reservation_id": reservation_id},
                {"$set": {**fields, "status": "cancelada", "cancel_reason": reason},
                 "$setOnInsert": {"created_at": _now()}},
                upsert=True,
            )

    # -- notificaciones simuladas ----------------------------------------------------------------
    def add_notification(self, event_id: str, kind: str, customer_id: str | None, message: str) -> bool:
        """Registra la notificación (una por evento y tipo). Devuelve False si ya existía."""
        try:
            self._db["notifications"].insert_one(
                {"event_id": event_id, "kind": kind, "customer_id": customer_id,
                 "channel": "log", "message": message, "created_at": _now()}
            )
        except DuplicateKeyError:
            return False
        logger.info("NOTIFICACIÓN simulada [%s] para %s: %s", kind, customer_id or "mostrador", message)
        return True
