from decimal import Decimal

import pytest

from tests.perms import CUSTOMER, SUPERVISOR, WAITER

TAKEAWAY = {
    "order_type": "para_llevar",
    "items": [
        {"product_id": "p-cafe", "quantity": 2},
        {"product_id": "p-pan", "quantity": 1},
    ],
}


def test_cliente_crea_pedido_y_el_total_lo_calcula_el_servidor(client, auth):
    r = client.post("/orders", json=TAKEAWAY, headers=auth("cust-1", *CUSTOMER))
    assert r.status_code == 201
    body = r.json()
    assert Decimal(body["total"]) == Decimal("12000.00")  # 2 x 4500 + 1 x 3000
    assert body["status"] == "pendiente"
    assert body["customer_id"] == "cust-1"
    assert body["created_by"] == "cust-1"
    assert body["table_number"] is None
    assert {i["product_id"]: i["prep_minutes"] for i in body["items"]} == {"p-cafe": 3, "p-pan": 1}


@pytest.mark.parametrize("extra", [{"total": "1"}, {"customer_id": "cust-2"}, {"status": "pagado"}])
def test_el_cuerpo_no_puede_fijar_total_cliente_ni_estado(client, auth, extra):
    r = client.post("/orders", json={**TAKEAWAY, **extra}, headers=auth("cust-1", *CUSTOMER))
    assert r.status_code == 422


def test_cliente_no_puede_crear_pedido_en_mesa(client, auth):
    body = {"order_type": "en_mesa", "table_number": 4, "items": TAKEAWAY["items"]}
    r = client.post("/orders", json=body, headers=auth("cust-1", *CUSTOMER))
    assert r.status_code == 403
    assert r.json()["code"] == "forbidden"


def test_personal_crea_pedido_en_mesa_sin_cliente_asociado(client, auth):
    body = {"order_type": "en_mesa", "table_number": 4, "items": TAKEAWAY["items"]}
    r = client.post("/orders", json=body, headers=auth("staff-1", *WAITER, roles=("mesero",)))
    assert r.status_code == 201
    assert r.json()["customer_id"] is None
    assert r.json()["created_by"] == "staff-1"
    assert r.json()["table_number"] == 4


def test_en_mesa_exige_numero_de_mesa_y_para_llevar_lo_prohibe(client, auth):
    h = auth("staff-1", *WAITER)
    r1 = client.post("/orders", json={"order_type": "en_mesa", "items": TAKEAWAY["items"]}, headers=h)
    r2 = client.post("/orders", json={**TAKEAWAY, "table_number": 3}, headers=h)
    assert r1.status_code == 422
    assert r2.status_code == 422


def test_producto_inexistente_o_inactivo_se_rechaza(client, auth):
    h = auth("cust-1", *CUSTOMER)
    for pid in ("p-no-existe", "p-retirado"):
        r = client.post(
            "/orders",
            json={"order_type": "para_llevar", "items": [{"product_id": pid, "quantity": 1}]},
            headers=h,
        )
        assert r.status_code == 422
        assert r.json()["details"]["product_ids"] == [pid]


def test_cantidad_invalida_o_lineas_repetidas_se_rechazan(client, auth):
    h = auth("cust-1", *CUSTOMER)
    bad_qty = {"order_type": "para_llevar", "items": [{"product_id": "p-pan", "quantity": 0}]}
    dup = {
        "order_type": "para_llevar",
        "items": [{"product_id": "p-pan", "quantity": 1}, {"product_id": "p-pan", "quantity": 2}],
    }
    empty = {"order_type": "para_llevar", "items": []}
    for body in (bad_qty, dup, empty):
        assert client.post("/orders", json=body, headers=h).status_code == 422


def test_cliente_solo_ve_sus_pedidos(client, auth):
    h1, h2 = auth("cust-1", *CUSTOMER), auth("cust-2", *CUSTOMER)
    order_id = client.post("/orders", json=TAKEAWAY, headers=h1).json()["id"]

    assert [o["id"] for o in client.get("/orders", headers=h1).json()] == [order_id]
    assert client.get("/orders", headers=h2).json() == []
    # Pedido ajeno: 404, no revela que existe.
    assert client.get(f"/orders/{order_id}", headers=h2).status_code == 404
    assert client.get(f"/orders/{order_id}/status", headers=h2).status_code == 404
    assert client.get(f"/orders/{order_id}", headers=h1).status_code == 200


