"""Pruebas del rol Comedor en Gestor de Pedidos (SQLite en memoria, inventario simulado).

Ejecutar desde BUFFIT/apps/gestor-pedidos:  python -m pytest tests -q
"""
import os
import sys
from datetime import datetime, timedelta, timezone

import jwt
import pytest

os.environ["DATABASE_URL"] = "sqlite:///:memory:"
os.environ["EVENTS_ENABLED"] = "false"  # sin broker en las pruebas
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app import create_app  # noqa: E402
from app.config import Config  # noqa: E402
from app.database import db  # noqa: E402
from app.models import Order, OrderItem  # noqa: E402
import app.routes as routes  # noqa: E402
from app.utils import slot_start_minutes  # noqa: E402


def token(sub, role="Usuario"):
    payload = {"sub": str(sub), "role": role,
               "exp": datetime.now(timezone.utc) + timedelta(hours=1)}
    return {"Authorization": "Bearer " + jwt.encode(payload, Config.JWT_SECRET_KEY, algorithm=Config.JWT_ALGORITHM)}


@pytest.fixture()
def client(monkeypatch):
    monkeypatch.setattr(routes, "check_and_reserve_inventory", lambda hall, items: (True, "ok"))
    monkeypatch.setattr(routes, "restock_inventory", lambda hall, items: (True, "ok"))
    app = create_app()
    with app.app_context():
        db.drop_all()
        db.create_all()
        yield app.test_client()


def make_order(client, who, hall="Comedor Central", slot="12:00 PM - 12:15 PM"):
    r = client.post("/pedidos/crearOrden", headers=token(who), json={
        "dining_hall": hall, "pickup_time_slot": slot,
        "items": [{"product_name": "Arroz blanco", "category": "carbohidratos", "quantity": 1}]})
    assert r.status_code == 201, r.get_json()
    return r.get_json()


def test_slot_parsing():
    assert slot_start_minutes("11:30 AM - 11:45 AM") == 11 * 60 + 30
    assert slot_start_minutes("12:15 PM - 12:30 PM") == 12 * 60 + 15
    assert slot_start_minutes("1:15 PM - 1:30 PM") == 13 * 60 + 15
    assert slot_start_minutes("basura") is None


def test_requires_token(client):
    assert client.get("/pedidos/comedor?dining_hall=X").status_code == 401


def test_consumer_cannot_use_staff_endpoints(client):
    assert client.get("/pedidos/comedor?dining_hall=X", headers=token(1)).status_code == 403
    o = make_order(client, 1)
    assert client.put(f"/pedidos/{o['id']}/deliver", headers=token(1)).status_code == 403
    assert client.get("/pedidos/", headers=token(1)).status_code == 403


def test_consumer_only_sees_own_orders(client):
    o = make_order(client, 1)
    assert client.get("/pedidos/user/1", headers=token(1)).status_code == 200
    assert client.get("/pedidos/user/1", headers=token(2)).status_code == 403
    assert client.get(f"/pedidos/{o['id']}", headers=token(2)).status_code == 403
    assert client.get(f"/pedidos/{o['id']}", headers=token(9, "Comedor")).status_code == 200


def test_user_id_comes_from_token(client):
    r = client.post("/pedidos/crearOrden", headers=token(5), json={
        "user_id": "999", "dining_hall": "Comedor Central", "pickup_time_slot": "12:00 PM - 12:15 PM",
        "items": [{"product_name": "Arroz blanco", "category": "c", "quantity": 1}]})
    assert r.get_json()["user_id"] == "5"


def test_staff_list_sorted_and_filtered_by_hall(client):
    make_order(client, 1, slot="1:15 PM - 1:30 PM")
    make_order(client, 2, slot="11:30 AM - 11:45 AM")
    make_order(client, 3, slot="12:15 PM - 12:30 PM")
    make_order(client, 4, hall="Comedor Hemeroteca")
    r = client.get("/pedidos/comedor?dining_hall=comedor central", headers=token(9, "Comedor"))
    assert r.status_code == 200
    slots = [o["pickup_time_slot"] for o in r.get_json()]
    assert slots == ["11:30 AM - 11:45 AM", "12:15 PM - 12:30 PM", "1:15 PM - 1:30 PM"]
    assert all(o["status_label"] == "Sin entregar" for o in r.get_json())


def test_deliver_flow(client):
    o = make_order(client, 1)
    staff = token(9, "Comedor")
    # comedor equivocado
    bad = client.put(f"/pedidos/{o['id']}/deliver", headers=staff, json={"dining_hall": "Comedor Hemeroteca"})
    assert bad.status_code == 403
    ok = client.put(f"/pedidos/{o['id']}/deliver", headers=staff, json={"dining_hall": "Comedor Central"})
    assert ok.status_code == 200 and ok.get_json()["order"]["status_label"] == "Entregada"
    # no se puede cancelar lo entregado
    assert client.put(f"/pedidos/{o['id']}/cancel", headers=token(1)).status_code == 409
    delivered = client.get("/pedidos/comedor?dining_hall=Comedor Central&status=DELIVERED", headers=staff)
    assert len(delivered.get_json()) == 1


def test_cancel_restocks_and_respects_30_minute_rule(client, monkeypatch):
    calls = []
    monkeypatch.setattr(routes, "restock_inventory", lambda hall, items: calls.append((hall, [i.product_name for i in items])) or (True, "ok"))
    # franja lejana (nunca a menos de 30 min): se cancela y devuelve stock
    far = make_order(client, 1, slot="11:30 PM - 11:45 PM")
    # (si la prueba corre después de las 11:00 PM de Bogotá, la regla bloquearía con razón)
    from app.utils import now_bogota
    if now_bogota().hour < 23:
        r = client.put(f"/pedidos/{far['id']}/cancel", headers=token(1))
        assert r.status_code == 200 and r.get_json()["order"]["status"] == "CANCELLED"
        assert calls == [("Comedor Central", ["Arroz blanco"])]
    # franja ya iniciada (00:00 de hoy): el consumidor no puede, el personal sí
    past = make_order(client, 1, slot="12:00 AM - 12:15 AM")
    assert client.put(f"/pedidos/{past['id']}/cancel", headers=token(1)).status_code == 409
    assert client.put(f"/pedidos/{past['id']}/cancel", headers=token(9, "Comedor")).status_code == 200
    # otro usuario no puede cancelar
    other = make_order(client, 1, slot="11:30 PM - 11:45 PM")
    assert client.put(f"/pedidos/{other['id']}/cancel", headers=token(2)).status_code == 403


def test_cancel_fails_closed_if_restock_fails(client, monkeypatch):
    o = make_order(client, 1, slot="11:30 PM - 11:45 PM")
    monkeypatch.setattr(routes, "restock_inventory", lambda hall, items: (False, "caído"))
    from app.utils import now_bogota
    if now_bogota().hour < 23:
        r = client.put(f"/pedidos/{o['id']}/cancel", headers=token(1))
        assert r.status_code == 502
        assert db.session.get(Order, o["id"]).status == "CONFIRMED"
