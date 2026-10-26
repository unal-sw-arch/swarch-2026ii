from fastapi import APIRouter, Depends, Request, Response
import httpx
from app.config import settings
from app.auth import verify_jwt_token


router = APIRouter(prefix="/users", tags=["Gestor de Usuarios"])

# =====================================================================
# 1. RUTAS PÚBLICAS GESTIÓN DE USUARIOS(Sin token JWT)
# =====================================================================

@router.post("/login")
async def login_proxy(request: Request):
    """Punto de entrada público para inicio de sesión."""
    target_url = f"{settings.USERS_SERVICE_URL}/users/login"
    body = await request.body()
    headers = dict(request.headers)
    headers.pop("host", None)

    async with httpx.AsyncClient() as client:
        response = await client.post(
            target_url,
            content=body,
            headers=headers,
            timeout=10.0
        )

    return Response(
        content=response.content,
        status_code=response.status_code,
        headers=dict(response.headers)
    )


@router.post("/register")
async def register_proxy(request: Request):
    """
    Punto de entrada público para registro/creación de usuarios.
    Reenvía los datos sin exigir cabeceras de autorización previo.
    """
    target_url = f"{settings.USERS_SERVICE_URL}/users/register"  # O settings.USERS_SERVICE_URL/users si el registro vive en el gestor
    body = await request.body()
    headers = dict(request.headers)
    headers.pop("host", None)

    async with httpx.AsyncClient() as client:
        response = await client.post(
            target_url,
            content=body,
            headers=headers,
            timeout=10.0
        )

    return Response(
        content=response.content,
        status_code=response.status_code,
        headers=dict(response.headers)
    )

# =====================================================================
# 2. RUTAS PROTEGIDAS GESTIÓN DE USUARIOS: Todo el resto bajo /users/{path}
#    Requiere token JWT y reenvía al Gestor de Usuarios
# =====================================================================
@router.api_route("/{path:path}", methods=["GET", "POST", "PUT", "DELETE"])
async def proxy_users(
    path: str, 
    request: Request, 
    user_payload: dict = Depends(verify_jwt_token)
):
    """Reenvía peticiones protegidas al Gestor de Usuarios inyectando contexto (X-User-Id, X-User-Role)."""
    target_url = f"{settings.USERS_SERVICE_URL}/{path}"
    body = await request.body()
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
            content=body,
            timeout=10.0
        )

    return Response(
        content=response.content,
        status_code=response.status_code,
        headers=dict(response.headers)
    )