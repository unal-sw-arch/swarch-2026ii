"""Construcción perezosa de las dependencias reales (no se conecta al importar)."""
from functools import lru_cache

from handlers import Deps


@lru_cache
def build_deps() -> Deps:
    from clients.kitchen_client import KitchenClient
    from clients.minio_client import ReceiptStorage
    from clients.mongo_client import MongoStore
    from clients.order_client import OrderClient

    store = MongoStore()
    store.ensure_indexes()

    def dispatch_receipt(event: dict) -> None:
        from tasks.receipts import generate_receipt_task

        generate_receipt_task.delay(event)

    return Deps(
        kitchen=KitchenClient(),
        orders=OrderClient(),
        store=store,
        receipts=ReceiptStorage(),
        dispatch_receipt=dispatch_receipt,
    )
