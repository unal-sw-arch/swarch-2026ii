from datetime import datetime, timedelta, timezone
import jwt
from fastapi import HTTPException, Security, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from app.config import settings

security = HTTPBearer()

def create_access_token(user_id: str, role: str) -> str:
    """Genera un token JWT firmado para el usuario con su rol y caducidad de 15 minutos."""
    now = datetime.now(timezone.utc)
    payload = {
        "sub": str(user_id),
        "role": role,
        "exp": now + timedelta(hours=0.25),
        "iat": now
    }
    return jwt.encode(payload, settings.JWT_SECRET_KEY, algorithm=settings.JWT_ALGORITHM)

def create_refresh_token(user_id: str) -> str:
    """Genera un token JWT firmado para el usuario con caducidad de 1 dia."""
    now = datetime.now(timezone.utc)
    payload = {
        "sub": str(user_id),
        "exp": now + timedelta(hours=24),
        "iat": now
    }
    return jwt.encode(payload, settings.JWT_SECRET_KEY, algorithm=settings.JWT_ALGORITHM)

def verify_jwt_token(credentials: HTTPAuthorizationCredentials = Security(security)) -> dict:
    """Valida el token JWT enviado en la cabecera Authorization: Bearer <token>."""
    token = credentials.credentials
    try:
        payload = jwt.decode(
            token, 
            settings.JWT_SECRET_KEY, 
            algorithms=[settings.JWT_ALGORITHM]
        )
        return payload
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, 
            detail="El token de autenticación ha expirado"
        )
    except jwt.InvalidTokenError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, 
            detail="Token de autenticación inválido o alterado"
        )

def decode_raw_token(token: str) -> dict:
    """Decodifica un token directamente desde un string (útil para refresh tokens en el body)."""
    try:
        return jwt.decode(
            token, 
            settings.JWT_SECRET_KEY, 
            algorithms=[settings.JWT_ALGORITHM]
        )
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, 
            detail="El refresh token ha expirado."
        )
    except jwt.InvalidTokenError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, 
            detail="Refresh token inválido o alterado."
        )