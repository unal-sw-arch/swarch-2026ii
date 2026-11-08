"""Reglas de negocio de pedidos.

- El total SIEMPRE se calcula aquí con los precios del catálogo; nunca viene del cliente.
- `customer_id` sale del JWT. El cliente solo crea pedidos `para_llevar`.
- Un pedido nace `pendiente`: se encola en cocina solo después del pago (payment_service).
"""
import uuid
from decimal import Decimal

from sqlalchemy.orm import Session

from app.errors import Conflict, Forbidden, NotFound, Unprocessable
from app.models import ORDER_STATUS_FLOW, Order, OrderItem
from app.repositories import order_repository
from app.repositories.product_repository import ProductRepository
from app.schemas import OrderCreate
from app.security import Principal

CENT = Decimal("0.01")
_RANK = {status: i for i, status in enumerate(ORDER_STATUS_FLOW)}


def create_order(
    db: Session, products_repo: ProductRepository, principal: Principal, payload: OrderCreate
) -> Order:
    is_staff = principal.has("orders:create")  # mesero / supervisor / admin
    if not is_staff and payload.order_type != "para_llevar":
        raise Forbidden("Los clientes solo pueden crear pedidos para llevar")

    products = products_repo.get_many([line.product_id for line in payload.items], active_only=True)
    missing = [l.product_id for l in payload.items if l.product_id not in products]
    if missing:
        raise Unprocessable("Productos inexistentes o no disponibles", {"product_ids": missing})

    order = Order(
        # Pedido de mostrador: sin cliente asociado; el cliente solo conserva lo propio.
        customer_id=None if is_staff else principal.user_id,
        created_by=principal.user_id,
        order_type=payload.order_type,
        table_number=payload.table_number,
        notes=payload.notes,
    )
    total = Decimal("0")
    for line in payload.items:
        product = products[line.product_id]
        unit_price = product.price.quantize(CENT)
        line_total = (unit_price * line.quantity).quantize(CENT)
        order.items.append(
            OrderItem(
                product_id=product.product_id,
                product_name=product.name,
                unit_price=unit_price,
                prep_minutes=product.prep_minutes,
                quantity=line.quantity,
                line_total=line_total,
            )
        )
        total += line_total
    order.total = total
    return order_repository.add(db, order)


def get_visible_order(db: Session, order_id: uuid.UUID, principal: Principal) -> Order:
    """Pedido si existe Y el solicitante puede verlo. Ajeno = 404 (no filtra su existencia)."""
    order = order_repository.get(db, order_id)
    if order is None or (
        not principal.has("orders:read_all") and order.customer_id != principal.user_id
    ):
        raise NotFound("Pedido no encontrado")
    return order


def list_visible_orders(
    db: Session, principal: Principal, *, status: str | None, limit: int, offset: int
) -> list[Order]:
    customer_id = None if principal.has("orders:read_all") else principal.user_id
    return order_repository.list_orders(
        db, customer_id=customer_id, status=status, limit=limit, offset=offset
    )


def apply_kitchen_status(db: Session, order_id: uuid.UUID, new_status: str) -> tuple[Order, bool]:
    """Avanza el estado por eventos de cocina (vía Worker). Idempotente y tolerante al desorden:
    un estado igual o anterior al actual no se aplica (`applied=False`)."""
    order = order_repository.get_for_update(db, order_id)
    if order is None:
        raise NotFound("Pedido no encontrado")
    if order.status in ("pendiente", "cancelado"):
        raise Conflict(f"El pedido está '{order.status}' y no admite estados de cocina")
    if _RANK[new_status] <= _RANK[order.status]:
        db.rollback()  # libera el bloqueo
        return order, False
    order.status = new_status
    db.commit()
    db.refresh(order)
    return order, True
