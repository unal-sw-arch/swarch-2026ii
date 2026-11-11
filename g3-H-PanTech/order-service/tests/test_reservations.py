import datetime as dt

from app.config import settings

from tests.perms import CUSTOMER, WAITER


def _body(date, time="10:00", people=2, **extra):
    return {"date": date, "time": time, "people": people, **extra}


def _create(client, auth, date, sub="cust-1", perms=CUSTOMER, **kw):
    return client.post("/reservations", json=_body(date, **kw), headers=auth(sub, *perms))


def test_cliente_crea_reserva_con_customer_id_del_jwt(client, auth, future_date, outbox_rows):
    r = _create(client, auth, future_date, notes="Cerca de la ventana")
    assert r.status_code == 201
    body = r.json()
    assert body["status"] == "pendiente"
    assert body["customer_id"] == "cust-1"
    assert body["date"] == future_date and body["time"] == "10:00:00"

    event = outbox_rows("reservation.created")[0].payload
    assert event["reservation_id"] == body["id"]
    assert event["customer_id"] == "cust-1"
    assert (event["date"], event["time"], event["people"]) == (future_date, "10:00", 2)
    assert {"event_id", "occurred_at"} <= event.keys()


def test_el_cuerpo_no_puede_fijar_customer_id_ni_estado(client, auth, future_date):
    for extra in ({"customer_id": "cust-2"}, {"status": "confirmada"}):
        r = client.post(
            "/reservations", json=_body(future_date, **extra), headers=auth("cust-1", *CUSTOMER)
        )
        assert r.status_code == 422


def test_fecha_pasada_y_exceso_de_personas_se_rechazan(client, auth, future_date):
    yesterday = (dt.date.today() - dt.timedelta(days=1)).isoformat()
    assert _create(client, auth, yesterday).status_code == 422
    assert _create(client, auth, future_date, people=500).status_code == 422
    assert _create(client, auth, future_date, people=0).status_code == 422
    assert _create(client, auth, "no-es-fecha").status_code == 422


def test_cliente_solo_ve_sus_reservas_y_la_ajena_es_404(client, auth, future_date):
    mine = _create(client, auth, future_date, sub="cust-1").json()["id"]
    _create(client, auth, future_date, sub="cust-2")

    own = client.get("/reservations", headers=auth("cust-1", *CUSTOMER)).json()
    assert [r["id"] for r in own] == [mine]
    assert client.get(f"/reservations/{mine}", headers=auth("cust-2", *CUSTOMER)).status_code == 404
    assert client.get(f"/reservations/{mine}", headers=auth("cust-1", *CUSTOMER)).status_code == 200


def test_personal_ve_todas_las_reservas(client, auth, future_date):
    _create(client, auth, future_date, sub="cust-1")
    _create(client, auth, future_date, sub="cust-2")
    r = client.get("/reservations", headers=auth("waiter-1", *WAITER))
    assert len(r.json()) == 2


def test_cliente_cancela_su_reserva_y_se_publica_el_evento(client, auth, future_date, outbox_rows):
    rid = _create(client, auth, future_date).json()["id"]
    r = client.put(
        f"/reservations/{rid}",
        json={"status": "cancelada", "reason": "Cambio de planes"},
        headers=auth("cust-1", *CUSTOMER),
    )
    assert r.status_code == 200
    assert r.json()["status"] == "cancelada"
    event = outbox_rows("reservation.cancelled")[0].payload
    assert event["reservation_id"] == rid
    assert event["customer_id"] == "cust-1"
    assert event["reason"] == "Cambio de planes"


def test_cancelacion_repetida_devuelve_409_y_no_duplica_el_evento(client, auth, future_date, outbox_rows):
    rid = _create(client, auth, future_date).json()["id"]
    h = auth("cust-1", *CUSTOMER)
    assert client.delete(f"/reservations/{rid}", headers=h).status_code == 200
    assert client.delete(f"/reservations/{rid}", headers=h).status_code == 409
    assert client.put(f"/reservations/{rid}", json={"status": "cancelada"}, headers=h).status_code == 409
    assert len(outbox_rows("reservation.cancelled")) == 1


def test_cliente_no_puede_cancelar_reserva_ajena(client, auth, future_date, outbox_rows):
    rid = _create(client, auth, future_date, sub="cust-1").json()["id"]
    r = client.delete(f"/reservations/{rid}", headers=auth("cust-2", *CUSTOMER))
    assert r.status_code == 404
    assert outbox_rows("reservation.cancelled") == []


def test_solo_el_personal_confirma(client, auth, future_date, outbox_rows, monkeypatch):
    monkeypatch.setattr(settings, "publish_reservation_confirmed", True)
    rid = _create(client, auth, future_date).json()["id"]
    body = {"status": "confirmada"}
    assert client.put(f"/reservations/{rid}", json=body, headers=auth("cust-1", *CUSTOMER)).status_code == 403

    ok = client.put(f"/reservations/{rid}", json=body, headers=auth("waiter-1", *WAITER))
    assert ok.status_code == 200 and ok.json()["status"] == "confirmada"
    assert len(outbox_rows("reservation.confirmed")) == 1
    assert client.put(f"/reservations/{rid}", json=body, headers=auth("waiter-1", *WAITER)).status_code == 409


def test_personal_puede_cancelar_cualquier_reserva(client, auth, future_date):
    rid = _create(client, auth, future_date, sub="cust-1").json()["id"]
    r = client.delete(f"/reservations/{rid}", headers=auth("waiter-1", *WAITER))
    assert r.status_code == 200
    assert r.json()["cancel_reason"] == "Cancelada por el personal"


def test_confirmar_no_publica_evento_si_la_cola_no_esta_habilitada(client, auth, future_date, outbox_rows):
    """Sin cola enlazada en RabbitMQ, un `reservation.confirmed` bloquearía el outbox: por defecto no se publica."""
    rid = _create(client, auth, future_date).json()["id"]
    r = client.put(f"/reservations/{rid}", json={"status": "confirmada"}, headers=auth("waiter-1", *WAITER))
    assert r.status_code == 200
    assert outbox_rows("reservation.confirmed") == []
