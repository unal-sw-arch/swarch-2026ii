"""Contrato de eventos: lo que publica el Order Service cumple los esquemas compartidos
(docs/equipo/propuestas-yeison/schemas/), los mismos contra los que prueba el Worker."""
import json
from pathlib import Path

import pytest

from tests.perms import CUSTOMER, WAITER

SCHEMAS = Path(__file__).resolve().parents[2] / "docs" / "equipo" / "propuestas-yeison" / "schemas"
_TYPES = {"string": str, "integer": int, "null": type(None), "array": list, "object": dict}


def check(instance, schema, path="$"):
    """Validador mínimo de JSON Schema (type, required, properties, items, enum)."""
    wanted = schema.get("type")
    if wanted:
        options = wanted if isinstance(wanted, list) else [wanted]
        assert any(isinstance(instance, _TYPES[t]) and not (t == "integer" and isinstance(instance, bool))
                   for t in options), f"{path}: se esperaba {wanted}, llegó {type(instance).__name__}"
    if "enum" in schema:
        assert instance in schema["enum"], f"{path}: {instance!r} fuera de {schema['enum']}"
    if isinstance(instance, dict):
        for name in schema.get("required", []):
            assert name in instance, f"{path}: falta '{name}'"
        for name, sub in schema.get("properties", {}).items():
            if name in instance:
                check(instance[name], sub, f"{path}.{name}")
    if isinstance(instance, list) and "items" in schema:
        for i, element in enumerate(instance):
            check(element, schema["items"], f"{path}[{i}]")


def schema(name):
    path = SCHEMAS / f"{name}.schema.json"
    if not path.exists():
        pytest.skip("esquemas compartidos no disponibles en este entorno")
    return json.loads(path.read_text(encoding="utf-8"))


def test_order_paid_cumple_el_esquema(client, auth, outbox_rows):
    h = auth("cust-1", *CUSTOMER)
    order_id = client.post("/orders", json={"order_type": "para_llevar",
                           "items": [{"product_id": "p-cafe", "quantity": 2}]}, headers=h).json()["id"]
    client.post(f"/orders/{order_id}/payment", json={"amount": "9000.00"}, headers=h)
    check(outbox_rows("order.paid")[0].payload, schema("order.paid"))


def test_order_paid_de_mostrador_cumple_el_esquema(client, auth, outbox_rows):
    h = auth("waiter-1", *WAITER)
    order_id = client.post("/orders", json={"order_type": "en_mesa", "table_number": 3,
                           "items": [{"product_id": "p-pan", "quantity": 1}]}, headers=h).json()["id"]
    client.post(f"/orders/{order_id}/payment", json={"amount": "3000.00"}, headers=h)
    payload = outbox_rows("order.paid")[0].payload
    assert payload["customer_id"] is None
    check(payload, schema("order.paid"))


def test_eventos_de_reserva_cumplen_los_esquemas(client, auth, future_date, outbox_rows, monkeypatch):
    from app.config import settings

    monkeypatch.setattr(settings, "publish_reservation_confirmed", True)
    customer, waiter = auth("cust-1", *CUSTOMER), auth("waiter-1", *WAITER)
    rid = client.post("/reservations", json={"date": future_date, "time": "10:00", "people": 2}, headers=customer).json()["id"]
    client.put(f"/reservations/{rid}", json={"status": "confirmada"}, headers=waiter)
    client.delete(f"/reservations/{rid}", headers=customer)

    check(outbox_rows("reservation.created")[0].payload, schema("reservation.created"))
    check(outbox_rows("reservation.confirmed")[0].payload, schema("reservation.created"))
    check(outbox_rows("reservation.cancelled")[0].payload, schema("reservation.cancelled"))
