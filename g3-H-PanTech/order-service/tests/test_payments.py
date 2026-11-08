from sqlalchemy import select

from app.models import Order
from tests.perms import COOK, CUSTOMER, WAITER

ORDER = {"order_type": "para_llevar", "items": [{"product_id": "p-cafe", "quantity": 2}]}  # 9000.00


def _create(client, auth, sub="cust-1", perms=CUSTOMER):
    return client.post("/orders", json=ORDER, headers=auth(sub, *perms)).json()["id"]


def _pay(client, auth, order_id, sub="cust-1", perms=CUSTOMER, amount="9000.00", key=None):
    headers = auth(sub, *perms)
    if key:
        headers["Idempotency-Key"] = key
    return client.post(f"/orders/{order_id}/payment", json={"amount": amount}, headers=headers)


def test_cliente_paga_su_pedido_y_queda_pagado(client, auth, outbox_rows):
    order_id = _create(client, auth)
    r = _pay(client, auth, order_id)
    assert r.status_code == 201
    body = r.json()
    assert body["order_status"] == "pagado"
    assert body["idempotent_replay"] is False
    assert body["paid_by"] == "cust-1"
    status = client.get(f"/orders/{order_id}/status", headers=auth("cust-1", *CUSTOMER))
    assert status.json()["status"] == "pagado"

    events = outbox_rows("order.paid")
    assert len(events) == 1
    payload = events[0].payload
    assert payload["order_id"] == order_id
    assert payload["customer_id"] == "cust-1"
    assert payload["total"] == "9000.00"
    assert {"event_id", "paid_at", "occurred_at", "items", "order_type"} <= payload.keys()
    assert payload["items"][0]["prep_minutes"] == 3


def test_monto_alterado_se_rechaza_y_no_cambia_nada(client, auth, outbox_rows, session_factory):
    order_id = _create(client, auth)
    r = _pay(client, auth, order_id, amount="1.00")
    assert r.status_code == 422
    assert r.json()["details"]["expected_total"] == "9000.00"
    assert outbox_rows("order.paid") == []
    with session_factory() as db:
        assert db.scalars(select(Order.status)).one() == "pendiente"


def test_pedido_ajeno_devuelve_404_y_no_se_paga(client, auth, outbox_rows):
    order_id = _create(client, auth, sub="cust-1")
    r = _pay(client, auth, order_id, sub="cust-2")
    assert r.status_code == 404
    assert outbox_rows("order.paid") == []


def test_personal_con_orders_charge_cobra_pedido_de_cliente(client, auth, outbox_rows):
    order_id = _create(client, auth)
    r = _pay(client, auth, order_id, sub="waiter-1", perms=WAITER)
    assert r.status_code == 201
    assert r.json()["paid_by"] == "waiter-1"
    assert outbox_rows("order.paid")[0].payload["customer_id"] == "cust-1"


def test_cocinero_no_puede_cobrar(client, auth):
    order_id = _create(client, auth)
    assert _pay(client, auth, order_id, sub="cook-1", perms=COOK).status_code == 403


def test_pedido_cancelado_no_admite_pago(client, auth, session_factory):
    order_id = _create(client, auth)
    with session_factory() as db:
        order = db.scalars(select(Order)).one()
        order.status = "cancelado"
        db.commit()
    assert _pay(client, auth, order_id).status_code == 409


def test_el_cuerpo_no_acepta_campos_extra(client, auth):
    order_id = _create(client, auth)
    r = client.post(
        f"/orders/{order_id}/payment",
        json={"amount": "9000.00", "status": "confirmado"},
        headers=auth("cust-1", *CUSTOMER),
    )
    assert r.status_code == 422
