"""Consumidor de eventos de dominio de RabbitMQ.

Se ejecuta DENTRO del proceso Celery (bootstep): lee `panaderia.events` y convierte cada evento en una
tarea Celery, que aporta reintentos con backoff. El mensaje de dominio se confirma (ack) solo después de
publicar la tarea; si eso falla, vuelve a la cola (requeue).
"""
import logging
from celery import bootsteps
from kombu import Consumer, Exchange, Queue

import config
from dead_letter import publish_to_dlq
from dispatch import dispatch_domain_message

logger = logging.getLogger("worker.consumer")


def event_queues() -> list[Queue]:
    """Las colas de `infrastructure/rabbitmq/definitions.json` (una por evento, con DLX hacia panaderia.dlq).

    Con DECLARE_TOPOLOGY=false (lo normal) solo se consume de ellas; con true se declaran con los mismos argumentos.
    """
    events = Exchange(config.RABBITMQ_EXCHANGE, type="topic", durable=True)
    return [
        Queue(
            name,
            exchange=events,
            routing_key=config.QUEUE_BINDINGS[name],
            durable=True,
            queue_arguments={
                "x-dead-letter-exchange": config.DLX_EXCHANGE,
                "x-dead-letter-routing-key": config.DLQ_QUEUE,
            },
            no_declare=not config.DECLARE_TOPOLOGY,
        )
        for name in config.EVENTS_QUEUES
    ]


def declare_dead_letter(channel) -> None:
    dlx = Exchange(config.DLX_EXCHANGE, type="topic", durable=True)
    Queue(config.DLQ_QUEUE, exchange=dlx, routing_key="#", durable=True).bind(channel).declare()


class DomainEventsStep(bootsteps.ConsumerStep):
    """Se registra en `celery_app.py`."""

    def __init__(self, parent, **kwargs):
        super().__init__(parent, **kwargs)
        self._app = parent.app

    def get_consumers(self, channel):
        if config.DECLARE_TOPOLOGY:
            declare_dead_letter(channel)
        return [
            Consumer(
                channel,
                queues=event_queues(),
                callbacks=[self.on_message],
                on_decode_error=self.on_decode_error,
                accept=["json"],
            )
        ]

    def on_message(self, body, message) -> None:
        event_name = (message.delivery_info or {}).get("routing_key", "")
        try:
            dispatch_domain_message(
                event_name,
                body,
                send_task=lambda name, args: self._app.send_task(name, args=args, queue=config.TASK_QUEUE),
                dead_letter=publish_to_dlq,
            )
        except Exception:  # noqa: BLE001 - no se pudo publicar la tarea ni la DLQ: reintentar el mensaje
            logger.exception("No se pudo despachar %s; el mensaje vuelve a la cola", event_name)
            message.requeue()
            return
        message.ack()

    def on_decode_error(self, message, exc) -> None:
        logger.error("Mensaje ilegible, se rechaza hacia la DLQ: %s", exc)
        message.reject()  # sin requeue → DLX → panaderia.dlq
