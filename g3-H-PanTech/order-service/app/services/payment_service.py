"""Pago simulado con idempotencia.

Garantías:
- El total lo fija el servidor: `amount` debe coincidir con `order.total` (si no, 422).
- Un pedido se paga una sola vez: `payments.order_id` es UNIQUE y la fila del pedido se bloquea.
- Repetir la petición con la misma `Idempotency-Key` devuelve el resultado original SIN publicar
  otro `order.paid`; sin llave (o con otra) responde 409.
- Redis acelera la repetición y evita pagos simultáneos; si Redis cae, PostgreSQL sigue siendo
  la garantía.
- `order.paid` entra al outbox en la misma transacción que el pago: no se pierde.
"""
import logging
import uuid
from decimal import Decimal

from redis.exceptions import RedisError
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.config import settings
from app.errors import Conflict, NotFound, Unprocessable
from app.events import outbox
from app.models import Order, Payment
from app.repositories import order_repository
from app.security import Principal

logger = logging.getLogger("order-service.payments")

CENT = Decimal("0.01")
_LOCK_TTL_SECONDS = 15


def _cache_key(order_id: uuid.UUID, idempotency_key: str) -> str:
    return f"idem:payment:{order_id}:{idempotency_key}"


def _cache_get(redis_client, order_id, idempotency_key) -> uuid.UUID | None:
    if redis_client is None or not idempotency_key:
        return None
    try:
        raw = redis_client.get(_cache_key(order_id, idempotency_key))
        return uuid.UUID(raw) if raw else None
    except (RedisError, ValueError):
        return None


def _cache_set(redis_client, order_id, idempotency_key, payment_id) -> None:
    if redis_client is None or not idempotency_key:
        return
    try:
        redis_client.set(
            _cache_key(order_id, idempotency_key), str(payment_id), ex=settings.idempotency_ttl_seconds
        )
    except RedisError:
        logger.warning("Redis no disponible: no se guardó la llave de idempotencia")


def _acquire_lock(redis_client, order_id) -> str | None:
    """Candado corto por pedido. Devuelve el token, o None si Redis no está disponible."""
    if redis_client is None:
        return None
    token = uuid.uuid4().hex
    try:
        if not redis_client.set(f"lock:payment:{order_id}", token, nx=True, ex=_LOCK_TTL_SECONDS):
            raise Conflict("Ya hay un pago en curso para este pedido")
    except RedisError:
        logger.warning("Redis no disponible: se continúa solo con el bloqueo de PostgreSQL")
        return None
    return token


def _release_lock(redis_client, order_id, token) -> None:
    if redis_client is None or token is None:
        return
    try:
        key = f"lock:payment:{order_id}"
        if redis_client.get(key) == token:
            redis_client.delete(key)
    except RedisError:
        pass


def _order_paid_payload(order: Order, payment: Payment) -> dict:
    """Payload mínimo del contrato + datos que el Worker necesita para encolar en cocina."""
    return {
        "order_id": str(order.id),
        "customer_id": order.customer_id,
        "total": str(order.total),
        "paid_at": payment.created_at.isoformat(),
        # Extras (el contrato fija solo el mínimo): evitan que el Worker consulte al Order Service.
        "order_type": order.order_type,
        "table_number": order.table_number,
        "items": [
            {
                "product_id": i.product_id,
                "name": i.product_name,
                "quantity": i.quantity,
                "prep_minutes": i.prep_minutes,
                "unit_price": str(i.unit_price),
            }
            for i in order.items
        ],
    }


def pay_order(
    db: Session,
    redis_client,
    principal: Principal,
    order_id: uuid.UUID,
    amount: Decimal,
    idempotency_key: str | None,
) -> tuple[Payment, Order, bool]:
    """Devuelve (pago, pedido, replay). `replay=True` si fue una repetición idempotente."""
    is_cashier = principal.has("orders:charge")  # mesero / supervisor / admin

    cached = _cache_get(redis_client, order_id, idempotency_key)
    if cached is not None:
        payment = order_repository.get_payment(db, cached)
        order = order_repository.get(db, order_id)
        if payment and order and (is_cashier or order.customer_id == principal.user_id):
            return payment, order, True

    lock = _acquire_lock(redis_client, order_id)
    try:
        order = order_repository.get_for_update(db, order_id)
        if order is None or (not is_cashier and order.customer_id != principal.user_id):
            raise NotFound("Pedido no encontrado")  # ajeno = 404

        existing = order_repository.get_payment_for_order(db, order.id)
        if existing is not None:
            db.rollback()
            if idempotency_key and existing.idempotency_key == idempotency_key:
                return existing, order, True
            raise Conflict("El pedido ya fue pagado")

        if order.status != "pendiente":
            db.rollback()
            raise Conflict(f"El pedido está '{order.status}' y no admite pago")

        if amount.quantize(CENT) != order.total.quantize(CENT):
            db.rollback()
            raise Unprocessable(
                "El monto no coincide con el total del pedido", {"expected_total": str(order.total)}
            )

        payment = Payment(
            order_id=order.id,
            amount=order.total,
            method="simulado",
            status="confirmado",
            idempotency_key=idempotency_key,
            paid_by=principal.user_id,
        )
        order.status = "pagado"
        db.add(payment)
        db.flush()  # asigna created_at al pago para el payload
        outbox.enqueue(db, "order.paid", _order_paid_payload(order, payment))
        try:
            db.commit()
        except IntegrityError:
            # Carrera: otro request pagó el mismo pedido entre la lectura y el commit.
            db.rollback()
            existing = order_repository.get_payment_for_order(db, order_id)
            if existing and idempotency_key and existing.idempotency_key == idempotency_key:
                return existing, order_repository.get(db, order_id), True
            raise Conflict("El pedido ya fue pagado")
        db.refresh(payment)
        db.refresh(order)
        _cache_set(redis_client, order_id, idempotency_key, payment.id)
        return payment, order, False
    finally:
        _release_lock(redis_client, order_id, lock)
