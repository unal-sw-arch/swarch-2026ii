"""Lógica de Inventory que se dispara por el paso del tiempo (sin petición HTTP)."""

import logging
from functools import partial

from django.db import transaction
from django.utils import timezone

from . import notifications
from .models import Implement, Reserve

logger = logging.getLogger(__name__)


def release_expired_reserves() -> int:
    """Libera las reservas cuyo plazo de retiro (RN-02) ya venció.

    Por cada reserva vencida: la desactiva, devuelve el implemento a
    "Disponible" y encola la notificación al usuario. Devuelve cuántas liberó.

    Es idempotente: si dos pasadas coinciden, la segunda encuentra la reserva
    ya inactiva y no hace nada, por lo que nunca se notifica dos veces.
    """
    expired_ids = list(
        Reserve.objects.filter(active=True, datetime_expiration__lt=timezone.now())
        .values_list("pk", flat=True)
    )

    released = 0
    for reserve_id in expired_ids:
        with transaction.atomic():
            # Se bloquea la fila y se vuelve a validar: entre la consulta de arriba
            # y este punto un administrador pudo confirmar o cancelar la reserva
            try:
                reserve = (
                    Reserve.objects.select_for_update()
                    .select_related("implement")
                    .get(pk=reserve_id)
                )
            except Reserve.DoesNotExist:
                continue

            if (
                not reserve.active
                or reserve.datetime_expiration is None
                or reserve.datetime_expiration >= timezone.now()
            ):
                continue

            reserve.active = False
            reserve.save(update_fields=["active"])

            implement = reserve.implement
            # Solo se libera si sigue "Reservado": otro flujo pudo haberlo cambiado
            if implement.state == Implement.State.RESERVED:
                implement.state = Implement.State.AVAILABLE
                implement.save(update_fields=["state"])

            # La notificación sale solo si el commit tuvo éxito. Se usa partial y no un
            # lambda para fijar la reserva actual y no la última del bucle.
            transaction.on_commit(
                partial(notifications.notify_reserve_expired, reserve)
            )
            released += 1

    return released
