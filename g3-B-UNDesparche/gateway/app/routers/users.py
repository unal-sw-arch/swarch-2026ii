from fastapi import APIRouter, Depends, Request

from ..cache import clear_user_cache
from ..clients import users_service
from ..deps import get_current_user
from ..http_utils import forward_query_params, relay
from ..schemas import CurrentUser

router = APIRouter()


@router.get("/me/", response_model=CurrentUser)
async def users_me(current_user: CurrentUser = Depends(get_current_user)):
    """
    Devuelve los datos del usuario autenticado. Sirve como inicio de sesión y registro:
    get_current_user verifica el token, consulta el caché y, si hace falta, consulta
    o crea al usuario en Users. No hace proxy adicional.
    """
    return current_user


@router.get("/")
async def list_users(
    request: Request, current_user: CurrentUser = Depends(get_current_user)
):
    """
    Proxy a Users Service: listar usuarios (acepta filtros y búsqueda por query params).
    Inyecta headers de usuario para permisos internos de Users.
    """
    upstream = await users_service.request(
        "GET", "/users/", user=current_user, params=forward_query_params(request)
    )
    return relay(upstream)


@router.get("/{user_id}/")
async def retrieve_user(user_id: str, current_user: CurrentUser = Depends(get_current_user)):
    """Proxy a Users Service: obtener un usuario por ID."""
    upstream = await users_service.request("GET", f"/users/{user_id}/", user=current_user)
    return relay(upstream)


@router.patch("/{user_id}/")
async def update_user(
    user_id: str,
    body: dict | None = None,
    current_user: CurrentUser = Depends(get_current_user),
):
    """
    Proxy a Users Service: actualizar un usuario (rol, estado, etc.).
    Si el cambio se aplicó, se vacía el caché para que el nuevo rol/estado rija ya.
    """
    upstream = await users_service.request(
        "PATCH", f"/users/{user_id}/", user=current_user, json=body
    )
    if upstream.is_success:
        clear_user_cache()
    return relay(upstream)


@router.delete("/{user_id}/")
async def delete_user(user_id: str, current_user: CurrentUser = Depends(get_current_user)):
    """Proxy a Users Service: eliminar un usuario. Si se eliminó, se vacía el caché."""
    upstream = await users_service.request("DELETE", f"/users/{user_id}/", user=current_user)
    if upstream.is_success:
        clear_user_cache()
    return relay(upstream)
