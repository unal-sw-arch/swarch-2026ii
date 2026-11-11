"""Acceso a reservas en PostgreSQL."""
import datetime as dt
import uuid

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models import Reservation


def get(db: Session, reservation_id: uuid.UUID) -> Reservation | None:
    return db.get(Reservation, reservation_id)


def get_for_update(db: Session, reservation_id: uuid.UUID) -> Reservation | None:
    stmt = (
        select(Reservation)
        .where(Reservation.id == reservation_id)
        .with_for_update()
        .execution_options(populate_existing=True)
    )
    return db.scalars(stmt).first()


def list_reservations(
    db: Session,
    *,
    customer_id: str | None,
    status: str | None,
    date_from: dt.date | None,
    date_to: dt.date | None,
    limit: int,
    offset: int,
) -> list[Reservation]:
    """`customer_id=None` devuelve todas (alcance de `reservations:manage_all`)."""
    stmt = select(Reservation).order_by(Reservation.reserved_date, Reservation.reserved_time, Reservation.id)
    if customer_id is not None:
        stmt = stmt.where(Reservation.customer_id == customer_id)
    if status is not None:
        stmt = stmt.where(Reservation.status == status)
    if date_from is not None:
        stmt = stmt.where(Reservation.reserved_date >= date_from)
    if date_to is not None:
        stmt = stmt.where(Reservation.reserved_date <= date_to)
    return list(db.scalars(stmt.limit(limit).offset(offset)))
