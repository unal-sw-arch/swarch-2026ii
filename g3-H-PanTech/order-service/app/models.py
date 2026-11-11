"""Modelos del esquema `orders` en PostgreSQL (fuente de verdad transaccional).

Los ids de usuario (`customer_id`, `created_by`, `paid_by`) son texto porque el claim
`sub` lo define Identity Service; así no se asume UUID ni entero.
"""
from __future__ import annotations

import uuid
from datetime import date, datetime, time, timezone
from decimal import Decimal

from sqlalchemy import (
    JSON,
    CheckConstraint,
    Date,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    Numeric,
    String,
    Text,
    Time,
    Uuid,
)
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base

ORDER_TYPES = ("para_llevar", "en_mesa")
# Orden del ciclo de vida; `cancelado` queda fuera del avance lineal.
ORDER_STATUS_FLOW = ("pendiente", "pagado", "en_preparacion", "listo", "entregado")
RESERVATION_STATUSES = ("pendiente", "confirmada", "cancelada")


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


class Order(Base):
    __tablename__ = "orders"
    __table_args__ = (
        CheckConstraint("order_type IN ('para_llevar', 'en_mesa')", name="ck_orders_order_type"),
        CheckConstraint(
            "status IN ('pendiente', 'pagado', 'en_preparacion', 'listo', 'entregado', 'cancelado')",
            name="ck_orders_status",
        ),
        CheckConstraint(
            "(order_type = 'en_mesa' AND table_number IS NOT NULL) "
            "OR (order_type = 'para_llevar' AND table_number IS NULL)",
            name="ck_orders_table_number",
        ),
        Index("ix_orders_customer_id", "customer_id"),
        Index("ix_orders_status", "status"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    # Cliente dueño del pedido (JWT). NULL en pedidos de mostrador creados por personal.
    customer_id: Mapped[str | None] = mapped_column(String(64))
    created_by: Mapped[str] = mapped_column(String(64))
    order_type: Mapped[str] = mapped_column(String(20))
    table_number: Mapped[int | None] = mapped_column(Integer)
    status: Mapped[str] = mapped_column(String(20), default="pendiente")
    total: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    notes: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, onupdate=utcnow)

    items: Mapped[list[OrderItem]] = relationship(
        back_populates="order", cascade="all, delete-orphan", lazy="selectin"
    )


class OrderItem(Base):
    __tablename__ = "order_items"
    __table_args__ = (
        CheckConstraint("quantity > 0", name="ck_order_items_quantity"),
        Index("ix_order_items_order_id", "order_id"),
        Index("ix_order_items_product_id", "product_id"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    order_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("orders.orders.id", ondelete="CASCADE"))
    # Instantánea del producto al momento del pedido (el catálogo puede cambiar después).
    product_id: Mapped[str] = mapped_column(String(64))
    product_name: Mapped[str] = mapped_column(String(120))
    unit_price: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    prep_minutes: Mapped[int] = mapped_column(Integer)
    quantity: Mapped[int] = mapped_column(Integer)
    line_total: Mapped[Decimal] = mapped_column(Numeric(12, 2))

    order: Mapped[Order] = relationship(back_populates="items")


class Payment(Base):
    __tablename__ = "payments"

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    # unique: un pedido solo puede tener un pago (red de seguridad de la idempotencia).
    order_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("orders.orders.id"), unique=True)
    amount: Mapped[Decimal] = mapped_column(Numeric(12, 2))
    method: Mapped[str] = mapped_column(String(20), default="simulado")
    status: Mapped[str] = mapped_column(String(20), default="confirmado")
    idempotency_key: Mapped[str | None] = mapped_column(String(100))
    paid_by: Mapped[str] = mapped_column(String(64))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)


class Reservation(Base):
    __tablename__ = "reservations"
    __table_args__ = (
        CheckConstraint(
            "status IN ('pendiente', 'confirmada', 'cancelada')", name="ck_reservations_status"
        ),
        CheckConstraint("people > 0", name="ck_reservations_people"),
        Index("ix_reservations_customer_id", "customer_id"),
        Index("ix_reservations_reserved_date", "reserved_date"),
    )

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    customer_id: Mapped[str] = mapped_column(String(64))
    reserved_date: Mapped[date] = mapped_column(Date)
    reserved_time: Mapped[time] = mapped_column(Time)
    people: Mapped[int] = mapped_column(Integer)
    notes: Mapped[str | None] = mapped_column(Text)
    status: Mapped[str] = mapped_column(String(20), default="pendiente")
    cancel_reason: Mapped[str | None] = mapped_column(String(200))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow, onupdate=utcnow)


class Outbox(Base):
    """Eventos pendientes de publicar en RabbitMQ (patrón outbox)."""

    __tablename__ = "outbox"
    __table_args__ = (Index("ix_outbox_pending", "published_at", "next_attempt_at"),)

    id: Mapped[uuid.UUID] = mapped_column(Uuid, primary_key=True, default=uuid.uuid4)
    event_type: Mapped[str] = mapped_column(String(60))
    payload: Mapped[dict] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=utcnow)
    published_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    attempts: Mapped[int] = mapped_column(Integer, default=0, server_default="0")
    next_attempt_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    last_error: Mapped[str | None] = mapped_column(Text)
