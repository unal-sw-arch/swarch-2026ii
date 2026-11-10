from functools import wraps

import jwt
from flask import request, jsonify, g

from app.config import Config


def _decode_token():
    header = request.headers.get("Authorization", "")
    if not header.lower().startswith("bearer "):
        return None, ("Token requerido", 401)
    try:
        payload = jwt.decode(
            header.split(" ", 1)[1].strip(),
            Config.JWT_SECRET_KEY,
            algorithms=[Config.JWT_ALGORITHM],
        )
        return payload, None
    except jwt.ExpiredSignatureError:
        return None, ("El token ha expirado", 401)
    except jwt.InvalidTokenError:
        return None, ("Token inválido", 401)


def login_required(roles=None):
    """Exige un JWT válido (y opcionalmente uno de `roles`). Deja el payload en g.user."""
    def decorator(fn):
        @wraps(fn)
        def wrapper(*args, **kwargs):
            payload, err = _decode_token()
            if err:
                return jsonify({"error": err[0]}), err[1]
            if roles and payload.get("role") not in roles:
                return jsonify({"error": "No tienes permisos para esta acción"}), 403
            g.user = payload
            return fn(*args, **kwargs)
        return wrapper
    return decorator


def is_staff(payload) -> bool:
    return payload.get("role") in Config.STAFF_ROLES
