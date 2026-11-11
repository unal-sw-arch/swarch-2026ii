from datetime import datetime

from flask import Blueprint, jsonify, request
from sqlalchemy import func, case

from app.auth import login_required
from app.config import Config
from app.database import db
from app.models import PedidoSubsidio as P

stats_bp = Blueprint("estadisticas", __name__)
ROLES = Config.ALLOWED_ROLES

# "Validos" = pedidos no cancelados (los cancelados no consumen subsidio ni cuentan como demanda)
VALIDO = P.estado != "CANCELLED"
ENTREGADO = P.estado == "DELIVERED"
PENDIENTE = P.estado == "CONFIRMED"


def _num(x):
    return round(float(x or 0), 2)


def _filtros():
    """Lee desde, hasta (YYYY-MM-DD) y comedor del query string. Devuelve (lista de condiciones, error)."""
    conds = []
    for nombre, op in (("desde", ">="), ("hasta", "<=")):
        valor = request.args.get(nombre)
        if valor:
            try:
                dia = datetime.strptime(valor, "%Y-%m-%d").date()
            except ValueError:
                return None, (jsonify({"error": f"{nombre} debe tener formato YYYY-MM-DD"}), 400)
            conds.append(P.fecha >= dia if op == ">=" else P.fecha <= dia)
    comedor = (request.args.get("comedor") or "").strip()
    if comedor:
        conds.append(func.lower(P.dining_hall) == comedor.lower())
    return conds, None


def _agregados():
    """Columnas agregadas comunes a los distintos reportes."""
    return [
        func.count(P.order_id).filter(VALIDO).label("pedidos"),
        func.count(func.distinct(P.user_id)).filter(VALIDO).label("usuarios_distintos"),
        func.count(func.distinct(P.user_id)).filter(VALIDO, P.user_type != "GENERAL").label("estudiantes_distintos"),
        func.count(P.order_id).filter(ENTREGADO).label("entregados"),
        func.count(P.order_id).filter(PENDIENTE).label("pendientes"),
        func.count(P.order_id).filter(P.estado == "CANCELLED").label("cancelados"),
        func.coalesce(func.sum(P.subsidio).filter(ENTREGADO), 0).label("subsidio_ejercido"),
        func.coalesce(func.sum(P.subsidio).filter(PENDIENTE), 0).label("subsidio_comprometido"),
        func.coalesce(func.sum(P.valor_pagado).filter(ENTREGADO), 0).label("recaudado"),
    ]


def _fila(r, **extra):
    d = dict(extra)
    d.update({
        "pedidos": r.pedidos,
        "usuarios_distintos": r.usuarios_distintos,
        "estudiantes_distintos": r.estudiantes_distintos,
        "entregados": r.entregados,
        "pendientes": r.pendientes,
        "cancelados": r.cancelados,
        "subsidio_ejercido": _num(r.subsidio_ejercido),
        "subsidio_comprometido": _num(r.subsidio_comprometido),
        "recaudado": _num(r.recaudado),
    })
    return d


@stats_bp.route("/resumen", methods=["GET"])
@login_required(roles=ROLES)
def resumen():
    conds, err = _filtros()
    if err:
        return err
    r = db.session.query(*_agregados()).filter(*conds).one()
    data = _fila(r, precio_referencia=Config.REFERENCE_PRICE)
    data["promedio_subsidio_por_entrega"] = _num(r.subsidio_ejercido / r.entregados) if r.entregados else 0
    return jsonify(data), 200


@stats_bp.route("/por-comedor", methods=["GET"])
@login_required(roles=ROLES)
def por_comedor():
    conds, err = _filtros()
    if err:
        return err
    rows = (db.session.query(P.dining_hall, *_agregados()).filter(*conds)
            .group_by(P.dining_hall).order_by(func.count(P.order_id).filter(VALIDO).desc(), P.dining_hall).all())
    return jsonify([_fila(r, comedor=r.dining_hall) for r in rows]), 200


@stats_bp.route("/por-franja", methods=["GET"])
@login_required(roles=ROLES)
def por_franja():
    conds, err = _filtros()
    if err:
        return err
    rows = (db.session.query(P.time_slot, *_agregados()).filter(*conds)
            .group_by(P.time_slot).order_by(P.time_slot).all())
    return jsonify([_fila(r, franja=r.time_slot) for r in rows]), 200


@stats_bp.route("/por-tipo-usuario", methods=["GET"])
@login_required(roles=ROLES)
def por_tipo_usuario():
    conds, err = _filtros()
    if err:
        return err
    rows = (db.session.query(P.user_type, *_agregados()).filter(*conds)
            .group_by(P.user_type).order_by(P.user_type).all())
    return jsonify([_fila(r, tipo_usuario=r.user_type) for r in rows]), 200


@stats_bp.route("/serie-diaria", methods=["GET"])
@login_required(roles=ROLES)
def serie_diaria():
    conds, err = _filtros()
    if err:
        return err
    rows = (db.session.query(P.fecha, *_agregados()).filter(*conds)
            .group_by(P.fecha).order_by(P.fecha).all())
    return jsonify([_fila(r, fecha=r.fecha.isoformat()) for r in rows]), 200
