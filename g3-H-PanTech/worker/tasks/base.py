"""Base común de las tareas: reintentos con backoff y dead-letter al agotarlos."""
import logging

from celery import Task

import config
from dead_letter import publish_to_dlq
from errors import InvalidEvent

logger = logging.getLogger("worker.tasks")

# Opciones de reintento compartidas: todo error se reintenta salvo un evento inválido (va directo a la DLQ).
RETRY_OPTIONS = dict(
    autoretry_for=(Exception,),
    dont_autoretry_for=(InvalidEvent,),
    retry_backoff=2,  # 2, 4, 8, 16, 32 s...
    retry_backoff_max=60,
    retry_jitter=True,
    max_retries=config.MAX_RETRIES,
)


class EventTask(Task):
    event_name = "unknown"

    def on_failure(self, exc, task_id, args, kwargs, einfo):
        """Se llama al agotar los reintentos (o ante un InvalidEvent): el evento va a `panaderia.dlq`."""
        event = args[0] if args else {}
        logger.error("Tarea %s falló definitivamente: %s", self.name, exc)
        try:
            publish_to_dlq(self.event_name, event, f"{type(exc).__name__}: {exc}")
        except Exception:  # noqa: BLE001
            logger.exception("No se pudo publicar en la DLQ el evento %s", self.event_name)
