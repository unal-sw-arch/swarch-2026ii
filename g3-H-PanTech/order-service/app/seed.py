"""Arranque de MongoDB: índices del catálogo y datos semilla (los 8 productos del Prototipo 1).

Jaime también puede sembrar productos con scripts/seed_demo.py: la semilla solo corre si la
colección `menu` está vacía, así que no duplica.
"""
import logging
import time
from decimal import Decimal

from bson.decimal128 import Decimal128
from pymongo.database import Database

logger = logging.getLogger("order-service.seed")

SEED_PRODUCTS = [
    # Vitrina / café: rápidos (1 minuto).
    ("p-cafe-americano", "Café americano", "vitrina", 1, "3.50"),
    ("p-cafe-con-leche", "Café con leche", "vitrina", 1, "4.00"),
    ("p-pan-croissant", "Pan croissant", "vitrina", 1, "2.50"),
    ("p-pan-de-queso", "Pan de queso", "vitrina", 1, "2.00"),
    # Preparados: toman tiempo.
    ("p-omelette-de-queso", "Omelette de queso", "preparado", 12, "12.00"),
    ("p-huevos-revueltos", "Huevos revueltos", "preparado", 10, "11.00"),
    ("p-sandwich-de-pollo", "Sándwich de pollo", "preparado", 8, "13.00"),
    ("p-calentado-paisa", "Calentado paisa", "preparado", 15, "15.00"),
]


def ensure_indexes(db: Database) -> None:
    db["menu"].create_index("product_id", unique=True)
    db["menu"].create_index([("category", 1), ("available", 1)])  # igual que mongodb/init.js
    db["order_history"].create_index("order_id", unique=True)
    db["order_history"].create_index([("customer_id", 1), ("paid_at", -1)])
    db["order_history"].create_index([("paid_at", -1)])
    db["reservation_read"].create_index("reservation_id", unique=True)
    db["reservation_read"].create_index([("customer_id", 1), ("date", -1)])


def seed_menu(db: Database) -> int:
    """Inserta el menú inicial si la colección está vacía. Devuelve cuántos insertó."""
    if db["menu"].estimated_document_count() > 0:
        return 0
    docs = [
        {
            "product_id": pid,
            "name": name,
            "category": category,
            "prep_minutes": prep,
            "price": Decimal128(Decimal(price)),
            "available": True,
        }
        for pid, name, category, prep, price in SEED_PRODUCTS
    ]
    db["menu"].insert_many(docs)
    return len(docs)


def bootstrap(db: Database, *, seed: bool, retries: int = 15, delay: float = 2.0) -> None:
    """Aplica índices y semilla reintentando mientras MongoDB arranca."""
    last_error = None
    for attempt in range(1, retries + 1):
        try:
            ensure_indexes(db)
            if seed:
                inserted = seed_menu(db)
                if inserted:
                    logger.info("Menú inicial sembrado: %s productos", inserted)
            return
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            logger.warning("MongoDB no disponible (intento %s/%s): %s", attempt, retries, exc)
            time.sleep(delay)
    logger.error("No se pudo preparar MongoDB; el menú no estará disponible: %s", last_error)
