"""Lógica de Events que se dispara por el paso del tiempo (sin petición HTTP)."""

import logging
from functools import partial

from django.db import transaction
from django.utils import timezone

from . import notifications
from .models import Event

logger = logging.getLogger(__name__)


def _finish_is_due(event: Event, now) -> bool:
    return (
        event.published
        and event.status in (Event.Status.PRO, Event.Status.ECU)
        and event.datetime_end <= now
    )


def _start_is_due(event: Event, now) -> bool:
    return (
        event.published
        and event.status == Event.Status.PRO
        and event.datetime_start <= now < event.datetime_end
    )


def _advance(event_id: int, new_status: str, is_due, now) -> bool:
    """Mueve un evento a `new_status` si sigue correspondiendo. Devuelve si lo cambió."""
    with transaction.atomic():
        # Se bloquea la fila y se vuelve a validar: entre la consulta de candidatos
        # y este punto el organizador pudo cancelar el evento o mover sus fechas
        try:
            event = Event.objects.select_for_update().get(pk=event_id)
        except Event.DoesNotExist:
            return False

        if not is_due(event, now):
            return False

        # La foto previa permite a notify_event_update decidir qué tipo de aviso enviar
        previous = notifications.snapshot_event(event)
        event.status = new_status
        # update_fields: solo se toca el estado, nunca el resto de la fila
        event.save(update_fields=["status"])

        # El aviso sale solo si el commit tuvo éxito. Se usa partial y no un lambda
        # para fijar el evento actual y no el último del bucle.
        transaction.on_commit(
            partial(notifications.notify_event_update, previous, event)
        )
        return True


def advance_event_states() -> dict[str, int]:
    """Avanza los eventos publicados según la hora: Programado -> En Curso -> Finalizado.

    Es idempotente: si dos pasadas coinciden, la segunda encuentra el evento ya
    cambiado y no hace nada, por lo que nunca se notifica dos veces.
    """
    now = timezone.now()

    # Primero se finaliza y después se inicia. Si el servicio estuvo caído, un evento
    # puede haber terminado sin pasar por "En Curso"; al finalizar primero pasa
    # directo a "Finalizado" y no se avisa que "empezó" algo que ya terminó.
    finish_ids = list(
        Event.objects.filter(
            published=True,
            status__in=[Event.Status.PRO, Event.Status.ECU],
            datetime_end__lte=now,
        ).values_list("pk", flat=True)
    )
    finished = sum(
        _advance(pk, Event.Status.FIN, _finish_is_due, now) for pk in finish_ids
    )

    # Esta consulta se evalúa después de finalizar, así que ya no incluye esos eventos
    start_ids = list(
        Event.objects.filter(
            published=True,
            status=Event.Status.PRO,
            datetime_start__lte=now,
            datetime_end__gt=now,
        ).values_list("pk", flat=True)
    )
    started = sum(
        _advance(pk, Event.Status.ECU, _start_is_due, now) for pk in start_ids
    )

    return {"started": started, "finished": finished}
