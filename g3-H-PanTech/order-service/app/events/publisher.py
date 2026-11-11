"""Publicación de eventos de dominio en RabbitMQ (exchange `panaderia.events`, tipo topic).

La routing key es el nombre del evento: `order.paid`, `reservation.created`, ...
"""
import json
from typing import Protocol

import pika

from app.config import settings


class EventPublisher(Protocol):
    def publish(self, routing_key: str, payload: dict, *, message_id: str) -> None: ...


class RabbitPublisher:
    """Abre una conexión corta por lote; con confirmaciones del broker y `mandatory`.

    Si no hay ninguna cola enlazada a la routing key, `basic_publish` falla y el evento
    se queda en el outbox (se reintenta) en vez de perderse en silencio.
    """

    def __init__(self, url: str | None = None, exchange: str | None = None):
        self._url = url or settings.rabbitmq_url
        self._exchange = exchange or settings.rabbitmq_exchange

    def publish(self, routing_key: str, payload: dict, *, message_id: str) -> None:
        params = pika.URLParameters(self._url)
        params.socket_timeout = 5
        params.blocked_connection_timeout = 5
        connection = pika.BlockingConnection(params)
        try:
            channel = connection.channel()
            channel.confirm_delivery()
            channel.exchange_declare(exchange=self._exchange, exchange_type="topic", durable=True)
            channel.basic_publish(
                exchange=self._exchange,
                routing_key=routing_key,
                body=json.dumps(payload, default=str).encode("utf-8"),
                properties=pika.BasicProperties(
                    content_type="application/json",
                    delivery_mode=2,  # persistente
                    message_id=message_id,
                    type=routing_key,
                ),
                mandatory=True,
            )
        finally:
            if connection.is_open:
                connection.close()
