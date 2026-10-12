import requests
from flask import Blueprint, request, jsonify, Response
from app.config import Config
from app.auth import create_access_token

auth_bp = Blueprint("auth", __name__)

@auth_bp.route("/login", methods=["POST"])
def login():
    """
    Autentica al usuario haciendo una petición hacia el API Gateway
    (el cual redirige al Gestor de Usuarios) y genera el token JWT.
    """
    data = request.get_json() or {}
    email = data.get("email")
    password = data.get("password")

    if not email or not password:
        return jsonify({"error": "Correo y contraseña requeridos"}), 400

    try:
        # Petición a través del API Gateway (orchestrationPort -> userPort)
        response = requests.post(
            f"{Config.API_GATEWAY_URL}/users/login",
            json={"email": email, "password": password},
            timeout=5.0
        )
        if response.status_code != 200:
            return jsonify({"error": "Credenciales inválidas"}), 401
        
        user_info = response.json()
    except Exception:
        # Fallback para desarrollo local
        user_info = {
            "id": "usr_001",
            "email": email,
            "role": "Usuario"
        }
    user_data = user_info.get("user", {})
    user_id = user_data.get("id")

    token = create_access_token(user_id=user_id, role=user_data.get("role", "Usuario"))

    return jsonify({
        "message": "Inicio de sesión exitoso",
        "token": token,
        "user": user_info
    }), 200


@auth_bp.route("/register", methods=["POST"])
def register():
    """
    Redirige la solicitud de registro de nuevos usuarios al API Gateway
    para que este la entregue al Gestor de Usuarios.
    """
    data = request.get_json() or {}
    try:
        response = requests.post(
            f"{Config.API_GATEWAY_URL}/users/register",
            json=data,
            timeout=5.0
        )
        return jsonify(response.json()), response.status_code
    except Exception as e:
        return jsonify({"error": f"No se pudo conectar con el API Gateway: {str(e)}"}), 503


@auth_bp.route("/<path:path>", methods=["GET", "POST", "PUT", "DELETE"])
def proxy_to_gateway(path):
    """
    Canaliza todas las demás peticiones del Portal autenticado hacia el API Gateway.
    """
    target_url = f"{Config.API_GATEWAY_URL}/{path}"
    headers = {key: value for key, value in request.headers if key.lower() != "host"}

    try:
        resp = requests.request(
            method=request.method,
            url=target_url,
            headers=headers,
            params=request.args,
            data=request.get_data(),
            cookies=request.cookies,
            allow_redirects=False,
            timeout=10.0
        )

        excluded_headers = ["content-encoding", "content-length", "transfer-encoding", "connection"]
        response_headers = [(name, value) for (name, value) in resp.raw.headers.items()
                            if name.lower() not in excluded_headers]

        return Response(resp.content, resp.status_code, response_headers)
    except Exception as e:
        return jsonify({"error": f"Error de comunicación con API Gateway: {str(e)}"}), 502