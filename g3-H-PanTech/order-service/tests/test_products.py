from tests.perms import ADMIN, COOK, CUSTOMER

NEW = {"name": "Chocolate caliente", "category": "preparado", "prep_minutes": 4, "price": "6500.00"}


def test_menu_devuelve_solo_productos_activos(client, auth):
    r = client.get("/menu", headers=auth("cust-1", *CUSTOMER))
    assert r.status_code == 200
    assert sorted(p["product_id"] for p in r.json()) == ["p-cafe", "p-desayuno", "p-pan"]
    cafe = next(p for p in r.json() if p["product_id"] == "p-cafe")
    assert (cafe["category"], cafe["prep_minutes"]) == ("preparado", 3)


def test_menu_exige_autenticacion(client):
    assert client.get("/menu").status_code == 401


def test_cualquier_usuario_autenticado_ve_el_catalogo_sin_inactivos(client, auth):
    ids = [p["product_id"] for p in client.get("/products", headers=auth("cust-1", *CUSTOMER)).json()]
    assert "p-retirado" not in ids
    assert client.get("/products/p-retirado", headers=auth("cust-1", *CUSTOMER)).status_code == 404


def test_admin_ve_inactivos(client, auth):
    ids = [p["product_id"] for p in client.get("/products", headers=auth("adm-1", *ADMIN)).json()]
    assert "p-retirado" in ids
    assert client.get("/products/p-retirado", headers=auth("adm-1", *ADMIN)).status_code == 200


def test_solo_products_manage_modifica_el_catalogo(client, auth):
    for perms in (CUSTOMER, COOK):
        h = auth("u-1", *perms)
        assert client.post("/products", json=NEW, headers=h).status_code == 403
        assert client.put("/products/p-pan", json={"price": "1.00"}, headers=h).status_code == 403
        assert client.delete("/products/p-pan", headers=h).status_code == 403


def test_admin_crea_y_actualiza_productos(client, auth):
    h = auth("adm-1", *ADMIN)
    created = client.post("/products", json=NEW, headers=h)
    assert created.status_code == 201
    pid = created.json()["product_id"]

    updated = client.put(f"/products/{pid}", json={"price": "7000.00", "prep_minutes": 5}, headers=h)
    assert updated.status_code == 200
    assert updated.json()["price"] == "7000.00" and updated.json()["prep_minutes"] == 5
    assert client.put("/products/no-existe", json={"price": "1.00"}, headers=h).status_code == 404


def test_producto_con_pedidos_no_se_borra_pero_uno_sin_pedidos_si(client, auth):
    client.post(
        "/orders",
        json={"order_type": "para_llevar", "items": [{"product_id": "p-pan", "quantity": 1}]},
        headers=auth("cust-1", *CUSTOMER),
    )
    h = auth("adm-1", *ADMIN)
    assert client.delete("/products/p-pan", headers=h).status_code == 409
    assert client.delete("/products/p-desayuno", headers=h).status_code == 204
    assert client.delete("/products/p-desayuno", headers=h).status_code == 404


def test_validacion_de_productos(client, auth):
    h = auth("adm-1", *ADMIN)
    for bad in ({**NEW, "price": "-1"}, {**NEW, "category": "otra"}, {**NEW, "prep_minutes": -3}, {**NEW, "x": 1}):
        assert client.post("/products", json=bad, headers=h).status_code == 422
