"""Patrón outbox: el evento se guarda en la MISMA transacción que el cambio de negocio y un
relay lo publica después. Si RabbitMQ falla, el evento queda pendiente y se reintenta."""
import logging
import threading
import uuid
from datetime import datetime, timedelta, timezone
from typing import Callable

from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from app.events.publisher import EventPublisher
from app.models import Outbox, utcnow

logger = logging.getLogger("order-service.outbox")


def enqueue(db: Session, event_type: str, payload: dict) -> Outbox:
    """Agrega el evento a la sesión (sin commit). Inyecta `event_id` y `occurred_at`."""
    row = Outbox(event_type=event_type, payload={})
    row.id = uuid.uuid4()
    row.payload = {
        "event_id": str(row.id),
        "occurred_at": datetime.now(timezone.utc).isoformat(),
        **payload,
    }
    db.add(row)
    return row


def publish_pending(
    session_factory: Callable[[], Session], publisher: EventPublisher, *, batch_size: int = 50
) -> tuple[int, int]:
    """Publica los eventos pendientes en orden. Devuelve (publicados, fallidos).

    Ante el primer fallo se detiene el lote (conserva el orden y no martilla al broker) y
    programa el siguiente intento con backoff exponencial (máx. 60 s).
    """
    sent = failed = 0
    with session_factory() as db:
        now = utcnow()
        stmt = (
            select(Outbox)
            .where(
                Outbox.published_at.is_(None),
                or_(Outbox.next_attempt_at.is_(None), Outbox.next_attempt_at <= now),
            )
            .order_by(Outbox.created_at, Outbox.id)
            .limit(batch_size)
            .with_for_update(skip_locked=True)
        )
        for row in db.scalars(stmt).all():
            try:
                publisher.publish(row.event_type, row.payload, message_id=str(row.id))
            except Exception as exc:  # noqa: BLE001 - cualquier fallo del broker se reintenta
                row.attempts += 1
                row.last_error = str(exc)[:500]
                row.next_attempt_at = now + timedelta(seconds=min(2**row.attempts, 60))
                failed += 1
                logger.warning(
                    "No se pudo publicar %s (%s), intento %s: %s",
                    row.event_type, row.id, row.attempts, exc,
                )
                break
            row.published_at = now
            sent += 1
        db.commit()
    return sent, failed


class OutboxRelay:
    """Hilo que publica el outbox periódicamente; `wake()` lo despierta tras un commit."""

    def __init__(
        self,
        session_factory: Callable[[], Session],
        publisher: EventPublisher,
        *,
        interval: float = 2.0,
        batch_size: int = 50,
    ):
        self._session_factory = session_factory
        self._publisher = publisher
        self._interval = interval
        self._batch_size = batch_size
        self._wake = threading.Event()
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._run, name="outbox-relay", daemon=True)

    def start(self) -> None:
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        self._wake.set()
        self._thread.join(timeout=5)

    def wake(self) -> None:
        self._wake.set()

    def _run(self) -> None:
        while not self._stop.is_set():
            self._wake.wait(self._interval)
            self._wake.clear()
            if self._stop.is_set():
                break
            try:
                publish_pending(self._session_factory, self._publisher, batch_size=self._batch_size)
            except Exception:  # noqa: BLE001 - el hilo nunca debe morir
                logger.exception("Fallo inesperado en el relay del outbox")


def wake(app) -> None:
    """Despierta el relay (si está activo) tras confirmar una transacción con eventos nuevos."""
    relay = getattr(app.state, "outbox_relay", None)
    if relay is not None:
        relay.wake()
