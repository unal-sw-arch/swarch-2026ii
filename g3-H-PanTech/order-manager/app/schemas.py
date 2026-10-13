"""Esquemas Pydantic del Order Manager."""
from datetime import datetime
from decimal import Decimal
from typing import List, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field

ProductCategory = Literal["vitrina", "preparado"]
OrderType = Literal["para_llevar", "en_mesa"]


# ----------------------------- Productos -----------------------------------
class ProductBase(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    category: ProductCategory
    prep_minutes: int = Field(ge=0, le=600)
    price: Decimal = Field(ge=0)
    active: bool = True


class ProductCreate(ProductBase):
    pass


class ProductUpdate(BaseModel):
    name: Optional[str] = Field(default=None, min_length=1, max_length=120)
    category: Optional[ProductCategory] = None
    prep_minutes: Optional[int] = Field(default=None, ge=0, le=600)
    price: Optional[Decimal] = Field(default=None, ge=0)
    active: Optional[bool] = None


class ProductOut(ProductBase):
    model_config = ConfigDict(from_attributes=True)
    id: int


# ------------------------------- Pedidos -----------------------------------
class OrderItemCreate(BaseModel):
    product_id: int = Field(gt=0)
    quantity: int = Field(gt=0, le=100)


class OrderCreate(BaseModel):
    type: OrderType
    table_number: Optional[int] = Field(default=None, ge=1)
    items: List[OrderItemCreate] = Field(min_length=1)


class OrderItemOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    product_id: int
    quantity: int
    name: Optional[str] = None


class OrderOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: int
    type: OrderType
    table_number: Optional[int] = None
    created_at: datetime
    items: List[OrderItemOut]


# ----------------------------- Reservas ------------------------------------
class ReservationBase(BaseModel):
    customer_name: str = Field(min_length=1, max_length=120)
    party_size: int = Field(ge=1, le=100)
    reserved_at: datetime
    notes: Optional[str] = Field(default=None, max_length=500)


class ReservationCreate(ReservationBase):
    pass


class ReservationUpdate(BaseModel):
    customer_name: Optional[str] = Field(default=None, min_length=1, max_length=120)
    party_size: Optional[int] = Field(default=None, ge=1, le=100)
    reserved_at: Optional[datetime] = None
    notes: Optional[str] = Field(default=None, max_length=500)


class ReservationOut(ReservationBase):
    model_config = ConfigDict(from_attributes=True)
    id: int
    created_at: datetime


class HealthOut(BaseModel):
    status: str
    service: str
