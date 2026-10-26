"""Pruebas de la regla de prioridad y de la validación de transiciones."""
from types import SimpleNamespace

import pytest

from app.priority import (
    effective_minutes,
    estimate_minutes,
    is_valid_transition,
    kitchen_sort_key,
)


# ---------------------------------------------------------------------------
# Regla de prioridad
# ---------------------------------------------------------------------------

def test_effective_minutes_minimo_uno():
    """Los productos de vitrina (o valores inválidos) cuentan mínimo 1 minuto."""
    assert effective_minutes(1) == 1
    assert effective_minutes(0) == 1
    assert effective_minutes(-5) == 1
    assert effective_minutes(None) == 1
    assert effective_minutes(10) == 10


def test_estimate_minutes_multiplica_por_cantidad():
    """El tiempo estimado suma prep_minutes x cantidad de cada ítem."""
    items = [
        {"product_id": 1, "quantity": 2},  # café: 1 min  -> 2
        {"product_id": 2, "quantity": 1},  # omelette: 12 -> 12
    ]
    product_info = {1: {"prep_minutes": 1}, 2: {"prep_minutes": 12}}
    assert estimate_minutes(items, product_info) == 14


def test_estimate_minutes_usa_minimo_para_vitrina():
    """Aunque el producto tenga 0 minutos, la vitrina aporta al menos 1 por unidad."""
    items = [SimpleNamespace(product_id=5, quantity=3)]
    product_info = {5: {"prep_minutes": 0}}
    assert estimate_minutes(items, product_info) == 3


def test_cola_prioriza_pedido_rapido_aunque_llegue_despues():
    """Un café con pan (rápido) se atiende antes que un desayuno (lento)."""
    # El desayuno llegó primero (arrival_ts menor) pero es mucho más lento.
    desayuno = {"status": "pendiente", "estimated_minutes": 15, "arrival_ts": 100.0}
    cafe = {"status": "pendiente", "estimated_minutes": 2, "arrival_ts": 200.0}
    cola = sorted([desayuno, cafe], key=kitchen_sort_key)
    assert cola[0] is cafe


def test_cola_desempata_por_hora_de_llegada():
    """Con el mismo tiempo estimado, gana el que llegó antes."""
    primero = {"status": "pendiente", "estimated_minutes": 5, "arrival_ts": 10.0}
    segundo = {"status": "pendiente", "estimated_minutes": 5, "arrival_ts": 20.0}
    cola = sorted([segundo, primero], key=kitchen_sort_key)
    assert cola[0] is primero


def test_en_preparacion_siempre_arriba():
    """Las comandas en preparación se muestran por encima de las pendientes."""
    en_prep = {"status": "en_preparacion", "estimated_minutes": 30, "arrival_ts": 999.0}
    pendiente = {"status": "pendiente", "estimated_minutes": 1, "arrival_ts": 1.0}
    cola = sorted([pendiente, en_prep], key=kitchen_sort_key)
    assert cola[0] is en_prep


# ---------------------------------------------------------------------------
# Validación de transiciones de estado
# ---------------------------------------------------------------------------

@pytest.mark.parametrize(
    "current,new",
    [
        ("pendiente", "en_preparacion"),
        ("en_preparacion", "listo"),
        ("listo", "entregado"),
    ],
)
def test_transiciones_validas(current, new):
    assert is_valid_transition(current, new) is True


@pytest.mark.parametrize(
    "current,new",
    [
        ("pendiente", "listo"),          # no se puede saltar la preparación
        ("pendiente", "entregado"),
        ("en_preparacion", "pendiente"),  # no se puede retroceder
        ("listo", "en_preparacion"),
        ("entregado", "listo"),           # una comanda entregada es terminal
        ("entregado", "entregado"),
    ],
)
def test_transiciones_invalidas(current, new):
    assert is_valid_transition(current, new) is False
