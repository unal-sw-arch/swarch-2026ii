import datetime as dt
import uuid

from fastapi import APIRouter, Depends, Query, Request, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.events import outbox
from app.schemas import ReservationCreate, ReservationOut, ReservationUpdate
from app.security import Principal, require_permission
from app.services import reservation_service

router = APIRouter(prefix="/reservations", tags=["reservations"])

_ANY = ("reservations:manage_own", "reservations:manage_all")


@router.post("", response_model=ReservationOut, status_code=status.HTTP_201_CREATED, summary="Crear reserva")
def create_reservation(
    payload: ReservationCreate,
    request: Request,
    principal: Principal = Depends(require_permission(*_ANY)),
    db: Session = Depends(get_db),
):
    reservation = reservation_service.create_reservation(db, principal, payload)
    outbox.wake(request.app)
    return reservation


@router.get("", response_model=list[ReservationOut], summary="Listar reservas (propias o todas según permiso)")
def list_reservations(
    status_filter: str | None = Query(None, alias="status", pattern="^(pendiente|confirmada|cancelada)$"),
    date_from: dt.date | None = None,
    date_to: dt.date | None = None,
    limit: int = Query(50, ge=1, le=100),
    offset: int = Query(0, ge=0),
    principal: Principal = Depends(require_permission(*_ANY)),
    db: Session = Depends(get_db),
):
    return reservation_service.list_visible(
        db, principal, status=status_filter, date_from=date_from, date_to=date_to, limit=limit, offset=offset
    )


@router.get("/{reservation_id}", response_model=ReservationOut, summary="Detalle de una reserva")
def get_reservation(
    reservation_id: uuid.UUID,
    principal: Principal = Depends(require_permission(*_ANY)),
    db: Session = Depends(get_db),
):
    return reservation_service.get_visible(db, reservation_id, principal)


@router.put("/{reservation_id}", response_model=ReservationOut, summary="Cancelar o confirmar una reserva")
def update_reservation(
    reservation_id: uuid.UUID,
    payload: ReservationUpdate,
    request: Request,
    principal: Principal = Depends(require_permission(*_ANY)),
    db: Session = Depends(get_db),
):
    reservation = reservation_service.change_status(
        db, principal, reservation_id, payload.status, payload.reason
    )
    outbox.wake(request.app)
    return reservation


@router.delete("/{reservation_id}", response_model=ReservationOut, summary="Cancelar una reserva (alias de PUT)")
def cancel_reservation(
    reservation_id: uuid.UUID,
    request: Request,
    principal: Principal = Depends(require_permission(*_ANY)),
    db: Session = Depends(get_db),
):
    reservation = reservation_service.change_status(db, principal, reservation_id, "cancelada", None)
    outbox.wake(request.app)
    return reservation
