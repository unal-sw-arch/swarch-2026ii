from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    PORT: int = 8000
    JWT_SECRET_KEY: str = "clave_secreta_super_segura_buffit"
    JWT_ALGORITHM: str = "HS256"

    # Microservicios conectados al Gateway según diagrama C&C
    USERS_SERVICE_URL: str = "http://localhost:8001"      # Gestor de Usuarios
    ORDERS_SERVICE_URL: str = "http://localhost:8002"     # Gestor de Pedidos
    INVENTORY_SERVICE_URL: str = "http://localhost:8003"  # Gestor de Inventario

    class Config:
        env_file = ".env"

settings = Settings()