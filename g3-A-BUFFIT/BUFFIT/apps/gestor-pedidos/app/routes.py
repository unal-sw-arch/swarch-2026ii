from datetime import datetime, timedelta

from flask import Blueprint, request, jsonify, g
from sqlalchemy import func

from app.auth import login_required, is_staff
from app.config import Config
from app.database import db
from app.events import publish_order_event
from app.grpc_client import check_and_reserve_inventory  # Client gRPC
from app.inventory_client import restock_inventory
from app.models import Order, OrderItem, PRICE_MAP
from app.utils import (BOGOTA, day_bounds_utc, now_bogota, slot_start_minutes,
                       to_bogota)

orders_bp = Blueprint("orders", __name__)

STAFF = Config.STAFF_ROLES


def _owns(order_user_id) -> bool:
    return str(g.user.get("sub")) == str(order_user_id)


@orders_bp.route("/crearOrden", methods=["POST"])
@login_required()
def create_order():
    """
    Crea un nuevo pedido. Primero valida y descuenta inventario vía gRPC.
    Si hay existencias suficientes, asigna tarifa fija por user_type y guarda la orden.
    """
    data = request.get_json() or {}

    # Un consumidor solo puede pedir a su nombre: el id sale del token
    user_id = data.get("user_id") if is_staff(g.user) else g.user.get("sub")
    user_type = data.get("user_type", "ESTUDIANTE").upper() # APOYO_ALIMENTARIO, ESTUDIANTE, GENERAL
    dining_hall = data.get("dining_hall")
    pickup_time_slot = data.get("pickup_time_slot")
    items_data = data.get("items", [])

    if not user_id or not dining_hall or not pickup_time_slot or not items_data:
        return jsonify({
            "error": "Datos incompletos. Se requieren user_id, dining_hall, pickup_time_slot e items."
        }), 400

    # ------------------------------------------------------------------
    # VALIDACIÓN Y DESCUENTO DE INVENTARIO VÍA gRPC
    # ------------------------------------------------------------------
    has_stock, stock_message = check_and_reserve_inventory(dining_hall, items_data)

    if not has_stock:
        # Rechaza la orden si no hay inventario suficiente en ese comedor
        return jsonify({
            "error": "No se pudo procesar la orden debido a insuficiencia de inventario.",
            "detail": stock_message
        }), 400

    # Determinar tarifa fija por el tipo de usuario (0.0, 8400.0 o 16500.0)
    fixed_price = PRICE_MAP.get(user_type, PRICE_MAP["GENERAL"])

    # Creación y confirmación de la orden en BD Pedidos
    new_order = Order(
        user_id=str(user_id),
        user_type=user_type,
        dining_hall=dining_hall,
        pickup_time_slot=pickup_time_slot,
        total_price=fixed_price,
        status="CONFIRMED"
    )

    db.session.add(new_order)
    db.session.commit()

    # Guardar los ítems asociados al pedido
    for item in items_data:
        order_item = OrderItem(
            order_id=new_order.id,
            product_name=item.get("product_name") or item.get("name"),
            category=item.get("category") or item.get("item_type"),
            quantity=item.get("quantity", 1)
        )
        db.session.add(order_item)

    db.session.commit()
    publish_order_event(new_order, "pedido.creado")
    return jsonify(new_order.to_dict()), 201


@orders_bp.route("/", methods=["GET"])
@login_required(roles=STAFF)
def get_all_orders():
    """Historial de todas las órdenes (solo personal del comedor / administrador)."""
    orders = Order.query.order_by(Order.id.desc()).all()
    return jsonify([order.to_dict() for order in orders]), 200


@orders_bp.route("/user/<string:user_id>", methods=["GET"])
@login_required()
def get_user_orders(user_id):
    """Órdenes de un usuario: el propio consumidor o el personal."""
    if not (_owns(user_id) or is_staff(g.user)):
        return jsonify({"error": "No puedes ver los pedidos de otro usuario"}), 403
    orders = Order.query.filter_by(user_id=str(user_id)).order_by(Order.id.desc()).all()
    return jsonify([order.to_dict() for order in orders]), 200


# ====================================================================
# ROL PERSONAL DEL COMEDOR
# ====================================================================
@orders_bp.route("/comedor", methods=["GET"])
@login_required(roles=STAFF)
def get_dining_hall_orders():
    """
    Órdenes de un comedor para un día (por defecto hoy, hora de Bogotá),
    ordenadas por cercanía de la franja de recogida.
    Query: dining_hall (obligatorio), status (CONFIRMED|DELIVERED|CANCELLED), fecha (YYYY-MM-DD).
    """
    dining_hall = (request.args.get("dining_hall") or "").strip()
    if not dining_hall:
        return jsonify({"error": "Se requiere dining_hall"}), 400

    fecha = request.args.get("fecha")
    try:
        day = datetime.strptime(fecha, "%Y-%m-%d").date() if fecha else now_bogota().date()
    except ValueError:
        return jsonify({"error": "fecha debe tener formato YYYY-MM-DD"}), 400
    start, end = day_bounds_utc(day)

    query = Order.query.filter(
        func.lower(Order.dining_hall) == dining_hall.lower(),
        Order.created_at >= start,
        Order.created_at < end,
    )
    status = request.args.get("status")
    if status:
        query = query.filter(Order.status == status.upper())

    orders = query.all()
    # Primero las más próximas en el tiempo; las franjas ilegibles al final
    orders.sort(key=lambda o: (slot_start_minutes(o.pickup_time_slot) is None,
                               slot_start_minutes(o.pickup_time_slot) or 0, o.id))
    return jsonify([o.to_dict() for o in orders]), 200


