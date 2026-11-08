"""Acceso a pedidos y pagos en PostgreSQL."""
import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Order, OrderItem, Payment


def add(db: Session, order: Order) -> Order:
    db.add(order)
    db.commit()
    db.refresh(order)
    return order


def get(db: Session, order_id: uuid.UUID) -> Order | None:
    return db.get(Order, order_id)


def get_for_update(db: Session, order_id: uuid.UUID) -> Order | None:
    """Bloquea la fila del pedido (serializa pagos concurrentes en PostgreSQL)."""
    stmt = (
        select(Order)
        .where(Order.id == order_id)
        .with_for_update()
        .execution_options(populate_existing=True)
    )
    return db.scalars(stmt).first()


def list_orders(
    db: Session, *, customer_id: str | None, status: str | None, limit: int, offset: int
) -> list[Order]:
    """`customer_id=None` devuelve todos los pedidos (alcance `all`)."""
    stmt = select(Order).order_by(Order.created_at.desc(), Order.id)
    if customer_id is not None:
        stmt = stmt.where(Order.customer_id == customer_id)
    if status is not None:
        stmt = stmt.where(Order.status == status)
    return list(db.scalars(stmt.limit(limit).offset(offset)))


def get_payment_for_order(db: Session, order_id: uuid.UUID) -> Payment | None:
    return db.scalars(select(Payment).where(Payment.order_id == order_id)).first()


def get_payment(db: Session, payment_id: uuid.UUID) -> Payment | None:
    return db.get(Payment, payment_id)


def product_has_orders(db: Session, product_id: str) -> bool:
    stmt = select(OrderItem.id).where(OrderItem.product_id == product_id).limit(1)
    return db.scalars(stmt).first() is not None
