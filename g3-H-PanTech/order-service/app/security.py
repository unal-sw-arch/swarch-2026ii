"""Validación de JWT RS256 (JWKS de identity-service) y autorización por permiso.

Order Service NO emite tokens: solo los valida. Claims esperados: `sub`, `email`,
`roles`, `permissions`, `level`, `iss`, `iat`, `exp`.
"""
import hmac
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

import jwt
from fastapi import Depends, Header, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.config import settings

_bearer = HTTPBearer(auto_error=False)


@dataclass(frozen=True)
class Principal:
    user_id: str
    roles: frozenset[str]
    permissions: frozenset[str]
    level: int

    def has(self, permission: str) -> bool:
        return permission in self.permissions


def _unauthorized(detail: str) -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail=detail,
        headers={"WWW-Authenticate": "Bearer"},
    )


@lru_cache
def _jwk_client() -> jwt.PyJWKClient:
    # Cachea las claves públicas; se refresca si llega un `kid` desconocido.
    return jwt.PyJWKClient(settings.jwks_url, cache_keys=True, lifespan=300, timeout=5)


@lru_cache
def _public_key_from_file(path: str) -> str:
    return Path(path).read_text(encoding="utf-8")


def _signing_key(token: str):
    if settings.jwt_public_key:
        return settings.jwt_public_key
    if settings.jwt_public_key_file:
        return _public_key_from_file(settings.jwt_public_key_file)
    return _jwk_client().get_signing_key_from_jwt(token).key


def get_current_principal(
    creds: HTTPAuthorizationCredentials | None = Depends(_bearer),
) -> Principal:
    if creds is None:
        raise _unauthorized("Falta el token de autenticación")
    try:
        key = _signing_key(creds.credentials)
        payload = jwt.decode(
            creds.credentials,
            key,
            algorithms=[settings.jwt_algorithm],
            issuer=settings.jwt_issuer or None,
            audience=settings.jwt_audience or None,
            leeway=10,
            # Con `aud` en el token, PyJWT lo rechaza si no se indica audiencia: se desactiva solo si está vacía.
            options={"require": ["sub", "exp"], "verify_aud": bool(settings.jwt_audience)},
        )
    except jwt.PyJWKClientConnectionError:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "No se pudo obtener la clave de Identity")
    except jwt.PyJWTError:
        raise _unauthorized("Token inválido o expirado")

    roles = payload.get("roles") or []
    permissions = payload.get("permissions") or []
    if not isinstance(roles, list) or not isinstance(permissions, list):
        raise _unauthorized("Token mal formado")

    return Principal(
        user_id=str(payload["sub"]),
        roles=frozenset(map(str, roles)),
        permissions=frozenset(map(str, permissions)),
        level=int(payload.get("level") or 0),
    )


def require_permission(*codes: str):
    """Dependencia que exige AL MENOS UNO de los permisos indicados.

    Devuelve el `Principal` para que el endpoint valide además el alcance (propio/todos).
    """

    def dependency(principal: Principal = Depends(get_current_principal)) -> Principal:
        if not any(principal.has(c) for c in codes):
            raise HTTPException(status.HTTP_403_FORBIDDEN, "No tienes permiso para esta operación")
        return principal

    return dependency


def require_service_token(x_service_token: str | None = Header(default=None)) -> None:
    """Autenticación servicio a servicio (Worker → Order Service)."""
    if not x_service_token or not hmac.compare_digest(x_service_token, settings.service_token):
        raise _unauthorized("Service token inválido")
