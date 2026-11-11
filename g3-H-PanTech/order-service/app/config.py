"""Configuración del Order Service (variables de entorno)."""
from pydantic import AliasChoices, Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str = "postgresql+psycopg://panaderia:panaderia@postgres:5432/panaderia"
    # La base se toma de la ruta de la URL (mongodb://usuario:clave@host:27017/panaderia?authSource=admin).
    mongodb_url: str = Field(default="mongodb://mongodb:27017/panaderia", validation_alias=AliasChoices("MONGODB_URL", "MONGO_URL"))
    redis_url: str = "redis://redis:6379/0"
    rabbitmq_url: str = "amqp://guest:guest@rabbitmq:5672/"
    rabbitmq_exchange: str = "panaderia.events"

    # JWT (RS256). Si `jwt_public_key` (PEM) está definida se usa en lugar de JWKS.
    jwks_url: str = "http://identity-service:8002/.well-known/jwks.json"
    jwt_public_key: str | None = None
    jwt_public_key_file: str | None = None  # ruta a un PEM; alternativa a jwt_public_key
    jwt_issuer: str | None = "identity-service"
    jwt_audience: str | None = "panaderia-express"  # vacío = no validar `aud`
    jwt_algorithm: str = "RS256"

    # Sin valor por defecto a propósito: sin secreto no se arranca.
    service_token: str

    business_tz: str = "America/Bogota"
    max_reservation_people: int = 20
    idempotency_ttl_seconds: int = 86400

    # `reservation.confirmed` NO está en rabbitmq/definitions.json: publicarlo sin cola enlazada bloquearía el outbox.
    # Actívalo solo cuando Jaime agregue la cola worker.reservation_confirmed.
    publish_reservation_confirmed: bool = False

    seed_menu: bool = True
    outbox_relay_enabled: bool = True
    outbox_poll_seconds: float = 2.0
    outbox_batch_size: int = 50

    @field_validator("database_url")
    @classmethod
    def _use_psycopg3(cls, value: str) -> str:
        """El compose entrega `postgresql+psycopg2://`; este servicio usa el driver psycopg 3."""
        for prefix in ("postgresql+psycopg2://", "postgresql://", "postgres://"):
            if value.startswith(prefix):
                return "postgresql+psycopg://" + value[len(prefix):]
        return value


settings = Settings()
