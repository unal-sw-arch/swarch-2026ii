import os
from dotenv import load_dotenv

# Carga las variables de entorno del archivo .env
load_dotenv()

# URLs de los microservicios existentes (para el proxy)
USERS_SERVICE_URL = os.getenv("USERS_SERVICE_URL")
EVENTS_SERVICE_URL = os.getenv("EVENTS_SERVICE_URL")
INVENTORY_SERVICE_URL = os.getenv("INVENTORY_SERVICE_URL")

# Archivo de credenciales de Firebase (ya lo tienes en la raíz del gateway)
FIREBASE_CREDENTIALS_PATH = os.getenv("FIREBASE_CREDENTIALS_PATH")

# Tiempo de vida del caché de usuarios (segundos).
USER_CACHE_TTL_SECONDS = int(os.getenv("USER_CACHE_TTL_SECONDS"))

# Tiempo máximo de espera (segundos) por la respuesta de un microservicio
HTTP_TIMEOUT_SECONDS = float(os.getenv("HTTP_TIMEOUT_SECONDS"))

# Orígenes permitidos para CORS (separados por coma en el .env)
CORS_ORIGINS = [o.strip() for o in os.getenv("CORS_ORIGINS").split(",") if o.strip()]


def validate_config():
    """Lanza un error claro si faltan configuraciones críticas al iniciar."""
    if not FIREBASE_CREDENTIALS_PATH:
        raise ValueError("FIREBASE_CREDENTIALS_PATH no está definido en el .env")
    if not os.path.exists(FIREBASE_CREDENTIALS_PATH):
        raise FileNotFoundError(
            f"No se encontró el archivo de credenciales de Firebase: {FIREBASE_CREDENTIALS_PATH}"
        )