@orders_bp.route("/<int:order_id>/deliver", methods=["PUT", "POST"])
@login_required(roles=STAFF)
def deliver_order(order_id):
    """Marca un pedido como entregado. Si se envía dining_hall, debe ser el del pedido."""
    order = db.session.get(Order, order_id)
    if not order:
        return jsonify({"error": f"No se encontró el pedido #{order_id}"}), 404

    body = request.get_json(silent=True) or {}
    hall = (body.get("dining_hall") or request.args.get("dining_hall") or "").strip()
    if hall and hall.lower() != order.dining_hall.lower():
        return jsonify({"error": f"El pedido #{order_id} es de {order.dining_hall}, no de {hall}"}), 403

    if order.status == "DELIVERED":
        return jsonify({"message": f"El pedido #{order_id} ya estaba entregado.", "order": order.to_dict()}), 200
    if order.status != "CONFIRMED":
        return jsonify({"error": f"No se puede entregar un pedido en estado {order.status}"}), 409

    order.status = "DELIVERED"
    db.session.commit()
    publish_order_event(order, "pedido.entregado")
    return jsonify({"message": f"Pedido #{order_id} entregado.", "order": order.to_dict()}), 200


# ====================================================================
# DETALLE Y CANCELACIÓN
# ====================================================================
@orders_bp.route("/<int:order_id>", methods=["GET"])
@login_required()
def get_order_by_id(order_id):
    """Detalle de un pedido: su dueño o el personal."""
    order = db.session.get(Order, order_id)
    if not order:
        return jsonify({"error": f"No se encontró el pedido #{order_id}"}), 404
    if not (_owns(order.user_id) or is_staff(g.user)):
        return jsonify({"error": "No puedes ver el pedido de otro usuario"}), 403
    return jsonify(order.to_dict()), 200


def _minutes_until_pickup(order):
    """Minutos que faltan para el inicio de la franja (negativo si ya pasó); None si no se puede calcular."""
    start_min = slot_start_minutes(order.pickup_time_slot)
    if start_min is None or order.created_at is None:
        return None
    day = to_bogota(order.created_at).date()
    pickup = datetime(day.year, day.month, day.day, tzinfo=BOGOTA) + timedelta(minutes=start_min)
    return (pickup - now_bogota()).total_seconds() / 60


@orders_bp.route("/<int:order_id>/cancel", methods=["PUT", "POST"])
@login_required()
def cancel_order(order_id):
    """
    Cancela un pedido (CONFIRMED -> CANCELLED) y devuelve las porciones al inventario.
    El consumidor solo puede cancelar los suyos y hasta CANCEL_MIN_MINUTES antes de la franja.
    """
    order = db.session.get(Order, order_id)
    if not order:
        return jsonify({"error": f"No se encontró el pedido #{order_id}"}), 404

    staff = is_staff(g.user)
    if not (_owns(order.user_id) or staff):
        return jsonify({"error": "No puedes cancelar el pedido de otro usuario"}), 403

    if order.status == "CANCELLED":
        return jsonify({"message": f"El pedido #{order_id} ya se encuentra cancelado."}), 200
    if order.status == "DELIVERED":
        return jsonify({"error": "No se puede cancelar un pedido ya entregado."}), 409

    if not staff:
        remaining = _minutes_until_pickup(order)
        if remaining is not None and remaining < Config.CANCEL_MIN_MINUTES:
            return jsonify({
                "error": f"Solo puedes cancelar hasta {Config.CANCEL_MIN_MINUTES} minutos antes de la franja de recogida."
            }), 409

    # Primero se devuelve el stock: si falla, el pedido sigue confirmado y se puede reintentar
    ok, detail = restock_inventory(order.dining_hall, order.items)
    if not ok:
        return jsonify({"error": "No se pudo devolver el inventario; el pedido no fue cancelado.",
                        "detail": detail}), 502

    order.status = "CANCELLED"
    try:
        db.session.commit()
    except Exception as e:
        db.session.rollback()
        return jsonify({"error": f"Error al cancelar la orden: {str(e)}"}), 500
    publish_order_event(order, "pedido.cancelado")
    return jsonify({
        "message": f"Pedido #{order_id} cancelado correctamente.",
        "order": order.to_dict()
    }), 200
