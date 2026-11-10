"""Consumidor RabbitMQ: escucha los eventos de pedidos y los entrega al procesador.

Topologia:
  exchange 'pedidos' (topic, durable) --pedido.*--> cola 'estadisticas.pedidos' (durable)
  Los mensajes invalidos o rechazados van a la cola 'estadisticas.pedidos.dlq'.
"""
import json
import logging
import threading
import time

import pika

from app.config import Config
from app.processor import handle_event, InvalidEvent

log = logging.getLogger("estadisticas.consumer")


def declare_topology(channel):
    channel.exchange_declare(exchange=Config.EVENTS_EXCHANGE, exchange_type="topic", durable=True)
    channel.queue_declare(queue=Config.EVENTS_DLQ, durable=True)
    channel.queue_declare(
        queue=Config.EVENTS_QUEUE,
        durable=True,
        arguments={"x-dead-letter-exchange": "", "x-dead-letter-routing-key": Config.EVENTS_DLQ},
    )
    channel.queue_bind(queue=Config.EVENTS_QUEUE, exchange=Config.EVENTS_EXCHANGE, routing_key="pedido.*")


def process_message(app, body: bytes) -> str:
    """Decide que hacer con un mensaje: 'ack' (listo), 'reject' (a la DLQ) o 'retry' (reintentar)."""
    try:
        event = json.loads(body)
    except (ValueError, UnicodeDecodeError):
        log.error("Mensaje no es JSON valido; va a la DLQ")
        return "reject"
    try:
        with app.app_context():
            result = handle_event(event)
        log.info("Evento %s pedido=%s -> %s", event.get("type"), event.get("order_id"), result)
        return "ack"
    except InvalidEvent as exc:
        log.error("Evento invalido (%s); va a la DLQ", exc)
        return "reject"
    except Exception:
        log.exception("Error procesando el evento; se reintentara")
        return "retry"


def _on_message(app):
    def callback(channel, method, properties, body):
        action = process_message(app, body)
        if action == "ack":
            channel.basic_ack(method.delivery_tag)
        elif action == "reject":
            channel.basic_reject(method.delivery_tag, requeue=False)
        else:
            time.sleep(2)  # evita un bucle apretado si la BD esta caida
            channel.basic_nack(method.delivery_tag, requeue=True)
    return callback


def _run_forever(app):
    while True:
        try:
            params = pika.URLParameters(Config.RABBITMQ_URL)
            params.heartbeat = 60
            connection = pika.BlockingConnection(params)
            channel = connection.channel()
            declare_topology(channel)
            channel.basic_qos(prefetch_count=10)
            channel.basic_consume(queue=Config.EVENTS_QUEUE, on_message_callback=_on_message(app))
            log.info("Conectado a RabbitMQ; esperando eventos en '%s'", Config.EVENTS_QUEUE)
            channel.start_consuming()
        except Exception as exc:
            log.warning("Sin conexion con RabbitMQ (%s); reintentando en 5 s", exc)
            time.sleep(5)


def start_consumer(app) -> threading.Thread:
    thread = threading.Thread(target=_run_forever, args=(app,), name="rabbit-consumer", daemon=True)
    thread.start()
    return thread
