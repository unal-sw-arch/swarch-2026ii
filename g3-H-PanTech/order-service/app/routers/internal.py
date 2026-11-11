"""Endpoints servicio a servicio (X-Service-Token). El API Gateway NO debe enrutar /internal/*."""
import uuid

from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from app.database import get_db
from app.schemas import InternalStatusResult, InternalStatusUpdate
from app.security import require_service_token
from app.services import order_service

router = APIRouter(prefix="/internal", tags=["internal"], dependencies=[Depends(require_service_token)])


@router.post(
    "/orders/{order_id}/status",
    response_model=InternalStatusResult,
    summary="Avanzar el estado por eventos de cocina (lo llama el Worker)",
)
def update_order_status(
    order_id: uuid.UUID, payload: InternalStatusUpdate, db: Session = Depends(get_db)
):
    order, applied = order_service.apply_kitchen_status(db, order_id, payload.status)
    return InternalStatusResult(id=order.id, status=order.status, applied=applied)
