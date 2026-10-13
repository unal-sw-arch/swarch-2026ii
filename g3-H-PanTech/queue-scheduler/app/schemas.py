"""Esquemas Pydantic del Queue Scheduler."""
from datetime import datetime
from typing import List, Literal, Optional

from pydantic import BaseModel, Field

OrderType = Literal["para_llevar", "en_mesa"]
OrderStatus = Literal["pendiente", "en_preparacion", "listo", "entregado"]


class OrderItemIn(BaseModel):
    product_id: int = Field(gt=0)
    quantity: int = Field(gt=0)


class OrderIn(BaseModel):
    """Payload que envía el Order Manager al encolar un pedido nuevo."""

    order_id: int = Field(gt=0)
    type: OrderType
    table_number: Optional[int] = Field(default=None, ge=1)
    items: List[OrderItemIn] = Field(min_length=1)


class StatusUpdate(BaseModel):
    status: Literal["en_preparacion", "listo"]


class OrderItemOut(BaseModel):
    product_id: int
    name: str
    quantity: int
    prep_minutes: int


class OrderOut(BaseModel):
    order_id: int
    type: OrderType
    table_number: Optional[int] = None
    status: OrderStatus
    estimated_minutes: int
    total_items: int
    items: List[OrderItemOut]
    created_at: Optional[str] = None
    arrival_ts: float


class DispatchOut(BaseModel):
    para_llevar: List[OrderOut]
    en_mesa: List[OrderOut]


# ---------------------- Historial de estados (Requerimiento 9) ---------------
class StatusEventOut(BaseModel):
    status: OrderStatus
    changed_at: datetime


class OrderHistoryOut(BaseModel):
    """Historial completo de una comanda, con sus marcas de tiempo."""

    order_id: int
    events: List[StatusEventOut]
    started_at: Optional[datetime] = None
    ready_at: Optional[datetime] = None
    delivered_at: Optional[datetime] = None
    duration_seconds: Optional[int] = None


class HistoryRowOut(BaseModel):
    """Fila del reporte de historial por comanda."""

    order_id: int
    status: OrderStatus
    started_at: datetime
    ready_at: Optional[datetime] = None
    delivered_at: Optional[datetime] = None
    duration_seconds: Optional[int] = None
