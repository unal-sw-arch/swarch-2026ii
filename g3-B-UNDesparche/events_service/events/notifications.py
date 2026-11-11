"""Construye y encola las notificaciones de Events. Pensado para llamarse desde views.py."""
import os

from core.notifications_publisher import publish_notification

from .models import Subscription
from .tokens import make_unsubscribe_token

FRONTEND_URL = os.getenv("FRONTEND_URL", "http://localhost:5172").rstrip("/")
UNSUBSCRIBE_PATH = os.getenv("UNSUBSCRIBE_PATH", "/unsubscribe")  # ruta del frontend que lee ?token=

_STATUS_TYPES = {"CAN": "event.cancelled", "ECU": "event.started", "FIN": "event.finished"}


def _recipient(subscription: Subscription) -> dict:
    token = make_unsubscribe_token(subscription)
    return {
        "email": subscription.notification_email,
        "unsubscribe_url": f"{FRONTEND_URL}{UNSUBSCRIBE_PATH}?token={token}",
    }


def _event_data(event) -> dict:
    return {
        "event_id": event.id,
        "event_name": event.name,
        "place": event.place,
        "datetime_start": event.datetime_start.isoformat(),
        "datetime_end": event.datetime_end.isoformat(),
    }


def snapshot_event(event) -> dict:
    """Foto del evento ANTES de guardar, para detectar qué cambió."""
    return {
        "status": event.status,
        "place": event.place,
        "datetime_start": event.datetime_start,
        "datetime_end": event.datetime_end,
    }


def notify_subscribed(subscription: Subscription) -> None:
    if not subscription.notification_email:
        return
    publish_notification(
        "event.subscribed", [_recipient(subscription)], _event_data(subscription.event)
    )


def notify_event_update(previous: dict, event) -> None:
    """Cancelación / inicio / fin tienen prioridad; si no, cambio de fecha o lugar."""
    if not event.published:
        return

    type_ = None
    data = _event_data(event)
    if event.status != previous["status"] and event.status in _STATUS_TYPES:
        type_ = _STATUS_TYPES[event.status]
    elif (
        event.datetime_start != previous["datetime_start"]
        or event.datetime_end != previous["datetime_end"]
        or event.place != previous["place"]
    ):
        type_ = "event.rescheduled"
        data["previous_datetime_start"] = previous["datetime_start"].isoformat()
        data["previous_place"] = previous["place"]
    if type_ is None:
        return

    subscriptions = Subscription.objects.filter(event=event).exclude(notification_email="")
    recipients = [_recipient(s) for s in subscriptions]
    publish_notification(type_, recipients, data)
