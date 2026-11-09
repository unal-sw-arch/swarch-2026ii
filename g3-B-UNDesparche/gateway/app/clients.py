import logging

import httpx

from .config import (
    USERS_SERVICE_URL,
    EVENTS_SERVICE_URL,
    INVENTORY_SERVICE_URL,
    HTTP_TIMEOUT_SECONDS,
)
from .schemas import CurrentUser

logger = logging.getLogger(__name__)

# Cliente HTTP compartido (reutiliza conexiones) para hablar con los microservicios.
_http_client = httpx.AsyncClient(timeout=HTTP_TIMEOUT_SECONDS)


async def close_http_client():
    """Cierra el cliente HTTP compartido. Se llama al apagar el gateway."""
    await _http_client.aclose()


def _build_gateway_headers() -> dict:
    """
    Header que identifica que la petición proviene del gateway.
    Es el único punto donde se define: cuando se agregue un secreto compartido
    entre servicios, el cambio se hace aquí.
    """
    return {"X-From-Gateway": "true"}


def _build_user_headers(user: CurrentUser | None) -> dict:
    """
    Convierte el usuario en headers para los microservicios.
    Si no hay usuario devuelve un dict vacío (petición anónima).
    No se envía el nombre: los headers HTTP solo admiten ASCII y los nombres
    con tildes o eñes harían fallar la petición.
    """
    if user is None:
        return {}

    return {
        "X-User-Id": user.firebase_uid,
        "X-User-Email": user.email,
        "X-User-Role": user.role,
        "X-User-Faculty": user.faculty or "",  # httpx no acepta None como valor
        "X-User-Status": user.status,
    }


class ServiceClient:
    """Cliente HTTP hacia un microservicio concreto (identificado por su URL base)."""

    def __init__(self, base_url: str):
        self._base_url = base_url.rstrip("/")

    async def request(
        self,
        method: str,
        path: str,
        *,
        user: CurrentUser | None = None,
        params: dict | list | None = None,
        json: dict | None = None,
        data: dict | None = None,
        files: dict | None = None,
    ) -> httpx.Response:
        """
        Reenvía una petición al microservicio inyectando los headers del gateway y,
        si hay usuario, los del usuario. user=None -> petición anónima (endpoint público).
        data/files: campos y archivos de un formulario multipart.
        """
        headers = {**_build_gateway_headers(), **_build_user_headers(user)}
        return await _http_client.request(
            method,
            f"{self._base_url}{path}",
            headers=headers,
            params=params,
            json=json,
            data=data,
            files=files,
        )


users_service = ServiceClient(USERS_SERVICE_URL)
events_service = ServiceClient(EVENTS_SERVICE_URL)
inventory_service = ServiceClient(INVENTORY_SERVICE_URL)


async def get_user_by_firebase_uid(firebase_uid: str) -> dict | None:
    """
    Consulta a Users por firebase_uid usando el filterset de su endpoint de listado.
    Devuelve el diccionario del usuario o None si no existe.
    Un listado filtrado nunca responde 404: responde 200 con una lista vacía
    (o {"count": 0, "results": []} si Users tiene paginación activa).
    """
    upstream = await users_service.request(
        "GET", "/users/", params={"firebase_uid": firebase_uid}
    )
    upstream.raise_for_status()
    payload = upstream.json()
    results = payload.get("results", []) if isinstance(payload, dict) else payload

    if len(results) > 1:
        # firebase_uid es único: más de un resultado significa que el filtro no se aplicó.
        logger.error("Users devolvió %d usuarios para un mismo firebase_uid", len(results))
        raise ValueError("Users devolvió más de un usuario para un firebase_uid.")
    return results[0] if results else None


async def create_user_in_users(user_data: dict) -> dict:
    """Pide a Users que cree un usuario. Lanza HTTPStatusError si Users lo rechaza."""
    upstream = await users_service.request("POST", "/users/", json=user_data)
    upstream.raise_for_status()
    return upstream.json()
