from tests.perms import ADMIN, COOK, CUSTOMER

ORDER = {"order_type": "para_llevar", "items": [{"product_id": "p-pan", "quantity": 1}]}


def test_health_no_requiere_autenticacion(client):
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["service"] == "order-service"


def test_sin_token_401_con_formato_uniforme(client):
    r = client.get("/orders")
    assert r.status_code == 401
    body = r.json()
    assert body["code"] == "unauthorized"
    assert body["request_id"]
    assert r.headers["X-Request-Id"] == body["request_id"]


def test_se_propaga_el_request_id_recibido(client):
    r = client.get("/orders", headers={"X-Request-Id": "abc-123"})
    assert r.json()["request_id"] == "abc-123"


def test_token_invalido_expirado_o_de_otra_clave_401(client, auth):
    assert client.get("/orders", headers={"Authorization": "Bearer basura"}).status_code == 401
    expired = auth("cust-1", *CUSTOMER, expires_in=-3600)
    assert client.get("/orders", headers=expired).status_code == 401
    forged = auth("cust-1", *CUSTOMER, wrong_key=True)
    assert client.get("/orders", headers=forged).status_code == 401
    wrong_issuer = auth("cust-1", *CUSTOMER, issuer="otro-emisor")
    assert client.get("/orders", headers=wrong_issuer).status_code == 401
    wrong_audience = auth("cust-1", *CUSTOMER, audience="otra-app")
    assert client.get("/orders", headers=wrong_audience).status_code == 401


def test_sin_permiso_403(client, auth):
    h = auth("cook-1", *COOK)
    assert client.get("/orders", headers=h).status_code == 403
    assert client.post("/orders", json=ORDER, headers=h).status_code == 403
    assert client.get("/reservations", headers=h).status_code == 403
    assert client.post("/orders/00000000-0000-0000-0000-000000000000/payment", json={"amount": "1"}, headers=h).status_code == 403


def test_cliente_no_pasa_por_endpoints_de_administracion(client, auth):
    h = auth("cust-1", *CUSTOMER)
    assert client.post("/products", json={}, headers=h).status_code == 403
    assert client.post("/internal/orders/00000000-0000-0000-0000-000000000000/status",
                       json={"status": "listo"}, headers=h).status_code == 401


# ---- Endpoint interno (Worker → Order Service) ----
def _paid_order(client, auth):
    h = auth("cust-1", *CUSTOMER)
    order_id = client.post("/orders", json=ORDER, headers=h).json()["id"]
    client.post(f"/orders/{order_id}/payment", json={"amount": "3000.00"}, headers=h)
    return order_id


def _internal(client, order_id, status, token="token-de-pruebas"):
    return client.post(
        f"/internal/orders/{order_id}/status", json={"status": status}, headers={"X-Service-Token": token}
    )


def test_internal_exige_service_token(client, auth):
    order_id = _paid_order(client, auth)
    assert _internal(client, order_id, "listo", token="malo").status_code == 401
    assert client.post(f"/internal/orders/{order_id}/status", json={"status": "listo"}).status_code == 401


def test_order_ready_marca_listo_y_es_idempotente(client, auth):
    order_id = _paid_order(client, auth)
    first = _internal(client, order_id, "listo")
    assert first.status_code == 200 and first.json() == {"id": order_id, "status": "listo", "applied": True}
    again = _internal(client, order_id, "listo")
    assert again.json()["applied"] is False
    # Evento desordenado: no retrocede el estado.
    late = _internal(client, order_id, "en_preparacion")
    assert late.json() == {"id": order_id, "status": "listo", "applied": False}
    seen = client.get(f"/orders/{order_id}/status", headers=auth("cust-1", *CUSTOMER))
    assert seen.json()["status"] == "listo"


def test_internal_rechaza_pedido_sin_pago_y_pedido_inexistente(client, auth):
    h = auth("cust-1", *CUSTOMER)
    unpaid = client.post("/orders", json=ORDER, headers=h).json()["id"]
    assert _internal(client, unpaid, "listo").status_code == 409
    assert _internal(client, "00000000-0000-0000-0000-000000000000", "listo").status_code == 404
