from fastapi import APIRouter, Depends, Request

from ..clients import inventory_service
from ..deps import get_current_user, get_optional_user
from ..http_utils import extract_payload, forward_query_params, relay
from ..schemas import CurrentUser

router = APIRouter()


# --- Implementos ---

@router.get("/implements/")
async def list_implements(
    request: Request,
    current_user: CurrentUser | None = Depends(get_optional_user),
):
    """
    Proxy a Inventory Service: listar implementos (acepta filtros por query params).
    Endpoint público; si hay token se envía la identidad del usuario.
    """
    upstream = await inventory_service.request(
        "GET", "/implements/", user=current_user, params=forward_query_params(request)
    )
    return relay(upstream)


@router.get("/implements/{implement_id}/")
async def retrieve_implement(
    implement_id: str,
    current_user: CurrentUser | None = Depends(get_optional_user),
):
    """Proxy a Inventory Service: obtener un implemento por ID. Endpoint público."""
    upstream = await inventory_service.request(
        "GET", f"/implements/{implement_id}/", user=current_user
    )
    return relay(upstream)


@router.post("/implements/")
async def create_implement(
    request: Request,
    current_user: CurrentUser = Depends(get_current_user),
):
    """
    Proxy a Inventory Service: crear implemento. Requiere autenticación.
    Soporta multipart/form-data (imágenes) y JSON.
    """
    payload = await extract_payload(request)
    upstream = await inventory_service.request(
        "POST", "/implements/", user=current_user, **payload
    )
    return relay(upstream)


@router.patch("/implements/{implement_id}/")
async def update_implement(
    request: Request,
    implement_id: str,
    current_user: CurrentUser = Depends(get_current_user),
):
    """
    Proxy a Inventory Service: actualizar implemento. Requiere autenticación.
    Soporta multipart/form-data (imágenes) y JSON.
    """
    payload = await extract_payload(request)
    upstream = await inventory_service.request(
        "PATCH", f"/implements/{implement_id}/", user=current_user, **payload
    )
    return relay(upstream)


@router.delete("/implements/{implement_id}/")
async def delete_implement(
    implement_id: str,
    current_user: CurrentUser = Depends(get_current_user),
):
    """Proxy a Inventory Service: eliminar implemento. Requiere autenticación."""
    upstream = await inventory_service.request(
        "DELETE", f"/implements/{implement_id}/", user=current_user
    )
    return relay(upstream)


# --- Reservas ---

@router.get("/reserves/")
async def list_reserves(
    request: Request,
    current_user: CurrentUser = Depends(get_current_user),
):
    """Proxy a Inventory Service: listar reservas. Requiere autenticación."""
    upstream = await inventory_service.request(
        "GET", "/reserves/", user=current_user, params=forward_query_params(request)
    )
    return relay(upstream)


@router.get("/reserves/{reserve_id}/")
async def retrieve_reserve(
    reserve_id: str,
    current_user: CurrentUser = Depends(get_current_user),
):
    """Proxy a Inventory Service: obtener una reserva por ID. Requiere autenticación."""
    upstream = await inventory_service.request(
        "GET", f"/reserves/{reserve_id}/", user=current_user
    )
    return relay(upstream)


@router.post("/reserves/")
async def create_reserve(
    body: dict | None = None,
    current_user: CurrentUser = Depends(get_current_user),
):
    """Proxy a Inventory Service: crear reserva. Requiere autenticación."""
    upstream = await inventory_service.request(
        "POST", "/reserves/", user=current_user, json=body
    )
    return relay(upstream)


@router.post("/reserves/{reserve_id}/confirm/")
async def confirm_reserve(
    reserve_id: str,
    current_user: CurrentUser = Depends(get_current_user),
):
    """Proxy a Inventory Service: confirmar una reserva. Requiere autenticación."""
    upstream = await inventory_service.request(
        "POST", f"/reserves/{reserve_id}/confirm/", user=current_user
    )
    return relay(upstream)


@router.post("/reserves/{reserve_id}/cancel/")
async def cancel_reserve(
    reserve_id: str,
    current_user: CurrentUser = Depends(get_current_user),
):
    """Proxy a Inventory Service: cancelar una reserva. Requiere autenticación."""
    upstream = await inventory_service.request(
        "POST", f"/reserves/{reserve_id}/cancel/", user=current_user
    )
    return relay(upstream)


# --- Préstamos ---

@router.get("/borrowings/")
async def list_borrowings(
    request: Request,
    current_user: CurrentUser = Depends(get_current_user),
):
    """Proxy a Inventory Service: listar préstamos. Requiere autenticación."""
    upstream = await inventory_service.request(
        "GET", "/borrowings/", user=current_user, params=forward_query_params(request)
    )
    return relay(upstream)


@router.get("/borrowings/{borrowing_id}/")
async def retrieve_borrowing(
    borrowing_id: str,
    current_user: CurrentUser = Depends(get_current_user),
):
    """Proxy a Inventory Service: obtener un préstamo por ID. Requiere autenticación."""
    upstream = await inventory_service.request(
        "GET", f"/borrowings/{borrowing_id}/", user=current_user
    )
    return relay(upstream)


@router.post("/borrowings/{borrowing_id}/return/")
async def return_implement(
    borrowing_id: str,
    current_user: CurrentUser = Depends(get_current_user),
):
    """Proxy a Inventory Service: devolver un implemento prestado. Requiere autenticación."""
    upstream = await inventory_service.request(
        "POST", f"/borrowings/{borrowing_id}/return/", user=current_user
    )
    return relay(upstream)
