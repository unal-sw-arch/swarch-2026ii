from app.database import get_redis
from app.main import app
from tests.perms import CUSTOMER

ORDER = {"order_type": "para_llevar", "items": [{"product_id": "p-cafe", "quantity": 2}]}


def _setup(client, auth):
    return client.post("/orders", json=ORDER, headers=auth("cust-1", *CUSTOMER)).json()["id"]


def _pay(client, auth, order_id, key=None, sub="cust-1"):
    headers = auth(sub, *CUSTOMER)
    if key:
        headers["Idempotency-Key"] = key
    return client.post(f"/orders/{order_id}/payment", json={"amount": "9000.00"}, headers=headers)


def test_pago_repetido_con_la_misma_llave_no_publica_dos_veces(client, auth, outbox_rows):
    order_id = _setup(client, auth)
    first = _pay(client, auth, order_id, key="k-1")
    second = _pay(client, auth, order_id, key="k-1")

    assert first.status_code == 201
    assert second.status_code == 200
    assert second.json()["payment_id"] == first.json()["payment_id"]
    assert second.json()["idempotent_replay"] is True
    assert len(outbox_rows("order.paid")) == 1


def test_repeticion_sin_redis_se_resuelve_con_postgresql(client, auth, outbox_rows):
    app.dependency_overrides[get_redis] = lambda: None
    order_id = _setup(client, auth)
    first = _pay(client, auth, order_id, key="k-2")
    second = _pay(client, auth, order_id, key="k-2")
    assert (first.status_code, second.status_code) == (201, 200)
    assert len(outbox_rows("order.paid")) == 1


def test_pedido_pagado_no_se_paga_otra_vez_sin_llave_o_con_otra(client, auth, outbox_rows):
    order_id = _setup(client, auth)
    assert _pay(client, auth, order_id, key="k-3").status_code == 201
    assert _pay(client, auth, order_id).status_code == 409
    assert _pay(client, auth, order_id, key="otra-llave").status_code == 409
    assert len(outbox_rows("order.paid")) == 1


def test_la_repeticion_de_otro_usuario_no_obtiene_el_pago_ajeno(client, auth):
    order_id = _setup(client, auth)
    _pay(client, auth, order_id, key="k-4")
    assert _pay(client, auth, order_id, key="k-4", sub="cust-2").status_code == 404


def test_pago_en_curso_devuelve_409(client, auth, fake_redis, outbox_rows):
    order_id = _setup(client, auth)
    fake_redis.set(f"lock:payment:{order_id}", "otro-request", nx=True, ex=15)
    r = _pay(client, auth, order_id, key="k-5")
    assert r.status_code == 409
    assert outbox_rows("order.paid") == []


def test_el_candado_se_libera_despues_del_pago(client, auth, fake_redis):
    order_id = _setup(client, auth)
    _pay(client, auth, order_id, key="k-6")
    assert f"lock:payment:{order_id}" not in fake_redis.store
