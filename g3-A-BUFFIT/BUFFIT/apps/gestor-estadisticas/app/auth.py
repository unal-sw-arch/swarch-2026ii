from functools import wraps

import jwt
from flask import request, jsonify, g

from app.config import Config


def login_required(roles=None):
    """Exige un JWT valido (emitido por el api-gateway) y, opcionalmente, uno de `roles`."""
    def decorator(fn):
        @wraps(fn)
        def wrapper(*args, **kwargs):
            header = request.headers.get("Authorization", "")
            if not header.lower().startswith("bearer "):
                return jsonify({"error": "Token requerido"}), 401
            try:
                payload = jwt.decode(header.split(" ", 1)[1].strip(), Config.JWT_SECRET_KEY,
                                     algorithms=[Config.JWT_ALGORITHM])
            except jwt.ExpiredSignatureError:
                return jsonify({"error": "El token ha expirado"}), 401
            except jwt.InvalidTokenError:
                return jsonify({"error": "Token inválido"}), 401
            if roles and payload.get("role") not in roles:
                return jsonify({"error": "No tienes permisos para esta acción"}), 403
            g.user = payload
            return fn(*args, **kwargs)
        return wrapper
    return decorator
