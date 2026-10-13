"""Rutas de reservas (citas): CRUD básico."""
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from ..database import get_db
from ..models import Reservation
from ..schemas import ReservationCreate, ReservationOut, ReservationUpdate

router = APIRouter(tags=["reservas"])


@router.get("/reservations", response_model=list[ReservationOut])
def list_reservations(db: Session = Depends(get_db)):
    return db.query(Reservation).order_by(Reservation.reserved_at).all()


@router.post("/reservations", response_model=ReservationOut, status_code=201)
def create_reservation(payload: ReservationCreate, db: Session = Depends(get_db)):
    reservation = Reservation(**payload.model_dump())
    db.add(reservation)
    db.commit()
    db.refresh(reservation)
    return reservation


@router.get("/reservations/{reservation_id}", response_model=ReservationOut)
def get_reservation(reservation_id: int, db: Session = Depends(get_db)):
    reservation = db.get(Reservation, reservation_id)
    if reservation is None:
        raise HTTPException(status_code=404, detail="Reserva no encontrada")
    return reservation


@router.put("/reservations/{reservation_id}", response_model=ReservationOut)
def update_reservation(
    reservation_id: int, payload: ReservationUpdate, db: Session = Depends(get_db)
):
    reservation = db.get(Reservation, reservation_id)
    if reservation is None:
        raise HTTPException(status_code=404, detail="Reserva no encontrada")
    for field, value in payload.model_dump(exclude_unset=True).items():
        setattr(reservation, field, value)
    db.commit()
    db.refresh(reservation)
    return reservation


@router.delete("/reservations/{reservation_id}", status_code=204)
def delete_reservation(reservation_id: int, db: Session = Depends(get_db)):
    reservation = db.get(Reservation, reservation_id)
    if reservation is None:
        raise HTTPException(status_code=404, detail="Reserva no encontrada")
    db.delete(reservation)
    db.commit()
