"""Enrutamiento puro de eventos de dominio a tareas (sin dependencias de Celery ni de RabbitMQ)."""
from typing import Callable

import config


def dispatch_domain_message(
    event_name: str,
    body: object,
    *,
    send_task: Callable[..., object],
    dead_letter: Callable[[str, object, object], None],
) -> None:
    """Enruta un evento a su tarea. Los eventos desconocidos o mal formados van a la DLQ."""
    task_name = config.EVENT_TASKS.get(event_name)
    if task_name is None:
        dead_letter(event_name, body, f"Evento sin consumidor: {event_name}")
        return
    if not isinstance(body, dict) or not body.get("event_id"):
        dead_letter(event_name, body, "El evento no es un objeto con event_id")
        return
    send_task(task_name, args=[body])
