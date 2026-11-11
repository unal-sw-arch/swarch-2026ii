from fastapi import APIRouter, Depends, Request, Response, HTTPException, status
import httpx
from app.config import settings
from app.auth import verify_jwt_token, create_access_token, create_refresh_token, decode_raw_token

router = APIRouter(prefix="/users", tags=["Gestor de Usuarios"])

# =====================================================================
# 1. RUTAS PÚBLICAS DE AUTENTICACIÓN Y REGISTRO
# =====================================================================

@router.post("/login")
async def login(request: Request):
    """
    Verifica credenciales en Gestor de Usuarios y emite el JWT directamente.
    """
    body = await request.body()
    headers = dict(request.headers)
    headers.pop("host", None)

    target_url = f"{settings.USERS_SERVICE_URL}/users/login"
    
    async with httpx.AsyncClient() as client:
        response = await client.post(
            target_url,
            content=body,
            headers=headers,
            timeout=10.0
        )

    if response.status_code != 200:
        return Response(
            content=response.content,
            status_code=response.status_code,
            headers=dict(response.headers)
        )

    user_info = response.json()
    user_data = user_info.get("user", user_info)
    user_id = user_data.get("id") or user_data.get("_id")
    user_active = user_data.get("is_active")
    role = user_data.get("role", "Usuario")

    if not user_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="La respuesta del Gestor de Usuarios no contiene ID"
        )

    if not user_active:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="El usuario esta inhabilitado"
        )

    token = create_access_token(user_id=str(user_id), role=role)
    refresh_token = create_refresh_token(user_id=str(user_id))

    return {
        "message": "Inicio de sesión exitoso",
        "token": token,
        "refresh":refresh_token,
        "user": user_data
    }


@router.post("/register")
async def register(request: Request):
    """
    Redirige la solicitud de registro al Gestor de Usuarios.
    """
    body = await request.body()
    headers = dict(request.headers)
    headers.pop("host", None)

    target_url = f"{settings.USERS_SERVICE_URL}/users/register"

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

@router.post("/refresh")
async def refresh(request: Request):
    """
    Recibe el refresh_token, verifica el estado del usuario y emite un nuevo access_token si sigue activo.
    """
    body = await request.json()
    refresh_token = body.get("refresh_token")

    if not refresh_token:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Falta el refresh_token en el cuerpo de la petición"
        )

    # 1. Validar el token y extraer el sub (user_id)
    payload = decode_raw_token(refresh_token)
    user_id = payload.get("sub")

    if not user_id:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="El token no contiene un ID de usuario válido"
        )

    target_url = f"{settings.USERS_SERVICE_URL}/users/{user_id}"
    
    async with httpx.AsyncClient() as client:
        user_response = await client.get(target_url, timeout=10.0)

    if user_response.status_code != 200:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Usuario no encontrado"
        )

    user_data = user_response.json()
    
    if not user_data.get("is_active"):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="La cuenta ha sido inhabilitada."
        )

    #Generar el NUEVO access token
    new_token = create_access_token(
        user_id=str(user_id), 
        role=user_data.get("role", "Usuario")
    )

    return {
        "message": "Token renovado con éxito",
        "token": new_token
    }

# =====================================================================
# 2. RUTAS PROTEGIDAS
# =====================================================================

@router.api_route("/{path:path}", methods=["GET", "POST", "PUT", "DELETE", "PATCH"])
async def proxy_users(
    path: str, 
    request: Request, 
    user_payload: dict = Depends(verify_jwt_token)
):
    """Reenvía peticiones protegidas al Gestor de Usuarios inyectando contexto (X-User-Id, X-User-Role)."""
    """Reenvía peticiones protegidas inyectando X-User-Id y X-User-Role."""
    target_url = f"{settings.USERS_SERVICE_URL}/users/{path}"
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