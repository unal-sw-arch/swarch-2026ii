import jwt
from datetime import datetime, timedelta, timezone  # <-- Importar timezone
from app.config import Config

def create_access_token(user_id: str, role: str) -> str:
    """Genera un token JWT firmado para el usuario con su rol y caducidad."""
    now = datetime.now(timezone.utc)
    payload = {
        "sub": user_id,
        "role": role,
        "exp": now + timedelta(hours=8),
        "iat": now
    }
    return jwt.encode(payload, Config.JWT_SECRET_KEY, algorithm=Config.JWT_ALGORITHM)