def test_mesero_ve_y_consulta_solo_los_pedidos_que_creo(client, auth):
    waiter = auth("waiter-1", *WAITER, roles=("mesero",))
    other_waiter = auth("waiter-2", *WAITER, roles=("mesero",))
    order_id = client.post("/orders", json=TAKEAWAY, headers=waiter).json()["id"]
    other_order_id = client.post("/orders", json=TAKEAWAY, headers=other_waiter).json()["id"]

    response = client.get("/orders", headers=waiter)
    assert response.status_code == 200
    assert [order["id"] for order in response.json()] == [order_id]
    assert client.get(f"/orders/{order_id}", headers=waiter).status_code == 200
    assert client.get(f"/orders/{other_order_id}", headers=waiter).status_code == 404


def test_personal_con_read_all_ve_todos(client, auth):
    client.post("/orders", json=TAKEAWAY, headers=auth("cust-1", *CUSTOMER))
    client.post("/orders", json=TAKEAWAY, headers=auth("cust-2", *CUSTOMER))
    r = client.get("/orders", headers=auth("sup-1", *SUPERVISOR, roles=("supervisor",)))
    assert r.status_code == 200
    assert len(r.json()) == 2


def test_cliente_cancela_su_pedido_pendiente(client, auth):
    customer = auth("cust-1", *CUSTOMER)
    order_id = client.post("/orders", json=TAKEAWAY, headers=customer).json()["id"]

    response = client.post(f"/orders/{order_id}/cancel", headers=customer)

    assert response.status_code == 200
    assert response.json()["status"] == "cancelado"


def test_cliente_no_puede_cancelar_pedido_ajeno(client, auth):
    owner = auth("cust-1", *CUSTOMER)
    other_customer = auth("cust-2", *CUSTOMER)
    order_id = client.post("/orders", json=TAKEAWAY, headers=owner).json()["id"]

    response = client.post(f"/orders/{order_id}/cancel", headers=other_customer)

    assert response.status_code == 404
    assert client.get(f"/orders/{order_id}", headers=owner).json()["status"] == "pendiente"


def test_cliente_no_puede_cancelar_pedido_ya_pagado(client, auth):
    customer = auth("cust-1", *CUSTOMER)
    order_id = client.post("/orders", json=TAKEAWAY, headers=customer).json()["id"]
    payment = client.post(
        f"/orders/{order_id}/payment",
        json={"amount": "12000.00", "method": "simulado"},
        headers=customer,
    )
    assert payment.status_code == 201

    response = client.post(f"/orders/{order_id}/cancel", headers=customer)

    assert response.status_code == 409
    assert client.get(f"/orders/{order_id}", headers=customer).json()["status"] == "pagado"


def test_pedidos_cancelados_no_aparecen_en_la_lista_de_pendientes(client, auth):
    customer = auth("cust-1", *CUSTOMER)
    supervisor = auth("sup-1", *SUPERVISOR, roles=("supervisor",))
    order_id = client.post("/orders", json=TAKEAWAY, headers=customer).json()["id"]
    canceled = client.post(f"/orders/{order_id}/cancel", headers=supervisor)
    assert canceled.status_code == 200

    pending = client.get("/orders?status=pendiente", headers=supervisor)
    canceled_orders = client.get("/orders?status=cancelado", headers=supervisor)

    assert pending.status_code == 200
    assert pending.json() == []
    assert [order["id"] for order in canceled_orders.json()] == [order_id]


def test_estado_del_pedido_para_polling(client, auth):
    h = auth("cust-1", *CUSTOMER)
    order_id = client.post("/orders", json=TAKEAWAY, headers=h).json()["id"]
    r = client.get(f"/orders/{order_id}/status", headers=h)
    assert r.status_code == 200
    assert r.json()["status"] == "pendiente"


def test_id_de_pedido_con_formato_invalido(client, auth):
    r = client.get("/orders/no-es-un-uuid", headers=auth("cust-1", *CUSTOMER))
    assert r.status_code == 422
    assert r.json()["code"] == "validation_error"
