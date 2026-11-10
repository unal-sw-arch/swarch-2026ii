"""Pruebas del Gestor de Estadisticas (SQLite en memoria, sin broker).

Ejecutar desde BUFFIT/apps/gestor-estadisticas:  python -m pytest tests -q
"""
import json
import os
import sys
from datetime import datetime, timedelta, timezone

import jwt
import pytest

os.environ["DATABASE_URL"] = "sqlite:///:memory:"
os.environ["CONSUMER_ENABLED"] = "false"
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app import create_app  # noqa: E402
from app.config import Config  # noqa: E402
from app.consumer import process_message  # noqa: E402
from app.database import db  # noqa: E402
from app.models import PedidoSubsidio  # noqa: E402
from app.processor import calcular_subsidio, handle_event, InvalidEvent  # noqa: E402

_seq = [0]


def ev(tipo="pedido.creado", order_id=1, user="u1", user_type="ESTUDIANTE", hall="Comedor Central",
       slot="12:00 PM - 12:15 PM", paid=8400.0, created="2026-11-07T15:00:00Z"):
    _seq[0] += 1
    return {"event_id": f"e{_seq[0]}", "type": tipo, "occurred_at": created, "order_id": order_id,
            "user_id": user, "user_type": user_type, "dining_hall": hall, "time_slot": slot,
            "amount_paid": paid, "created_at": created}


def auth(role):
    payload = {"sub": "1", "role": role, "exp": datetime.now(timezone.utc) + timedelta(hours=1)}
    return {"Authorization": "Bearer " + jwt.encode(payload, Config.JWT_SECRET_KEY, algorithm=Config.JWT_ALGORITHM)}


@pytest.fixture()
def app():
    app = create_app()
    with app.app_context():
        db.drop_all()
        db.create_all()
        yield app


@pytest.fixture()
def client(app):
    return app.test_client()


# ----------------------------------------------------------- calculo del subsidio
def test_subsidio_por_tipo():
    assert calcular_subsidio(16500) == 0       # sin carnet
    assert calcular_subsidio(8400) == 8100     # con carnet
    assert calcular_subsidio(0) == 16500       # subsidio completo
    assert calcular_subsidio(20000) == 0       # nunca negativo


# ----------------------------------------------------------- procesamiento de eventos
def test_creado_guarda_fila_con_subsidio(app):
    assert handle_event(ev(order_id=1)) == "aplicado"
    fila = db.session.get(PedidoSubsidio, 1)
    assert (fila.estado, fila.subsidio, fila.valor_pagado, fila.precio_referencia) == ("CONFIRMED", 8100, 8400, 16500)


def test_ciclo_creado_entregado(app):
    handle_event(ev("pedido.creado", order_id=2, user_type="APOYO_ALIMENTARIO", paid=0))
    handle_event(ev("pedido.entregado", order_id=2, user_type="APOYO_ALIMENTARIO", paid=0))
    fila = db.session.get(PedidoSubsidio, 2)
    assert (fila.estado, fila.subsidio) == ("DELIVERED", 16500)


def test_evento_duplicado_es_idempotente(app):
    e = ev(order_id=3)
    assert handle_event(e) == "aplicado"
    assert handle_event(e) == "duplicado"
    assert PedidoSubsidio.query.count() == 1


def test_estado_final_no_retrocede(app):
    handle_event(ev("pedido.cancelado", order_id=4))
    assert handle_event(ev("pedido.creado", order_id=4)) == "ignorado"  # llego tarde
    assert db.session.get(PedidoSubsidio, 4).estado == "CANCELLED"


def test_entregado_sin_creado_previo_crea_la_fila(app):
    handle_event(ev("pedido.entregado", order_id=5))
    assert db.session.get(PedidoSubsidio, 5).estado == "DELIVERED"


def test_fecha_usa_hora_de_bogota(app):
    handle_event(ev(order_id=6, created="2026-11-08T03:30:00Z"))  # 22:30 del 7 en Bogota
    assert db.session.get(PedidoSubsidio, 6).fecha.isoformat() == "2026-11-07"


