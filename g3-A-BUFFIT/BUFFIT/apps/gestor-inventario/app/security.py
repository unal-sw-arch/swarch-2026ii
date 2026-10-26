import jwt
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.config import Config

_bearer = HTTPBearer(auto_error=False)


def require_write_role(credentials: HTTPAuthorizationCredentials = Depends(_bearer)) -> dict:
    """Solo Comedor / Administrador pueden modificar inventario (JWT emitido por el AuthService)."""
    if not Config.ENFORCE_ROLES:
        return {}
    if credentials is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Token requerido")
    try:
        payload = jwt.decode(credentials.credentials, Config.JWT_SECRET_KEY, algorithms=[Config.JWT_ALGORITHM])
    except jwt.InvalidTokenError:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Token inválido o expirado")
    if payload.get("role") not in Config.WRITE_ROLES:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "No tienes permisos para modificar el inventario")
    return payload
