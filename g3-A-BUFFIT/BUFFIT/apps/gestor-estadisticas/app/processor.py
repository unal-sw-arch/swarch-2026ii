"""Logica de negocio: aplica un evento de pedido y calcula el subsidio.

subsidio = precio_referencia - valor_pagado   (nunca negativo)
  GENERAL sin carnet (16500)   -> 0
  ESTUDIANTE con carnet (8400) -> 8100
  APOYO_ALIMENTARIO (0)        -> 16500
"""
from datetime import datetime, timedelta, timezone

from app.config import Config
from app.database import db
from app.models import PedidoSubsidio, EventoProcesado

BOGOTA = timezone(timedelta(hours=-5))
VALID_TYPES = {"pedido.creado": "CONFIRMED", "pedido.entregado": "DELIVERED", "pedido.cancelado": "CANCELLED"}
# Un pedido solo avanza: CONFIRMED -> (DELIVERED | CANCELLED). Un evento atrasado no lo devuelve.
RANK = {"CONFIRMED": 0, "DELIVERED": 1, "CANCELLED": 1}


class InvalidEvent(ValueError):
    """Evento mal formado: reintentarlo no sirve (va a la cola de errores)."""


def calcular_subsidio(valor_pagado: float, referencia: float = None) -> float:
    referencia = Config.REFERENCE_PRICE if referencia is None else referencia
    return max(0.0, round(referencia - float(valor_pagado), 2))


def _parse_fecha(event):
    raw = event.get("created_at") or event.get("occurred_at")
    try:
        dt = datetime.fromisoformat(str(raw).replace("Z", "+00:00"))
    except (TypeError, ValueError):
        raise InvalidEvent(f"fecha invalida: {raw!r}")
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(BOGOTA).date()


def handle_event(event: dict) -> str:
    """Aplica el evento. Devuelve 'aplicado', 'duplicado' o 'ignorado'. Lanza InvalidEvent si es invalido."""
    if not isinstance(event, dict):
        raise InvalidEvent("el evento no es un objeto JSON")
    tipo = event.get("type")
    if tipo not in VALID_TYPES:
        raise InvalidEvent(f"tipo desconocido: {tipo!r}")
    for campo in ("event_id", "order_id", "user_id", "user_type", "dining_hall", "amount_paid"):
        if event.get(campo) in (None, ""):
            raise InvalidEvent(f"falta el campo {campo}")
    try:
        order_id = int(event["order_id"])
        pagado = float(event["amount_paid"])
    except (TypeError, ValueError):
        raise InvalidEvent("order_id o amount_paid no son numericos")
    if pagado < 0:
        raise InvalidEvent("amount_paid negativo")

    if db.session.get(EventoProcesado, str(event["event_id"])):
        return "duplicado"

    nuevo_estado = VALID_TYPES[tipo]
    fila = db.session.get(PedidoSubsidio, order_id)
    resultado = "aplicado"
    if fila is None:
        fila = PedidoSubsidio(
            order_id=order_id,
            user_id=str(event["user_id"]),
            user_type=str(event["user_type"]).upper(),
            dining_hall=str(event["dining_hall"]),
            time_slot=event.get("time_slot"),
            precio_referencia=Config.REFERENCE_PRICE,
            valor_pagado=pagado,
            subsidio=calcular_subsidio(pagado),
            estado=nuevo_estado,
            fecha=_parse_fecha(event),
        )
        db.session.add(fila)
    elif RANK[nuevo_estado] > RANK[fila.estado]:
        fila.estado = nuevo_estado
    else:
        resultado = "ignorado"

    db.session.add(EventoProcesado(event_id=str(event["event_id"]), tipo=tipo, order_id=order_id))
    db.session.commit()
    return resultado
