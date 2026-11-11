import os
from dotenv import load_dotenv

# Carga las variables de entorno del archivo .env
load_dotenv()

# URLs de los microservicios existentes (para el proxy)
USERS_SERVICE_URL = os.getenv("USERS_SERVICE_URL", "http://localhost:8001")
EVENTS_SERVICE_URL = os.getenv("EVENTS_SERVICE_URL", "http://localhost:8002")
INVENTORY_SERVICE_URL = os.getenv("INVENTORY_SERVICE_URL", "http://localhost:8003")

# Archivo de credenciales de Firebase (ya lo tienes en la raíz del gateway)
FIREBASE_CREDENTIALS_PATH = os.getenv("FIREBASE_CREDENTIALS_PATH", "firebase_credentials.json")

# Tiempo de vida del caché de usuarios (segundos).
USER_CACHE_TTL_SECONDS = int(os.getenv("USER_CACHE_TTL_SECONDS", "60"))

# Tiempo máximo de espera (segundos) por la respuesta de un microservicio
HTTP_TIMEOUT_SECONDS = float(os.getenv("HTTP_TIMEOUT_SECONDS", "30"))

# Orígenes permitidos para CORS (separados por coma en el .env)
cors_raw = os.getenv(
    "CORS_ORIGINS",
    "http://localhost:5172,http://localhost:3000,http://127.0.0.1:5172,http://127.0.0.1:3000",
)
CORS_ORIGINS = [o.strip() for o in cors_raw.split(",") if o.strip()]


def validate_config():
    """Lanza un error claro si faltan configuraciones críticas al iniciar."""
    if not FIREBASE_CREDENTIALS_PATH:
        raise ValueError("FIREBASE_CREDENTIALS_PATH no está definido en el .env")
    if not os.path.exists(FIREBASE_CREDENTIALS_PATH):
        raise FileNotFoundError(
            f"No se encontró el archivo de credenciales de Firebase: {FIREBASE_CREDENTIALS_PATH}"
        )