@pytest.mark.parametrize("malo", [
    {"type": "pedido.otro"}, {"type": "pedido.creado"}, "texto",
    {"type": "pedido.creado", "event_id": "x", "order_id": "abc", "user_id": "1",
     "user_type": "GENERAL", "dining_hall": "C", "amount_paid": 1},
])
def test_eventos_invalidos(app, malo):
    with pytest.raises(InvalidEvent):
        handle_event(malo)


def test_consumer_decide_ack_o_reject(app):
    assert process_message(app, json.dumps(ev(order_id=7)).encode()) == "ack"
    assert process_message(app, b"no es json") == "reject"
    assert process_message(app, json.dumps({"type": "x"}).encode()) == "reject"


# ----------------------------------------------------------- API de estadisticas
def seed(app):
    handle_event(ev("pedido.creado", 10, "a", "ESTUDIANTE", "Comedor Central", paid=8400))
    handle_event(ev("pedido.entregado", 10, "a", "ESTUDIANTE", "Comedor Central", paid=8400))
    handle_event(ev("pedido.creado", 11, "a", "ESTUDIANTE", "Comedor Central", paid=8400))
    handle_event(ev("pedido.creado", 12, "b", "APOYO_ALIMENTARIO", "Comedor Central", paid=0))
    handle_event(ev("pedido.entregado", 12, "b", "APOYO_ALIMENTARIO", "Comedor Central", paid=0))
    handle_event(ev("pedido.creado", 13, "c", "GENERAL", "Comedor Hemeroteca", paid=16500))
    handle_event(ev("pedido.creado", 14, "d", "ESTUDIANTE", "Comedor Hemeroteca", paid=8400))
    handle_event(ev("pedido.cancelado", 14, "d", "ESTUDIANTE", "Comedor Hemeroteca", paid=8400))


def test_resumen_y_por_comedor(app, client):
    seed(app)
    r = client.get("/estadisticas/resumen", headers=auth("Administrador")).get_json()
    assert r["pedidos"] == 4 and r["cancelados"] == 1
    assert r["entregados"] == 2 and r["pendientes"] == 2
    assert r["subsidio_ejercido"] == 8100 + 16500
    assert r["subsidio_comprometido"] == 8100 + 0

    filas = client.get("/estadisticas/por-comedor", headers=auth("Comedor")).get_json()
    central = next(f for f in filas if f["comedor"] == "Comedor Central")
    hemer = next(f for f in filas if f["comedor"] == "Comedor Hemeroteca")
    assert central["pedidos"] == 3 and central["estudiantes_distintos"] == 2 and central["usuarios_distintos"] == 2
    assert hemer["pedidos"] == 1 and hemer["estudiantes_distintos"] == 0  # el GENERAL no cuenta como estudiante
    assert hemer["cancelados"] == 1


def test_filtros_y_otros_reportes(app, client):
    seed(app)
    h = auth("Administrador")
    r = client.get("/estadisticas/resumen?comedor=comedor central", headers=h).get_json()
    assert r["pedidos"] == 3
    assert client.get("/estadisticas/resumen?desde=2030-01-01", headers=h).get_json()["pedidos"] == 0
    assert client.get("/estadisticas/resumen?desde=hoy", headers=h).status_code == 400
    tipos = {f["tipo_usuario"]: f for f in client.get("/estadisticas/por-tipo-usuario", headers=h).get_json()}
    assert tipos["APOYO_ALIMENTARIO"]["subsidio_ejercido"] == 16500
    assert client.get("/estadisticas/por-franja", headers=h).get_json()[0]["pedidos"] == 4
    assert client.get("/estadisticas/serie-diaria", headers=h).get_json()[0]["fecha"] == "2026-11-07"


def test_permisos(client):
    assert client.get("/estadisticas/resumen").status_code == 401
    assert client.get("/estadisticas/resumen", headers=auth("Usuario")).status_code == 403
    assert client.get("/estadisticas/resumen", headers=auth("Comedor")).status_code == 200
    assert client.get("/health").status_code == 200
