from dataclasses import dataclass


class InvalidMessage(Exception):
    """Mensaje que nunca podrá procesarse (tipo desconocido, datos faltantes): va a la DLQ."""


@dataclass(frozen=True)
class TemplateSpec:
    template: str
    subject: str  # plantilla Jinja
    required: tuple[str, ...]
    source: str


CATALOG: dict[str, TemplateSpec] = {
    # ---- Events ----
    "event.subscribed": TemplateSpec(
        "event_subscribed.html", "Te suscribiste a {{ event_name }}",
        ("event_name", "place", "datetime_start", "datetime_end"), "events"),
    "event.cancelled": TemplateSpec(
        "event_cancelled.html", "Evento cancelado: {{ event_name }}",
        ("event_name", "place", "datetime_start"), "events"),
    "event.rescheduled": TemplateSpec(
        "event_rescheduled.html", "Cambios en {{ event_name }}",
        ("event_name", "place", "datetime_start", "datetime_end"), "events"),
    "event.started": TemplateSpec(
        "event_started.html", "{{ event_name }} acaba de comenzar",
        ("event_name", "place", "datetime_end"), "events"),
    "event.finished": TemplateSpec(
        "event_finished.html", "{{ event_name }} ha finalizado",
        ("event_name",), "events"),
    # ---- Inventory ----
    "implement.reserved": TemplateSpec(
        "implement_reserved.html", "Reservaste {{ implement_name }}",
        ("implement_name", "expires_at"), "inventory"),
    "implement.reserve_expired": TemplateSpec(
        "implement_reserve_expired.html", "Tu reserva de {{ implement_name }} venció",
        ("implement_name",), "inventory"),
    "implement.borrowed": TemplateSpec(
        "implement_borrowed.html", "Préstamo registrado: {{ implement_name }}",
        ("implement_name", "borrowed_at"), "inventory"),
    "implement.returned": TemplateSpec(
        "implement_returned.html", "Devolución registrada: {{ implement_name }}",
        ("implement_name", "returned_at"), "inventory"),
}


def get_spec(type_: str) -> TemplateSpec:
    try:
        return CATALOG[type_]
    except KeyError:
        raise InvalidMessage(f"Tipo de notificación desconocido: {type_!r}") from None


def validate_data(spec: TemplateSpec, data: dict) -> None:
    missing = [k for k in spec.required if data.get(k) in (None, "")]
    if missing:
        raise InvalidMessage(f"Faltan campos en data: {', '.join(missing)}")
