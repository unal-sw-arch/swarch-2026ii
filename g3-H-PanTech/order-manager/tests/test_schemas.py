"""Pruebas de validación de esquemas del Order Manager."""
import pytest
from pydantic import ValidationError

from app.schemas import OrderCreate, ProductCreate, ReservationCreate


def test_producto_requiere_categoria_valida():
    with pytest.raises(ValidationError):
        ProductCreate(name="X", category="otra", prep_minutes=1, price=1)


def test_pedido_requiere_al_menos_un_item():
    with pytest.raises(ValidationError):
        OrderCreate(type="para_llevar", items=[])


def test_cantidad_de_item_debe_ser_positiva():
    with pytest.raises(ValidationError):
        OrderCreate(type="para_llevar", items=[{"product_id": 1, "quantity": 0}])


def test_reserva_party_size_minimo():
    with pytest.raises(ValidationError):
        ReservationCreate(
            customer_name="Ana",
            party_size=0,
            reserved_at="2030-01-01T10:00:00",
        )
