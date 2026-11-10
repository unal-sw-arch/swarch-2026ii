from fastapi import APIRouter, Depends, Request, Response
import httpx
from app.config import settings
from app.auth import verify_jwt_token

router = APIRouter(prefix="/estadisticas", tags=["Gestor de Estadísticas"])


@router.api_route("/{path:path}", methods=["GET"])
async def proxy_statistics(path: str, request: Request, user_payload: dict = Depends(verify_jwt_token)):
    """Reenvía las consultas (solo lectura) hacia el microservicio de Estadísticas."""
    target_url = f"{settings.STATS_SERVICE_URL}/estadisticas/{path}"
    headers = dict(request.headers)
    headers.pop("host", None)
    headers["X-User-Id"] = str(user_payload.get("sub", ""))
    headers["X-User-Role"] = str(user_payload.get("role", "Usuario"))

    async with httpx.AsyncClient() as client:
        response = await client.request(
            method=request.method,
            url=target_url,
            headers=headers,
            params=request.query_params,
            timeout=10.0
        )

    return Response(
        content=response.content,
        status_code=response.status_code,
        headers=dict(response.headers)
    )
