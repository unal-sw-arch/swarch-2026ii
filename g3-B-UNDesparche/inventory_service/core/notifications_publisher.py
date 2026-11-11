"""Publicador mínimo hacia la cola de notificaciones (RabbitMQ).

Garantías:
  * Nunca lanza excepciones ni bloquea la petición HTTP: publica en un hilo aparte.
  * Si RabbitMQ está caído o la cola no existe, se registra el error y la API sigue respondiendo.
  * Usa confirmaciones del broker + mandatory para detectar mensajes no enrutables.

Variables de entorno (opcionales):
  RABBITMQ_URL            default amqp://undesparche:undesparche@rabbitmq:5672/%2F   (pika usa %2F para el vhost "/")
  NOTIFICATIONS_ENABLED   default true
"""
import json
import logging
import os
import threading
import uuid
from datetime import datetime, timezone

import pika

logger = logging.getLogger(__name__)

EXCHANGE = "notifications"
ROUTING_KEY = "email.send"
SOURCE = "inventory"


def _enabled() -> bool:
    return os.getenv("NOTIFICATIONS_ENABLED", "true").lower() in {"1", "true", "yes"}


def _publish_sync(body: bytes, message_id: str) -> None:
    params = pika.URLParameters(
        os.getenv("RABBITMQ_URL", "amqp://undesparche:undesparche@rabbitmq:5672/%2F")
    )
    params.socket_timeout = 3
    params.blocked_connection_timeout = 3
    params.connection_attempts = 2
    params.retry_delay = 1
    connection = pika.BlockingConnection(params)
    try:
        channel = connection.channel()
        channel.confirm_delivery()
        channel.exchange_declare(exchange=EXCHANGE, exchange_type="direct", durable=True)
        channel.basic_publish(
            exchange=EXCHANGE,
            routing_key=ROUTING_KEY,
            body=body,
            properties=pika.BasicProperties(
                content_type="application/json", delivery_mode=2, message_id=message_id
            ),
            mandatory=True,  # UnroutableError si la cola aún no existe (Notifications no ha arrancado)
        )
    finally:
        connection.close()


def publish_notification(type: str, recipients: list[dict], data: dict) -> None:
    """Encola una notificación.

    recipients: [{"email": "...", "name": "..." (opcional), "unsubscribe_url": "..." (opcional)}]
    data:       campos del tipo (ver GET /types en notifications_service).
    """
    if not _enabled() or not recipients:
        return
    message_id = str(uuid.uuid4())
    body = json.dumps(
        {
            "message_id": message_id,
            "schema_version": 1,
            "type": type,
            "source": SOURCE,
            "occurred_at": datetime.now(timezone.utc).isoformat(),
            "recipients": recipients,
            "data": data,
        },
        default=str,
    ).encode()

    def _run() -> None:
        try:
            _publish_sync(body, message_id)
            logger.info("Notificación encolada type=%s id=%s destinatarios=%d", type, message_id, len(recipients))
        except Exception:
            logger.exception("No se pudo encolar la notificación type=%s id=%s", type, message_id)

    threading.Thread(target=_run, name="notifications-publisher", daemon=True).start()
