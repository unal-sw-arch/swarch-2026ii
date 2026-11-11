"""DTOs de productos, pedidos, pagos y reservas."""
import datetime as dt
import uuid
from decimal import Decimal
from typing import Literal, Self

from pydantic import BaseModel, ConfigDict, Field, model_validator

ProductCategory = Literal["vitrina", "preparado"]


# ------------------------------- Productos ---------------------------------
class ProductIn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str = Field(min_length=1, max_length=120)
    category: ProductCategory
    prep_minutes: int = Field(ge=0, le=600)
    price: Decimal = Field(ge=0, max_digits=12, decimal_places=2)
    active: bool = True


class ProductUpdate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    name: str | None = Field(default=None, min_length=1, max_length=120)
    category: ProductCategory | None = None
    prep_minutes: int | None = Field(default=None, ge=0, le=600)
    price: Decimal | None = Field(default=None, ge=0, max_digits=12, decimal_places=2)
    active: bool | None = None


class ProductOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    product_id: str
    name: str
    category: str
    price: Decimal
    prep_minutes: int
    active: bool


class MenuItemOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    product_id: str
    name: str
    category: str
    price: Decimal
    prep_minutes: int


# -------------------------------- Pedidos ----------------------------------
class OrderItemIn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    product_id: str = Field(min_length=1, max_length=64)
    quantity: int = Field(ge=1, le=50)


class OrderCreate(BaseModel):
    # extra="forbid": el cuerpo no puede traer `total`, `customer_id`, `status`, etc.
    model_config = ConfigDict(extra="forbid")

    order_type: Literal["para_llevar", "en_mesa"]
    table_number: int | None = Field(default=None, ge=1, le=999)
    items: list[OrderItemIn] = Field(min_length=1, max_length=50)
    notes: str | None = Field(default=None, max_length=500)

    @model_validator(mode="after")
    def _validate(self) -> Self:
        if self.order_type == "en_mesa" and self.table_number is None:
            raise ValueError("table_number es obligatorio para pedidos en_mesa")
        if self.order_type == "para_llevar" and self.table_number is not None:
            raise ValueError("table_number no aplica a pedidos para_llevar")
        ids = [i.product_id for i in self.items]
        if len(ids) != len(set(ids)):
            raise ValueError("No repitas productos: usa una sola línea por producto con su cantidad")
        return self


class OrderItemOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    product_id: str
    product_name: str
    unit_price: Decimal
    prep_minutes: int
    quantity: int
    line_total: Decimal


class OrderOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    customer_id: str | None
    created_by: str
    order_type: str
    table_number: int | None
    status: str
    total: Decimal
    notes: str | None
    created_at: dt.datetime
    updated_at: dt.datetime
    items: list[OrderItemOut]


class OrderStatusOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    status: str
    updated_at: dt.datetime


class InternalStatusUpdate(BaseModel):
    """Lo envía el Worker (X-Service-Token) al consumir eventos de cocina."""

    model_config = ConfigDict(extra="forbid")

    status: Literal["en_preparacion", "listo", "entregado"]


class InternalStatusResult(BaseModel):
    id: uuid.UUID
    status: str
    applied: bool  # False si el evento llegó repetido o desordenado


# --------------------------------- Pagos -----------------------------------
class PaymentCreate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    amount: Decimal = Field(gt=0, max_digits=12, decimal_places=2)
    method: Literal["simulado"] = "simulado"


class PaymentOut(BaseModel):
    payment_id: uuid.UUID
    order_id: uuid.UUID
    amount: Decimal
    method: str
    status: str
    paid_by: str
    paid_at: dt.datetime
    order_status: str
    idempotent_replay: bool = False


# -------------------------------- Reservas ---------------------------------
class ReservationCreate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    date: dt.date
    time: dt.time
    people: int = Field(ge=1)
    notes: str | None = Field(default=None, max_length=500)


class ReservationUpdate(BaseModel):
    """PUT: solo cambia el estado. El cliente solo puede cancelar; confirmar es del personal."""

    model_config = ConfigDict(extra="forbid")

    status: Literal["confirmada", "cancelada"]
    reason: str | None = Field(default=None, max_length=200)


class ReservationOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    customer_id: str
    date: dt.date = Field(validation_alias="reserved_date")
    time: dt.time = Field(validation_alias="reserved_time")
    people: int
    notes: str | None
    status: str
    cancel_reason: str | None
    created_at: dt.datetime
    updated_at: dt.datetime
