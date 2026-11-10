"""Publicación de eventos de pedidos hacia el broker (RabbitMQ).

Los eventos se publican DESPUÉS de confirmar el cambio en la base de datos.
Si el broker no está disponible el pedido NO falla: se registra el error y se sigue
(la operación principal nunca depende del servicio de estadísticas).
"""
import json
import logging
import uuid
from datetime import datetime, timezone

import pika

from app.config import Config

log = logging.getLogger("pedidos.events")

EVENT_TYPES = {
    "CONFIRMED": "pedido.creado",
    "DELIVERED": "pedido.entregado",
    "CANCELLED": "pedido.cancelado",
}


def build_event(order, event_type: str) -> dict:
    """Evento autocontenido: lleva todo lo necesario para que el consumidor no consulte a Pedidos."""
    return {
        "event_id": str(uuid.uuid4()),
        "type": event_type,
        "occurred_at": datetime.now(timezone.utc).isoformat(),
        "order_id": order.id,
        "user_id": order.user_id,
        "user_type": order.user_type,
        "dining_hall": order.dining_hall,
        "time_slot": order.pickup_time_slot,
        "amount_paid": order.total_price,
        "status": order.status,
        "created_at": order.created_at.isoformat() + "Z" if order.created_at else None,
    }


def _send(routing_key: str, event: dict) -> None:
    params = pika.URLParameters(Config.RABBITMQ_URL)
    params.socket_timeout = 2
    params.blocked_connection_timeout = 2
    params.connection_attempts = 1
    connection = pika.BlockingConnection(params)
    try:
        channel = connection.channel()
        channel.exchange_declare(exchange=Config.EVENTS_EXCHANGE, exchange_type="topic", durable=True)
        # Se declara tambien la cola del consumidor (idempotente y con los MISMOS argumentos que
        # gestor-estadisticas): asi ningun evento se pierde si Pedidos publica antes de que
        # Estadisticas haya arrancado por primera vez.
        channel.queue_declare(queue=Config.STATS_DLQ, durable=True)
        channel.queue_declare(queue=Config.STATS_QUEUE, durable=True, arguments={
            "x-dead-letter-exchange": "", "x-dead-letter-routing-key": Config.STATS_DLQ})
        channel.queue_bind(queue=Config.STATS_QUEUE, exchange=Config.EVENTS_EXCHANGE, routing_key="pedido.*")
        channel.basic_publish(
            exchange=Config.EVENTS_EXCHANGE,
            routing_key=routing_key,
            body=json.dumps(event).encode("utf-8"),
            properties=pika.BasicProperties(
                content_type="application/json",
                delivery_mode=2,  # mensaje persistente
                message_id=event["event_id"],
            ),
        )
    finally:
        connection.close()


def publish_order_event(order, event_type: str) -> bool:
    """Publica el evento; devuelve False (sin lanzar excepción) si no se pudo."""
    if not Config.EVENTS_ENABLED:
        return False
    try:
        _send(event_type, build_event(order, event_type))
        return True
    except Exception as exc:  # broker caído, red, etc.
        log.warning("No se pudo publicar %s del pedido %s: %s", event_type, getattr(order, "id", "?"), exc)
        return False
