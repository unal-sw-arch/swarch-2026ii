"""Reservas: propiedad validada con el JWT y eventos de dominio vía outbox.

El cliente (`reservations:manage_own`) solo ve y cancela las propias; el personal
(`reservations:manage_all`) gestiona todas y es el único que puede confirmar.
"""
import datetime as dt
import uuid
from zoneinfo import ZoneInfo

from sqlalchemy.orm import Session

from app.config import settings
from app.errors import Conflict, Forbidden, NotFound, Unprocessable
from app.events import outbox
from app.models import Reservation
from app.repositories import reservation_repository
from app.schemas import ReservationCreate
from app.security import Principal


def _now_local() -> dt.datetime:
    return dt.datetime.now(ZoneInfo(settings.business_tz))


def _validate_slot(date: dt.date, time: dt.time, people: int) -> dt.time:
    if people > settings.max_reservation_people:
        raise Unprocessable(
            f"Máximo {settings.max_reservation_people} personas por reserva",
            {"max_people": settings.max_reservation_people},
        )
    time = time.replace(second=0, microsecond=0, tzinfo=None)
    moment = dt.datetime.combine(date, time, tzinfo=ZoneInfo(settings.business_tz))
    if moment <= _now_local():
        raise Unprocessable("La fecha y hora de la reserva deben ser futuras")
    return time


def _payload(r: Reservation) -> dict:
    return {
        "reservation_id": str(r.id),
        "customer_id": r.customer_id,
        "date": r.reserved_date.isoformat(),
        "time": r.reserved_time.strftime("%H:%M"),
        "people": r.people,
    }


def create_reservation(db: Session, principal: Principal, payload: ReservationCreate) -> Reservation:
    time = _validate_slot(payload.date, payload.time, payload.people)
    reservation = Reservation(
        customer_id=principal.user_id,  # del JWT, nunca del cuerpo
        reserved_date=payload.date,
        reserved_time=time,
        people=payload.people,
        notes=payload.notes,
        status="pendiente",
    )
    db.add(reservation)
    db.flush()
    outbox.enqueue(db, "reservation.created", _payload(reservation))
    db.commit()
    db.refresh(reservation)
    return reservation


def get_visible(db: Session, reservation_id: uuid.UUID, principal: Principal) -> Reservation:
    reservation = reservation_repository.get(db, reservation_id)
    if reservation is None or (
        not principal.has("reservations:manage_all") and reservation.customer_id != principal.user_id
    ):
        raise NotFound("Reserva no encontrada")  # ajena = 404
    return reservation


def list_visible(
    db: Session,
    principal: Principal,
    *,
    status: str | None,
    date_from: dt.date | None,
    date_to: dt.date | None,
    limit: int,
    offset: int,
) -> list[Reservation]:
    customer_id = None if principal.has("reservations:manage_all") else principal.user_id
    return reservation_repository.list_reservations(
        db,
        customer_id=customer_id,
        status=status,
        date_from=date_from,
        date_to=date_to,
        limit=limit,
        offset=offset,
    )


def change_status(
    db: Session, principal: Principal, reservation_id: uuid.UUID, new_status: str, reason: str | None
) -> Reservation:
    is_staff = principal.has("reservations:manage_all")
    reservation = reservation_repository.get_for_update(db, reservation_id)
    if reservation is None or (not is_staff and reservation.customer_id != principal.user_id):
        raise NotFound("Reserva no encontrada")

    if reservation.status == "cancelada":
        db.rollback()
        raise Conflict("La reserva ya está cancelada")

    if new_status == "confirmada":
        if not is_staff:
            db.rollback()
            raise Forbidden("Solo el personal puede confirmar reservas")
        if reservation.status == "confirmada":
            db.rollback()
            raise Conflict("La reserva ya está confirmada")
        reservation.status = "confirmada"
        db.flush()
        if settings.publish_reservation_confirmed:
            outbox.enqueue(db, "reservation.confirmed", _payload(reservation))
    else:  # cancelada
        reservation.status = "cancelada"
        reservation.cancel_reason = reason or (
            "Cancelada por el personal" if is_staff else "Cancelada por el cliente"
        )
        db.flush()
        outbox.enqueue(
            db, "reservation.cancelled", {**_payload(reservation), "reason": reservation.cancel_reason}
        )
    db.commit()
    db.refresh(reservation)
    return reservation
