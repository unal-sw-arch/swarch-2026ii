import logging

import httpx
from fastapi import Depends, HTTPException, status
from fastapi.concurrency import run_in_threadpool
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import ValidationError

from .cache import get_cached_user, save_user_in_cache
from .clients import create_user_in_users, get_user_by_firebase_uid
from .firebase import (
    AuthProviderUnavailableError,
    InvalidTokenError,
    verify_firebase_id_token,
)
from .schemas import CurrentUser

logger = logging.getLogger(__name__)

# RN-01: solo cuentas del dominio institucional
ALLOWED_EMAIL_DOMAIN = "@unal.edu.co"

# auto_error=False: la dependencia no responde sola cuando falta el token,
# así podemos distinguir "endpoint público con usuario opcional" de "token obligatorio".
_bearer_scheme = HTTPBearer(auto_error=False)


def _unauthorized(detail: str) -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail=detail,
        headers={"WWW-Authenticate": "Bearer"},
    )


async def _load_or_create_user(firebase_uid: str, email: str, name: str) -> CurrentUser:
    """
    Obtiene el usuario desde Users; si no existe lo crea (esto une login y registro:
    con un solo botón de Google, la primera petición autenticada registra al usuario).
    """
    payload = await get_user_by_firebase_uid(firebase_uid)

    if payload is None:
        # Solo la identidad: Users fija por su cuenta rol, estado y facultad iniciales
        user_data = {
            "firebase_uid": firebase_uid,
            "email": email,
            "name": name,
        }
        try:
            payload = await create_user_in_users(user_data)
        except httpx.HTTPStatusError as exc:
            if exc.response.status_code not in (400, 409):
                raise
            # Al primer login el front dispara varias peticiones a la vez: si otra
            # ya creó al usuario, Users rechaza el duplicado y basta con volver a leerlo.
            payload = await get_user_by_firebase_uid(firebase_uid)
            if payload is None:
                raise

    try:
        # La identidad (uid y correo) sale del token verificado; el resto, de Users.
        return CurrentUser.model_validate(
            {**payload, "firebase_uid": firebase_uid, "email": email}
        )
    except ValidationError as exc:
        logger.error("Users devolvió un usuario con formato inesperado: %s", exc)
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Users Service returned an unexpected user format.",
        )


async def _authenticate(id_token: str) -> CurrentUser:
    """
    Flujo de autenticación:
    1. Verifica el token con Firebase (solo aquí, en el gateway).
    2. Valida que el correo sea @unal.edu.co y esté verificado.
    3. Consulta el caché TTL; si no existe o expiró, consulta/crea en Users.
    4. Guarda el usuario en el caché.
    5. Rechaza con 403 a las cuentas bloqueadas (is_active=False) y devuelve al usuario.
    """
    try:
        # verify_id_token es síncrona; en un hilo aparte no bloquea el event loop.
        decoded_token = await run_in_threadpool(verify_firebase_id_token, id_token)
    except InvalidTokenError:
        raise _unauthorized("Firebase token invalid or expired.")
    except AuthProviderUnavailableError:
        logger.exception("No se pudo validar el token: Firebase no disponible")
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Authentication provider unavailable.",
        )

    email = (decoded_token.get("email") or "").lower()
    if not email.endswith(ALLOWED_EMAIL_DOMAIN):
        raise _unauthorized("Email must belong to @unal.edu.co domain.")

    if not decoded_token.get("email_verified"):
        raise _unauthorized("Email not verified.")

    firebase_uid = decoded_token.get("uid")
    if not firebase_uid:
        raise _unauthorized("Firebase token without user identifier.")

    user = get_cached_user(firebase_uid)
    if user is None:
        user = await _load_or_create_user(
            firebase_uid, email, decoded_token.get("name") or ""
        )
        save_user_in_cache(firebase_uid, user)

    # Se revisa también con el usuario que viene del caché: un bloqueo hecho por el
    # gateway vacía el caché y rige de inmediato; uno hecho por otra vía, al expirar el TTL.
    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Account blocked.",
        )

    return user


async def get_optional_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer_scheme),
) -> CurrentUser | None:
    """
    Para endpoints que funcionan con o sin sesión (listados públicos, suscripción a eventos).
    Sin header Authorization devuelve None; con un token inválido responde 401
    (un token malo nunca se trata como si fuera un usuario anónimo).
    """
    if credentials is None:
        return None
    return await _authenticate(credentials.credentials)


async def get_current_user(
    user: CurrentUser | None = Depends(get_optional_user),
) -> CurrentUser:
    """Para endpoints que exigen sesión: igual que la opcional, pero sin token responde 401."""
    if user is None:
        raise _unauthorized("Authentication token not provided.")
    return user
