import time

from .config import USER_CACHE_TTL_SECONDS
from .schemas import CurrentUser

# Estructura en memoria:
# { firebase_uid: { "data": CurrentUser, "expires_at": marca de tiempo monotónica } }
# Vive en la RAM de cada proceso: con varios workers de uvicorn cada uno tiene el suyo.
_user_cache: dict[str, dict] = {}


def get_cached_user(firebase_uid: str) -> CurrentUser | None:
    """
    Devuelve el usuario desde el caché si existe y no expiró.
    Devuelve None si no existe o si ya expiró el TTL (y lo elimina).
    """
    entry = _user_cache.get(firebase_uid)
    if entry is None:
        return None
    if time.monotonic() > entry["expires_at"]:
        _user_cache.pop(firebase_uid, None)
        return None
    return entry["data"]


def save_user_in_cache(firebase_uid: str, user: CurrentUser, ttl_seconds: int | None = None):
    """Guarda o actualiza el usuario en el caché con TTL."""
    if ttl_seconds is None:
        ttl_seconds = USER_CACHE_TTL_SECONDS
    _user_cache[firebase_uid] = {
        "data": user,
        "expires_at": time.monotonic() + ttl_seconds,
    }


def remove_user_from_cache(firebase_uid: str):
    """Elimina al usuario del caché si existe."""
    _user_cache.pop(firebase_uid, None)


def clear_user_cache():
    """
    Vacía todo el caché de usuarios.
    Se usa cuando se modifica o elimina un usuario a través del gateway: el gateway
    solo conoce al usuario por su firebase_uid, pero la petición identifica al
    usuario afectado por su id en Users, así que vaciar todo es lo más simple y
    siempre correcto. Cada usuario activo se recarga con una sola consulta a Users.
    """
    _user_cache.clear()


def clean_expired_users() -> int:
    """
    Elimina del caché todos los usuarios cuyo TTL ya expiró.
    Devuelve la cantidad de entradas eliminadas.
    """
    now = time.monotonic()
    expired_uids = [
        uid for uid, entry in _user_cache.items()
        if now > entry["expires_at"]
    ]
    for uid in expired_uids:
        _user_cache.pop(uid, None)
    return len(expired_uids)
