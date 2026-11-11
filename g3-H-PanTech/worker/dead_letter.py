"""Dead-letter: mensajes que no se pudieron procesar terminan en la cola `panaderia.dlq`."""
import json
import logging

import pika

import config

logger = logging.getLogger("worker.dlq")


def publish_to_dlq(event_name: str, body, reason: object) -> None:
    """Publica en la DLX `panaderia.dlx` con routing key `panaderia.dlq` (la cola está enlazada con `#`)."""
    payload = body if isinstance(body, (bytes, bytearray)) else json.dumps(body, default=str).encode()
    params = pika.URLParameters(config.RABBITMQ_URL)
    params.socket_timeout = 5
    connection = pika.BlockingConnection(params)
    try:
        channel = connection.channel()
        channel.confirm_delivery()
        channel.basic_publish(
            exchange=config.DLX_EXCHANGE,
            routing_key=config.DLQ_QUEUE,
            body=payload,
            properties=pika.BasicProperties(
                delivery_mode=2,
                content_type="application/json",
                headers={"x-original-routing-key": event_name, "x-error": str(reason)[:500]},
            ),
            mandatory=True,
        )
    finally:
        if connection.is_open:
            connection.close()
    logger.error("Evento %s enviado a %s: %s", event_name, config.DLQ_QUEUE, reason)
