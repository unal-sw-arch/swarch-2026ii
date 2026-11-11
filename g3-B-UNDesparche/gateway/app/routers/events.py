from fastapi import APIRouter, Depends, Request

from ..clients import events_service
from ..deps import get_current_user, get_optional_user
from ..http_utils import extract_payload, forward_query_params, relay
from ..schemas import CurrentUser

router = APIRouter()


@router.get("/")
async def list_events(
    request: Request,
    current_user: CurrentUser | None = Depends(get_optional_user),
):
    """
    Proxy a Events Service: listar eventos (acepta filtros y orden por query params).
    Endpoint público: sin token se ven solo los publicados; con token, Events puede
    incluir además los borradores del organizador.
    """
    upstream = await events_service.request(
        "GET", "/events/", user=current_user, params=forward_query_params(request)
    )
    return relay(upstream)


@router.get("/{event_id}/")
async def retrieve_event(
    event_id: str,
    current_user: CurrentUser | None = Depends(get_optional_user),
):
    """
    Proxy a Events Service: obtener un evento por ID.
    Endpoint público (con token, el organizador puede ver su borrador).
    """
    upstream = await events_service.request(
        "GET", f"/events/{event_id}/", user=current_user
    )
    return relay(upstream)


@router.post("/")
async def create_event(
    request: Request,
    current_user: CurrentUser = Depends(get_current_user),
):
    """
    Proxy a Events Service: crear evento. Requiere autenticación.
    Soporta multipart/form-data (imágenes) y JSON.
    """
    payload = await extract_payload(request)
    upstream = await events_service.request(
        "POST", "/events/", user=current_user, **payload
    )
    return relay(upstream)


@router.patch("/{event_id}/")
async def update_event(
    request: Request,
    event_id: str,
    current_user: CurrentUser = Depends(get_current_user),
):
    """
    Proxy a Events Service: actualizar evento. Requiere autenticación.
    Soporta multipart/form-data (imágenes) y JSON.
    """
    payload = await extract_payload(request)
    upstream = await events_service.request(
        "PATCH", f"/events/{event_id}/", user=current_user, **payload
    )
    return relay(upstream)


@router.delete("/{event_id}/")
async def delete_event(
    event_id: str,
    current_user: CurrentUser = Depends(get_current_user),
):
    """Proxy a Events Service: eliminar evento. Requiere autenticación."""
    upstream = await events_service.request(
        "DELETE", f"/events/{event_id}/", user=current_user
    )
    return relay(upstream)


@router.post("/{event_id}/publish/")
async def publish_event(
    event_id: str,
    current_user: CurrentUser = Depends(get_current_user),
):
    """Proxy a Events Service: publicar evento. Requiere autenticación."""
    upstream = await events_service.request(
        "POST", f"/events/{event_id}/publish/", user=current_user
    )
    return relay(upstream)


@router.post("/{event_id}/subscribe/")
async def subscribe_event(
    event_id: str,
    body: dict | None = None,
    current_user: CurrentUser | None = Depends(get_optional_user),
):
    """
    Proxy a Events Service: suscribirse a un evento.
    Autenticación opcional (RF-7): un externo se suscribe solo con su correo en el body.
    """
    upstream = await events_service.request(
        "POST", f"/events/{event_id}/subscribe/", user=current_user, json=body
    )
    return relay(upstream)


@router.post("/{event_id}/unsubscribe/")
async def unsubscribe_event(
    event_id: str,
    body: dict | None = None,
    current_user: CurrentUser | None = Depends(get_optional_user),
):
    """
    Proxy a Events Service: desuscribirse de un evento.
    Autenticación opcional, igual que subscribe.
    """
    upstream = await events_service.request(
        "POST", f"/events/{event_id}/unsubscribe/", user=current_user, json=body
    )
    return relay(upstream)


@router.post("/unsubscribe/")
async def unsubscribe_via_token(body: dict | None = None):
    """Proxy a Events Service: desuscripción pública mediante token."""
    upstream = await events_service.request(
        "POST", "/events/unsubscribe/", json=body
    )
    return relay(upstream)
