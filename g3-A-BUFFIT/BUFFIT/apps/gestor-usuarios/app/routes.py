from flask import Blueprint, request, jsonify
from app.database import db
from app.models import User

users_bp = Blueprint("users", __name__)

def get_gateway_headers():
    """Extrae el contexto de usuario inyectado por el API Gateway."""
    user_id = request.headers.get("X-User-Id")
    user_role = request.headers.get("X-User-Role", "Usuario")
    return user_id, user_role


@users_bp.route("/", methods=["POST"])
@users_bp.route("/register", methods=["POST"])
def create_user():
    """Crea un nuevo usuario guardando su contraseña encriptada."""
    data = request.get_json() or {}

    email = data.get("email") or data.get("username")
    name = data.get("name", email)
    password = data.get("password")
    role = data.get("role", "Usuario")
    user_type = data.get("user_type", "ESTUDIANTE").upper()

    if not email or not password:
        return jsonify({"error": "Faltan campos obligatorios: email/username y password."}), 400

    existing_user = User.query.filter_by(email=email).first()
    if existing_user:
        return jsonify({"error": f"El usuario con el correo {email} ya existe."}), 400

    new_user = User(
        name=name,
        email=email,
        role=role,
        user_type=user_type
    )
    # Encriptación de la contraseña
    new_user.set_password(password)

    db.session.add(new_user)
    db.session.commit()

    return jsonify(new_user.to_dict()), 201


@users_bp.route("/login", methods=["POST"])
def verify_credentials():
    """
    Endpoint consumido internamente por AuthService para el Login.
    Valida credenciales sin exponer la contraseña hash al exterior.
    """
    data = request.get_json() or {}
    email = data.get("email") or data.get("username")
    password = data.get("password")

    if not email or not password:
        return jsonify({"error": "Se requieren usuario/correo y contraseña."}), 400

    user = User.query.filter_by(email=email).first()
    if not user or not user.check_password(password):
        return jsonify({"error": "Credenciales inválidas."}), 401

    if not user.is_active:
        return jsonify({"error": "La cuenta del usuario está desactivada."}), 403

    # Retorna la información necesaria para que AuthService arme el Token JWT
    return jsonify({
        "message": "Autenticación exitosa",
        "user": user.to_dict()
    }), 200


@users_bp.route("/<string:user_id>", methods=["GET"])
def get_user_by_id(user_id):
    """Consulta perfil por ID."""
    user = User.query.get(user_id)
    if not user:
        return jsonify({"error": f"Usuario #{user_id} no encontrado."}), 404
    return jsonify(user.to_dict()), 200


@users_bp.route("/toggle/<string:user_id>/<string:action>", methods=["PATCH"])
def toggle_user(user_id, action):
    """Modificar Usuario (Requiere rol Administrador)."""
    _, requester_role = get_gateway_headers()

    if requester_role != "Administrador":
        return jsonify({"error": "Acceso denegado. Se requieren permisos de Administrador."}), 403

    user = User.query.get(user_id)
    data=request.get_json() or {}
    reason = data.get("reason")
    if user:
        user.is_active= False if action =='desactivar' else True
        user.notes_state= '' if action == 'activar' else str(reason)
        try:
            db.session.commit()
            return jsonify({"message" : f"Usuario {action}do con exito"}), 200
        except Exception as e:
            db.session.rollback()
            return jsonify({"error": "Error al actualizar la base de datos."}), 500
    else:
        return jsonify({"error": "Error usuario no encontrado."}),404

@users_bp.route("/delete/<string:user_id>", methods=["DELETE"])
def delete_user(user_id):
    """Eliminar usuario (Requiere rol Administrador)."""
    _, requester_role = get_gateway_headers()

    if requester_role != "Administrador":
        return jsonify({"error": "Acceso denegado. Se requieren permisos de Administrador."}), 403

    user = User.query.get(user_id)
    if user:
        db.session.delete(user)
        try:
            db.session.commit()
            return jsonify({"message" : "Usuario eliminado con exito"}), 200
        except Exception as e:
            db.session.rollback()
            return jsonify({"error": "Error al actualizar la base de datos."}), 500
    else:
        return jsonify({"error": "Error usuario no encontrado."}),404

@users_bp.route("/get", methods=["GET"])
def get_all_users():
    """Listado completo (Requiere rol Administrador)."""
    _, requester_role = get_gateway_headers()

    if requester_role != "Administrador":
        return jsonify({"error": "Acceso denegado. Se requieren permisos de Administrador."}), 403

    users = User.query.all()
    return jsonify([u.to_dict() for u in users]), 200