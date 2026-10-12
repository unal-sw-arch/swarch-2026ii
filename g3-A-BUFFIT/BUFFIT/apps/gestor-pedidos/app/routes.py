from flask import Blueprint, request, jsonify
from app.database import db
from app.models import Order, OrderItem, PRICE_MAP
from app.grpc_client import check_and_reserve_inventory  # Client gRPC

orders_bp = Blueprint("orders", __name__)

@orders_bp.route("/crearOrden", methods=["POST"])
def create_order():
    """
    Crea un nuevo pedido. Primero valida y descuenta inventario vía gRPC.
    Si hay existencias suficientes, asigna tarifa fija por user_type y guarda la orden.
    """
    data = request.get_json() or {}

    user_id = data.get("user_id")
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

    # Determinar tarifa fija por el tipo de usuario (0.0, 3500.0 o 12000.0)
    fixed_price = PRICE_MAP.get(user_type, PRICE_MAP["GENERAL"])

    # Creación y confirmación de la orden en BD Pedidos
    new_order = Order(
        user_id=user_id,
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
    return jsonify(new_order.to_dict()), 201


@orders_bp.route("/", methods=["GET"])
def get_all_orders():
    """Obtiene el historial de todas las órdenes."""
    orders = Order.query.all()
    return jsonify([order.to_dict() for order in orders]), 200


@orders_bp.route("/user/<string:user_id>", methods=["GET"])
def get_user_orders(user_id):
    """Obtiene las órdenes de un usuario específico."""
    orders = Order.query.filter_by(user_id=user_id).all()
    return jsonify([order.to_dict() for order in orders]), 200


@orders_bp.route("/<int:order_id>", methods=["GET"])
def get_order_by_id(order_id):
    """Obtiene el detalle de un pedido por su ID."""
    order = Order.query.get(order_id)
    if not order:
        return jsonify({"error": f"No se encontró el pedido #{order_id}"}), 404
    return jsonify(order.to_dict()), 200


# ====================================================================
# NUEVA RUTA: CANCELACIÓN DE PEDIDO
# ====================================================================
@orders_bp.route("/<int:order_id>/cancel", methods=["PUT", "POST"])
def cancel_order(order_id):
    """
    Cancela un pedido existente cambiando su estado a 'CANCELLED'.
    """
    order = Order.query.get(order_id)
    if not order:
        return jsonify({"error": f"No se encontró el pedido #{order_id}"}), 404

    if order.status == "CANCELLED":
        return jsonify({"message": f"El pedido #{order_id} ya se encuentra cancelado."}), 200

    order.status = "CANCELLED"
    
    try:
        db.session.commit()
        return jsonify({
            "message": f"Pedido #{order_id} cancelado correctamente.",
            "order": order.to_dict()
        }), 200
    except Exception as e:
        db.session.rollback()
        return jsonify({"error": f"Error al cancelar la orden: {str(e)}"}), 500