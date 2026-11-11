from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, EmailStr, Field


class Recipient(BaseModel):
    email: EmailStr
    name: str | None = None
    # Enlace de baja personalizado (lo genera el productor, p. ej. Events con su token firmado)
    unsubscribe_url: str | None = None


class NotificationMessage(BaseModel):
    """Contrato del mensaje AMQP publicado por Events e Inventory."""

    message_id: str = Field(min_length=8, description="UUID único por mensaje (trazabilidad y reintentos)")
    schema_version: int = 1
    type: str = Field(description="Ver GET /types")
    source: Literal["events", "inventory"]
    occurred_at: datetime
    recipients: list[Recipient] = Field(min_length=1)
    data: dict[str, Any] = Field(default_factory=dict)
