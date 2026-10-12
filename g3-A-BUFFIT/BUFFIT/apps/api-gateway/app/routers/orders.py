from fastapi import APIRouter, Depends, Request, Response
import httpx
from app.config import settings
from app.auth import verify_jwt_token

router = APIRouter(prefix="/pedidos", tags=["Gestión de Pedidos"])

@router.api_route("/{path:path}", methods=["GET", "POST", "PUT", "DELETE"])
async def proxy_orders(path: str, request: Request, user_payload: dict = Depends(verify_jwt_token)):
    """Reenvía la petición recibida hacia el microservicio de Pedidos."""
    target_url = f"{settings.ORDERS_SERVICE_URL}/pedidos/{path}"
    body = await request.body()
    headers = dict(request.headers)
    headers.pop("host", None)

    async with httpx.AsyncClient() as client:
        response = await client.request(
            method=request.method,
            url=target_url,
            headers=headers,
            params=request.query_params,
            content=body,
            timeout=10.0
        )

    return Response(
        content=response.content,
        status_code=response.status_code,
        headers=dict(response.headers)
    )