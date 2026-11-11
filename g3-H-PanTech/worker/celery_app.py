"""Aplicación Celery del Worker: broker RabbitMQ, reintentos y consumidor de eventos de dominio."""
from celery import Celery
from kombu import Queue

import config
from consumer import DomainEventsStep

app = Celery("worker", broker=config.BROKER_URL, include=[
    "tasks.order_paid",
    "tasks.order_ready",
    "tasks.reservation_events",
    "tasks.receipts",
])

app.conf.update(
    task_default_queue=config.TASK_QUEUE,
    task_queues=[Queue(config.TASK_QUEUE, durable=True)],
    task_serializer="json",
    accept_content=["json"],
    task_ignore_result=True,
    task_acks_late=True,  # se confirma al terminar: si el worker muere, el mensaje se reentrega
    task_reject_on_worker_lost=True,
    worker_prefetch_multiplier=1,
    broker_connection_retry_on_startup=True,
    timezone="UTC",
)

# Lee los eventos de panaderia.events y los convierte en tareas (ver consumer.py).
app.steps["consumer"].add(DomainEventsStep)
